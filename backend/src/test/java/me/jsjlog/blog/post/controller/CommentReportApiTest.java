package me.jsjlog.blog.post.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 댓글 신고.
 *
 * <p>신고는 남을 가리키는 기능이라 막아 둘 것이 많다. 익명으로 열어 두거나 여러 번
 * 받으면 혼자서 숫자를 부풀릴 수 있고, 그러면 신고 수를 믿을 수 없게 된다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentReportRepository commentReportRepository;

    private Comment comment;
    private MemberPrincipal writer;
    private MemberPrincipal reader;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("신고 API", 910L));

        Post post = new Post("신고 API 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        Member writerMember = member("report-writer", "댓글쓴이");
        Member readerMember = member("report-reader", "읽은이");

        writer = MemberPrincipal.ofSocial(writerMember);
        reader = MemberPrincipal.ofSocial(readerMember);

        comment = commentRepository.save(new Comment(post, null, writerMember, "신고 대상 댓글"));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER,
                providerId,
                nickname,
                null,
                null
        ));
    }

    private String reportUrl() {
        return "/api/v1/blog/comments/%d/reports".formatted(comment.getId());
    }

    @Test
    @DisplayName("로그인한 회원은 남의 댓글을 신고할 수 있다")
    void reportsComment() throws Exception {
        mockMvc.perform(post(reportUrl())
                        .with(user(reader))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ABUSE","detail":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reported").value(true));

        assertThat(commentReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인하지 않으면 신고할 수 없다 — 익명이면 한 사람이 몇 번이고 누를 수 있다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(post(reportUrl())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM","detail":null}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(commentReportRepository.count()).isZero();
    }

    @Test
    @DisplayName("내가 쓴 댓글은 신고할 수 없다")
    void rejectsSelfReport() throws Exception {
        mockMvc.perform(post(reportUrl())
                        .with(user(writer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM","detail":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REPORT_SELF"));

        assertThat(commentReportRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 댓글을 두 번 신고할 수 없다 — 혼자서 숫자를 올리지 못하게")
    void rejectsDuplicate() throws Exception {
        String body = """
                {"reason":"SPAM","detail":null}
                """;

        mockMvc.perform(post(reportUrl())
                        .with(user(reader))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post(reportUrl())
                        .with(user(reader))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_REPORT_DUPLICATED"));

        assertThat(commentReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("사유 없이는 신고할 수 없다")
    void requiresReason() throws Exception {
        mockMvc.perform(post(reportUrl())
                        .with(user(reader))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":null,"detail":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REPORT_REASON_REQUIRED"));
    }

    /**
     * 화면은 이 값으로 신고 단추를 감춘다. 서버가 틀리게 주면 자기 댓글에 신고 단추가
     * 뜨고, 눌러 봐야 거절만 되는 일이 생긴다.
     */
    @Test
    @DisplayName("댓글 목록이 내 댓글인지 알려준다")
    void tellsWhichCommentsAreMine() throws Exception {
        String comments = "/api/v1/blog/posts/%d/comments".formatted(comment.getPost().getId());

        // 쓴 사람이 보면 내 것이다
        mockMvc.perform(get(comments).with(user(writer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].mine").value(true));

        // 다른 사람이 보면 내 것이 아니다
        mockMvc.perform(get(comments).with(user(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].mine").value(false));
    }

    @Test
    @DisplayName("로그인하지 않으면 어떤 댓글도 내 것이 아니다")
    void nothingIsMineWhenSignedOut() throws Exception {
        mockMvc.perform(get("/api/v1/blog/posts/%d/comments".formatted(comment.getPost().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].mine").value(false));
    }

    @Test
    @DisplayName("신고해도 댓글이 저절로 숨지 않는다 — 몰려서 신고하면 멀쩡한 글이 사라진다")
    void doesNotHideAutomatically() throws Exception {
        mockMvc.perform(post(reportUrl())
                        .with(user(reader))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ABUSE","detail":null}
                                """))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.isDeleted()).isFalse());
    }
}
