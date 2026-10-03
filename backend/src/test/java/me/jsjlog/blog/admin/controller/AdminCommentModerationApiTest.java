package me.jsjlog.blog.admin.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.domain.CommentReportReason;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고를 보고 판단하는 자리.
 *
 * <p>중요한 것 둘이다. <b>운영자가 가린 것과 글쓴이가 지운 것이 갈려야 하고</b>,
 * 무엇을 했는지가 남아야 한다. 앞의 것이 섞이면 가려진 사람은 자기가 지운 줄 알고,
 * 뒤의 것이 없으면 왜 사라졌냐는 물음에 가리킬 게 없다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCommentModerationApiTest {

    private static final String COMMENTS = "/api/v1/admin/blog/comments";

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

    private Post post;
    private Comment comment;
    private MemberPrincipal admin;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("신고 관리", 930L));

        post = new Post("신고 관리 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        Member writer = member("moderation-writer", "글쓴이");
        Member reporter = member("moderation-reporter", "신고자");
        Member adminMember = memberRepository.save(Member.ofLocalAdmin(
                "moderation-admin", "encoded-password", "운영자"));

        admin = MemberPrincipal.ofLocal(adminMember);

        comment = commentRepository.save(new Comment(post, null, writer, "문제가 된 댓글"));
        commentReportRepository.save(
                new CommentReport(comment, reporter, CommentReportReason.ABUSE, null));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER, providerId, nickname, null, null));
    }

    private String url(String suffix) {
        return COMMENTS + "/" + comment.getId() + suffix;
    }

    @Test
    @DisplayName("신고 내역을 볼 수 있다 — 누가 신고했는지는 빼고")
    void showsReports() throws Exception {
        mockMvc.perform(get(url("/moderation")).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports.length()").value(1))
                .andExpect(jsonPath("$.data.reports[0].reasonLabel").value("욕설·비방"))
                .andExpect(jsonPath("$.data.reports[0].handled").value(false))
                // 신고자를 담을 자리 자체가 없어야 한다
                .andExpect(jsonPath("$.data.reports[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.reports[0].memberId").doesNotExist());
    }

    @Test
    @DisplayName("가리면 조치가 남고 신고도 처리된 것으로 바뀐다")
    void hideRecordsModerationAndHandlesReports() throws Exception {
        mockMvc.perform(put(url("/visibility"))
                        .with(user(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true,"reason":"욕설이 심함"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(url("/moderation")).with(user(admin)))
                .andExpect(jsonPath("$.data.moderations.length()").value(1))
                .andExpect(jsonPath("$.data.moderations[0].actionLabel").value("가림"))
                .andExpect(jsonPath("$.data.moderations[0].reason").value("욕설이 심함"))
                .andExpect(jsonPath("$.data.moderations[0].adminNickname").value("운영자"))
                .andExpect(jsonPath("$.data.reports[0].handled").value(true));
    }

    /**
     * 여기가 이 작업의 핵심이다. 전에는 관리자가 가려도 글쓴이가 지운 것과 같은 칸을 써서,
     * 가려진 사람이 자기가 지운 줄 알았다.
     */
    @Test
    @DisplayName("운영자가 가린 것과 글쓴이가 지운 것이 화면에 다르게 나온다")
    void separatesAdminHideFromMemberDelete() throws Exception {
        mockMvc.perform(put(url("/visibility"))
                        .with(user(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true,"reason":null}
                                """))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.isDeleted()).isTrue();
                    assertThat(found.isHiddenByAdmin()).isTrue();
                });
    }

    @Test
    @DisplayName("문제 없음으로 넘겨도 신고는 처리된다")
    void dismissHandlesReports() throws Exception {
        mockMvc.perform(post(url("/reports/dismiss"))
                        .with(user(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"읽어 보니 문제 없음"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(url("/moderation")).with(user(admin)))
                .andExpect(jsonPath("$.data.moderations[0].actionLabel").value("문제 없음"))
                .andExpect(jsonPath("$.data.reports[0].handled").value(true));

        // 댓글은 그대로다
        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.isDeleted()).isFalse());
    }

    @Test
    @DisplayName("신고 필터는 아직 판단하지 않은 것만 센다")
    void reportedFilterCountsOnlyUnhandled() throws Exception {
        mockMvc.perform(get(COMMENTS).param("status", "REPORTED").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].unhandledReportCount").value(1));

        mockMvc.perform(post(url("/reports/dismiss"))
                        .with(user(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // 처리한 뒤에는 목록에서 내려간다. 전체 신고 수는 남아서 이력을 잃지 않는다
        mockMvc.perform(get(COMMENTS).param("status", "REPORTED").with(user(admin)))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(COMMENTS).param("status", "ALL").with(user(admin)))
                .andExpect(jsonPath("$.data.items[?(@.reportCount == 1)]").exists());
    }

    @Test
    @DisplayName("되돌리면 그것도 이력에 남는다")
    void restoreIsRecorded() throws Exception {
        String body = """
                {"hidden":%s,"reason":null}
                """;

        mockMvc.perform(put(url("/visibility")).with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("true")))
                .andExpect(status().isOk());

        mockMvc.perform(put(url("/visibility")).with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("false")))
                .andExpect(status().isOk());

        mockMvc.perform(get(url("/moderation")).with(user(admin)))
                .andExpect(jsonPath("$.data.moderations.length()").value(2))
                .andExpect(jsonPath("$.data.moderations[0].actionLabel").value("되돌림"));

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.isDeleted()).isFalse();
                    assertThat(found.isHiddenByAdmin()).isFalse();
                });
    }
}
