package me.jsjlog.blog.admin.controller;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.CommentRetention;
import me.jsjlog.blog.post.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고 당시 본문을 확인할 수 있을 때와 없을 때를 가른다.
 *
 * 전에는 수정 기록이 파기되면 고친 뒤 본문이 신고 당시 본문으로 나왔다. 기록이 없다고
 * "고치지 않았다" 고 단정하지 않는지 본다.
 *
 * "그때 이미 기록이 쌓이고 있었는가" 를 DB 전체로 따지므로, 다른 테스트 기록이 섞이지 않게
 * 따로 만든 DB 를 쓴다. 6개월이 지난 상황은 기록 시각을 7개월 앞으로 당겨 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCommentReportedContentTest {

    private static final String LIST = "/api/v1/admin/blog/comments";
    private static final String COMMENTS = LIST + "/";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:reported_content;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private CommentService commentService;
    @Autowired private CommentRetention retention;
    @Autowired private CommentRepository comments;
    @Autowired private CommentReportRepository reports;
    @Autowired private ContentHistoryRepository histories;
    @Autowired private CategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private MemberRepository members;

    private Post post;
    private Member writer;
    private Member reporter;
    private MemberPrincipal admin;

    @BeforeEach
    void setUp() {
        Category category = categories.save(new Category("신고 당시", 980L));
        post = new Post("신고 당시 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusYears(1));
        post = posts.save(post);
        writer = members.save(Member.ofSocial(AuthProvider.NAVER, "rc-writer", "글쓴이", null, null));
        reporter = members.save(Member.ofSocial(AuthProvider.NAVER, "rc-reporter", "신고자", null, null));
        admin = MemberPrincipal.ofLocal(members.save(Member.ofLocalAdmin("rc-admin", "encoded-password", "운영자")));
    }

    private Long write(String content) {
        return commentService.createComment(post.getId(), new CommentCreateRequest(content, null), writer.getId()).id();
    }

    private void edit(Long commentId, String content) {
        commentService.updateComment(commentId, content, writer.getId());
    }

    private Long report(Long commentId) {
        Long id = reports.save(new CommentReport(
                comments.findById(commentId).orElseThrow(), reporter, CommentReportReason.ABUSE, null)).getId();
        em.flush();
        return id;
    }

    /** 실제로 7개월이 지난 것처럼 시각을 당긴다 */
    private void ageSevenMonths(String table, List<Long> ids) {
        em.flush();
        for (Long id : ids) {
            em.createNativeQuery("update " + table
                    + " set created_at = timestampadd(month, -7, created_at) where id = " + id).executeUpdate();
        }
        em.clear();
    }

    private List<Long> historyIds(Long commentId) {
        em.flush();
        return histories.findByTargetTypeAndTargetIdOrderByIdDesc(ContentHistory.Target.COMMENT, commentId)
                .stream().map(ContentHistory::getId).toList();
    }

    private org.springframework.test.web.servlet.ResultActions detail(Long commentId) throws Exception {
        return mockMvc.perform(get(COMMENTS + commentId).with(user(admin)));
    }

    @Test
    @DisplayName("수정 기록이 남아 있으면 신고 당시 본문을 확인해 보여 준다")
    void confirmsWhileHistoryRemains() throws Exception {
        Long id = write("신고 당시 원문");
        report(id);
        edit(id, "현재 수정된 내용");

        detail(id)
                .andExpect(jsonPath("$.data.content").value("현재 수정된 내용"))
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("신고 당시 원문"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(true));
    }

    @Test
    @DisplayName("6개월이 지나 수정 기록을 파기하면 지금 본문으로 대신하지 않고 확인할 수 없다고 답한다")
    void doesNotSubstituteCurrentContentAfterHistoryExpires() throws Exception {
        Long id = write("신고 당시 원문");
        Long reportId = report(id);
        edit(id, "현재 수정된 내용");

        ageSevenMonths("content_history", historyIds(id));
        ageSevenMonths("comment_report", List.of(reportId));
        ageSevenMonths("comment", List.of(id));
        retention.purgeExpired(LocalDateTime.now());

        detail(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("현재 수정된 내용"))
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").doesNotExist())
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").doesNotExist())
                .andExpect(jsonPath("$.data.histories.length()").value(0));
    }

    @Test
    @DisplayName("기록 일부만 파기돼도 남은 수정 기록으로 신고 당시 본문을 짐작하지 않는다")
    void partialExpiryIsNotGuessed() throws Exception {
        Long id = write("신고 당시 원문");
        Long reportId = report(id);
        edit(id, "첫 번째 수정");
        List<Long> older = historyIds(id);
        edit(id, "두 번째 수정");

        ageSevenMonths("content_history", older);
        ageSevenMonths("comment_report", List.of(reportId));
        retention.purgeExpired(LocalDateTime.now());

        detail(id)
                .andExpect(jsonPath("$.data.histories.length()").value(1))
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").doesNotExist());
    }

    @Test
    @DisplayName("기록 기능 전에 쓰고 고친 댓글은 신고 당시 본문을 확인할 수 없다고 답한다")
    void legacyEditedCommentIsNotRecorded() throws Exception {
        Comment legacy = comments.save(new Comment(post, null, writer, "옛 원문"));
        legacy.updateContent("옛날에 고친 말");
        report(legacy.getId());

        detail(legacy.getId())
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").doesNotExist())
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").doesNotExist());
    }

    @Test
    @DisplayName("기록 기능 전 댓글이어도 신고 때 이미 기록이 쌓이고 있었다면 신고 뒤 수정은 다 남아 있다")
    void legacyCommentReportedAfterRecordingStarted() throws Exception {
        write("다른 댓글");
        Comment legacy = comments.save(new Comment(post, null, writer, "옛 원문"));
        legacy.updateContent("옛날에 고친 말");
        em.flush();
        report(legacy.getId());

        detail(legacy.getId())
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("옛날에 고친 말"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(false));
    }

    @Test
    @DisplayName("한 번도 고치지 않은 댓글은 기록이 없어도 지금 본문이 신고 당시 본문이다")
    void neverEditedCommentIsConfirmed() throws Exception {
        Comment legacy = comments.save(new Comment(post, null, writer, "그대로인 말"));
        Long reportId = report(legacy.getId());
        ageSevenMonths("comment_report", List.of(reportId));

        detail(legacy.getId())
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("그대로인 말"))
                .andExpect(jsonPath("$.data.reports[0].editedAfterReport").value(false));
    }

    @Test
    @DisplayName("지운 원문은 보관 기한과 함께 관리자에게만 보인다")
    void deletedContentIsKeptWithDeadline() throws Exception {
        Long id = write("지운 말");
        report(id);
        commentService.deleteComment(id, writer.getId());
        em.flush();
        LocalDateTime deletedAt = comments.findById(id).orElseThrow().getDeletedAt();

        detail(id)
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.hiddenByAdmin").value(false))
                .andExpect(jsonPath("$.data.content").value("지운 말"))
                .andExpect(jsonPath("$.data.contentPurged").value(false))
                .andExpect(jsonPath("$.data.contentRetainedUntil").value(
                        startsWith(deletedAt.plus(CommentRetention.RETENTION).toString().substring(0, 19))))
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("원문을 파기한 뒤에는 관리자도 볼 수 없고 되살릴 수도 없다")
    void purgedContentIsGoneForAdminsToo() throws Exception {
        Long id = write("지운 말");
        report(id);
        commentService.deleteComment(id, writer.getId());
        em.flush();
        Comment comment = comments.findById(id).orElseThrow();
        retention.purgeExpired(comment.getDeletedAt().plus(CommentRetention.RETENTION).plusSeconds(1));
        em.clear();

        detail(id)
                .andExpect(jsonPath("$.data.contentPurged").value(true))
                .andExpect(jsonPath("$.data.content").value(""))
                .andExpect(jsonPath("$.data.contentRetainedUntil").doesNotExist())
                .andExpect(jsonPath("$.data.reports[0].reportedContentStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").doesNotExist())
                .andExpect(jsonPath("$.data.histories.length()").value(0));

        mockMvc.perform(put(COMMENTS + id + "/visibility").with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":false,\"reason\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_CONTENT_PURGED"));

        mockMvc.perform(get(LIST).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].contentPurged").value(true));
    }

    @Test
    @DisplayName("회원이나 비로그인 사용자는 댓글 상세와 기록을 볼 수 없다")
    void nonAdminsCannotSeeDetail() throws Exception {
        Long id = write("지운 말");
        commentService.deleteComment(id, writer.getId());

        mockMvc.perform(get(COMMENTS + id).with(user(MemberPrincipal.ofSocial(reporter))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(COMMENTS + id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(LIST).with(user(MemberPrincipal.ofSocial(writer))))
                .andExpect(status().isForbidden());
    }
}
