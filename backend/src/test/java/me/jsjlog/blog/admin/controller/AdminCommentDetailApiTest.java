package me.jsjlog.blog.admin.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 댓글 관리에서 한 줄을 눌렀을 때 보는 것.
 *
 * 신고를 받고 나서 글쓴이가 고치면, 운영자는 고친 뒤 본문만 보게 된다.
 * 신고 시점 본문이 따로 보여야 무엇이 신고됐는지 판단할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCommentDetailApiTest {

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

    @Autowired
    private CommentService commentService;

    private Post post;
    private Member writer;
    private Member reporter;
    private MemberPrincipal admin;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("댓글 상세", 931L));

        post = new Post("댓글 상세 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        writer = member("detail-writer", "글쓴이");
        reporter = member("detail-reporter", "신고자");
        admin = MemberPrincipal.ofLocal(memberRepository.save(
                Member.ofLocalAdmin("detail-admin", "encoded-password", "운영자")));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER, providerId, nickname, null, null));
    }

    private Long write(Member member, String content) {
        return commentService.createComment(
                post.getId(), new CommentCreateRequest(content, null), member.getId()).id();
    }

    private void report(Long commentId) {
        commentReportRepository.save(new CommentReport(
                commentRepository.findById(commentId).orElseThrow(),
                reporter,
                CommentReportReason.ABUSE,
                null));
    }

    @Test
    @DisplayName("신고 뒤에 고쳐도 신고 시점 본문이 보인다")
    void showsContentAtReportTime() throws Exception {
        Long commentId = write(writer, "신고당할 말");
        report(commentId);
        commentService.updateComment(commentId, "고친 말", writer.getId());

        mockMvc.perform(get(COMMENTS + "/" + commentId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("고친 말"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("신고당할 말"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(true));
    }

    @Test
    @DisplayName("신고 뒤에 고치지 않았으면 지금 본문이 곧 신고 시점 본문이다")
    void reportedContentIsCurrentWhenUntouched() throws Exception {
        Long commentId = write(writer, "그대로인 말");
        report(commentId);

        mockMvc.perform(get(COMMENTS + "/" + commentId).with(user(admin)))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("그대로인 말"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(false));
    }

    @Test
    @DisplayName("신고 전에 고친 것은 신고 시점 본문에 들어가 있다")
    void editBeforeReportIsPartOfReportedContent() throws Exception {
        Long commentId = write(writer, "처음 말");
        commentService.updateComment(commentId, "신고 전에 고친 말", writer.getId());
        report(commentId);

        mockMvc.perform(get(COMMENTS + "/" + commentId).with(user(admin)))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("신고 전에 고친 말"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(false));
    }

    @Test
    @DisplayName("작성·수정·삭제가 최신순으로 남고, 수정은 전후 본문이 함께 보인다")
    void showsHistoryNewestFirst() throws Exception {
        Long commentId = write(writer, "처음 말");
        commentService.updateComment(commentId, "고친 말", writer.getId());
        commentService.deleteComment(commentId, writer.getId());

        mockMvc.perform(get(COMMENTS + "/" + commentId).with(user(admin)))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.edited").value(true))
                .andExpect(jsonPath("$.data.histories.length()").value(3))
                .andExpect(jsonPath("$.data.histories[0].actionLabel").value("삭제"))
                .andExpect(jsonPath("$.data.histories[1].actionLabel").value("수정"))
                .andExpect(jsonPath("$.data.histories[1].beforeContent").value("처음 말"))
                .andExpect(jsonPath("$.data.histories[1].afterContent").value("고친 말"))
                .andExpect(jsonPath("$.data.histories[2].actionLabel").value("작성"))
                .andExpect(jsonPath("$.data.histories[2].afterContent").value("처음 말"));
    }

    @Test
    @DisplayName("신고 안 된 댓글도 상세와 기록을 볼 수 있다")
    void unreportedCommentHasDetail() throws Exception {
        Long commentId = write(writer, "평범한 말");

        mockMvc.perform(get(COMMENTS + "/" + commentId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("글쓴이"))
                .andExpect(jsonPath("$.data.postTitle").value("댓글 상세 글"))
                .andExpect(jsonPath("$.data.reports.length()").value(0))
                .andExpect(jsonPath("$.data.histories[0].actionLabel").value("작성"));
    }

    @Test
    @DisplayName("회원으로 거르면 그 회원 댓글만 나온다")
    void filtersByMember() throws Exception {
        write(writer, "글쓴이 댓글");
        write(reporter, "다른 회원 댓글");

        mockMvc.perform(get(COMMENTS)
                        .param("memberId", String.valueOf(writer.getId()))
                        .param("keyword", "댓글")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("글쓴이"))
                .andExpect(jsonPath("$.data.statusCounts.all").value(1));
    }
}
