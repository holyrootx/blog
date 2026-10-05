package me.jsjlog.blog.post.service;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.admin.dto.AdminCommentVisibilityRequest;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentModeration;
import me.jsjlog.blog.post.domain.CommentModerationAction;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentModerationRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 글쓴이가 지운 원문은 지운 시각부터, 변경 기록은 기록된 시각부터 6개월이 지나면 파기한다.
 *
 * 다른 테스트의 기록과 섞이면 "남은 기록 수" 를 셀 수 없어서 따로 만든 DB 를 쓴다.
 * 시각이 걸린 판정은 기준 시각을 넘겨 확인한다. 날짜 계산이 달 끝에서 흔들리지 않게
 * 기준을 15일로 잡는다.
 */
@SpringBootTest
@Transactional
class CommentRetentionTest {

    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 4, 15, 10, 0);
    private static final LocalDateTime EXPIRES_AT = DELETED_AT.plus(CommentRetention.RETENTION);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:comment_retention;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired private CommentRetention retention;
    @Autowired private CommentRepository comments;
    @Autowired private ContentHistoryRepository histories;
    @Autowired private CommentReportRepository reports;
    @Autowired private CommentModerationRepository moderations;
    @Autowired private CommentService commentService;
    @Autowired private AdminCommentService adminComments;
    @Autowired private CategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private MemberRepository members;
    @Autowired private EntityManager em;

    private Post post;
    private Member writer;
    private Member admin;

    @BeforeEach
    void setUp() {
        Category category = categories.save(new Category("보관 기간", 960L));
        post = new Post("보관 기간 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = posts.save(post);
        writer = members.save(Member.ofSocial(AuthProvider.NAVER, "retention-writer", "글쓴이", null, null));
        admin = members.save(Member.ofLocalAdmin("retention-admin", "encoded-password", "운영자"));
    }

    private Comment deletedAt(String content, LocalDateTime at) {
        Comment comment = comments.save(new Comment(post, null, writer, content));
        comment.delete(at);
        em.flush();
        return comment;
    }

    private Comment reload(Comment comment) {
        em.flush();
        em.clear();
        return comments.findById(comment.getId()).orElseThrow();
    }

    @Test
    @DisplayName("정확히 6개월이 된 원문은 아직 남긴다 — 다음 날 정리 때 지운다")
    void keepsContentAtExactBoundary() {
        Comment comment = deletedAt("지운 말", DELETED_AT);

        assertThat(retention.purgeExpired(EXPIRES_AT).purgedComments()).isZero();
        assertThat(reload(comment).getContent()).isEqualTo("지운 말");
        assertThat(reload(comment).isContentPurged()).isFalse();
    }

    @Test
    @DisplayName("6개월이 지나기 직전에는 남긴다")
    void keepsContentJustBeforeExpiry() {
        Comment comment = deletedAt("지운 말", DELETED_AT);

        retention.purgeExpired(EXPIRES_AT.minusSeconds(1));

        assertThat(reload(comment).getContent()).isEqualTo("지운 말");
    }

    @Test
    @DisplayName("6개월이 지나면 원문을 비우고 레코드는 남긴다")
    void purgesContentAfterExpiry() {
        Comment comment = deletedAt("지운 말", DELETED_AT);
        LocalDateTime now = EXPIRES_AT.plusSeconds(1);

        assertThat(retention.purgeExpired(now).purgedComments()).isEqualTo(1);

        Comment purged = reload(comment);
        assertThat(purged.getContent()).isEmpty();
        assertThat(purged.getContentPurgedAt()).isEqualTo(now);
        assertThat(purged.isDeleted()).isTrue();
        assertThat(purged.isHiddenByAdmin()).isFalse();
    }

    @Test
    @DisplayName("다시 돌려도 한 번만 파기하고 파기 시각도 바뀌지 않는다")
    void purgingTwiceIsHarmless() {
        Comment comment = deletedAt("지운 말", DELETED_AT);
        LocalDateTime first = EXPIRES_AT.plusDays(1);

        retention.purgeExpired(first);
        assertThat(retention.purgeExpired(first.plusDays(1)).purgedComments()).isZero();

        assertThat(reload(comment).getContentPurgedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("답글이 달린 댓글을 파기해도 답글과 연결은 그대로다")
    void keepsRepliesAndTheirParent() {
        Comment parent = deletedAt("부모 원문", DELETED_AT);
        Comment reply = comments.save(new Comment(post, parent, admin, "답글"));
        em.flush();

        retention.purgeExpired(EXPIRES_AT.plusDays(1));

        Comment savedReply = reload(reply);
        assertThat(savedReply.getContent()).isEqualTo("답글");
        assertThat(savedReply.getParent().getId()).isEqualTo(parent.getId());
        assertThat(comments.findById(parent.getId()).orElseThrow().isContentPurged()).isTrue();
    }

    @Test
    @DisplayName("신고와 조치 기록은 파기하지 않는다 — 어느 댓글에 무엇을 했는지는 남는다")
    void keepsReportAndModerationMetadata() {
        Comment comment = deletedAt("지운 말", DELETED_AT);
        CommentReport report = reports.save(new CommentReport(comment, admin, CommentReportReason.ABUSE, "사유"));
        CommentModeration moderation = moderations.save(
                CommentModeration.of(comment, admin, CommentModerationAction.DISMISS, "문제 없음"));
        em.flush();

        retention.purgeExpired(EXPIRES_AT.plusDays(1));
        em.clear();

        assertThat(reports.findById(report.getId())).isPresent();
        assertThat(moderations.findById(moderation.getId())).isPresent();
    }

    @Test
    @DisplayName("운영자가 가리기만 한 댓글은 기간이 지나도 파기하지 않는다")
    void keepsAdminHiddenComment() {
        Comment comment = comments.save(new Comment(post, null, writer, "가린 말"));
        comment.hideByAdmin();
        em.flush();

        retention.purgeExpired(LocalDateTime.now().plusYears(2));

        Comment saved = reload(comment);
        assertThat(saved.getContent()).isEqualTo("가린 말");
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.isContentPurged()).isFalse();
    }

    @Test
    @DisplayName("지운 뒤 운영자가 가려도 지운 시각부터 센다. 그 뒤에 생긴 기록도 함께 지운다")
    void authorDeletionStillCountsAfterAdminHides() {
        LocalDateTime longAgo = LocalDateTime.now().minusMonths(7);
        Comment comment = deletedAt("지운 말", longAgo);
        adminComments.updateVisibility(comment.getId(), new AdminCommentVisibilityRequest(true, "가림"), admin.getId());
        em.flush();
        assertThat(histories.findAll()).extracting(ContentHistory::getAction).contains(ContentHistory.Action.HIDE);

        retention.purgeExpired(LocalDateTime.now());

        assertThat(reload(comment).isContentPurged()).isTrue();
        assertThat(histories.findAll()).isEmpty();
    }

    @Test
    @DisplayName("파기 전에 되살리면 파기하지 않고, 다시 지우면 그때부터 다시 센다")
    void restoreAndDeleteAgainRestartsTheClock() {
        Long id = commentService.createComment(post.getId(), new CommentCreateRequest("말", null), writer.getId()).id();
        commentService.deleteComment(id, writer.getId());
        adminComments.updateVisibility(id, new AdminCommentVisibilityRequest(false, "되돌림"), admin.getId());
        em.flush();

        Comment restored = comments.findById(id).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(retention.purgeExpired(LocalDateTime.now().plusYears(1)).purgedComments()).isZero();

        em.clear();
        commentService.deleteComment(id, writer.getId());
        LocalDateTime redeletedAt = reload(comments.findById(id).orElseThrow()).getDeletedAt();
        assertThat(redeletedAt).isNotNull();

        assertThat(retention.purgeExpired(redeletedAt.plus(CommentRetention.RETENTION)).purgedComments()).isZero();
        assertThat(retention.purgeExpired(redeletedAt.plus(CommentRetention.RETENTION).plusSeconds(1)).purgedComments())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("공개 중인 댓글의 본문은 지우지 않는다. 그 댓글의 변경 기록만 기한이 되면 지운다")
    void keepsLiveCommentButExpiresItsHistory() {
        Long id = commentService.createComment(post.getId(), new CommentCreateRequest("처음", null), writer.getId()).id();
        commentService.updateComment(id, "고친 말", writer.getId());
        em.flush();
        assertThat(histories.count()).isEqualTo(2);

        CommentRetention.Result result = retention.purgeExpired(LocalDateTime.now().plusMonths(6).plusDays(1));

        assertThat(result.purgedComments()).isZero();
        assertThat(result.deletedHistories()).isEqualTo(2);
        assertThat(reload(comments.findById(id).orElseThrow()).getContent()).isEqualTo("고친 말");
    }

    @Test
    @DisplayName("6개월이 안 된 변경 기록은 남긴다")
    void keepsRecentHistory() {
        commentService.createComment(post.getId(), new CommentCreateRequest("남길 말", null), writer.getId());
        em.flush();

        assertThat(retention.purgeExpired(LocalDateTime.now().plusMonths(6).minusDays(1)).deletedHistories()).isZero();
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("언제 지웠는지 근거가 없는 예전 삭제 댓글은 자동으로 파기하지 않는다")
    void leavesLegacyDeletionsWithoutTimestamp() {
        Comment legacy = comments.save(new Comment(post, null, writer, "예전에 지운 말"));
        legacy.delete();
        em.flush();
        em.createNativeQuery("update comment set deleted_at = null where id = " + legacy.getId()).executeUpdate();
        em.clear();

        retention.purgeExpired(LocalDateTime.now().plusYears(5));

        Comment saved = comments.findById(legacy.getId()).orElseThrow();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getContent()).isEqualTo("예전에 지운 말");
    }

    @Test
    void purgesEveryCommentOfPermanentlyDeletedPostAtItsDeadline() {
        Comment parent = comments.save(new Comment(post, null, writer, "원댓글"));
        Comment reply = comments.save(new Comment(post, parent, admin, "답글"));
        post.delete(DELETED_AT.minusDays(31));
        post.purgeContent(DELETED_AT);
        // 오래된 글에 대한 조사를 나중에 기록해도 원문 파기 때 그 사본을 함께 지워야 한다.
        histories.save(new ContentHistory(ContentHistory.Target.COMMENT, parent.getId(), post.getId(),
                ContentHistory.Action.HIDE, "원댓글", "원댓글"));
        em.flush();

        assertThat(retention.purgeExpired(EXPIRES_AT).purgedComments()).isZero();
        assertThat(retention.purgeExpired(EXPIRES_AT.plusSeconds(1)).purgedComments()).isEqualTo(2);
        assertThat(reload(parent).getContent()).isEmpty();
        Comment savedReply = reload(reply);
        assertThat(savedReply.getContent()).isEmpty();
        assertThat(savedReply.getParent().getId()).isEqualTo(parent.getId());
        assertThat(histories.findAll()).isEmpty();
    }

    @Test
    void postPurgeDoesNotRestartAnAlreadyDeletedCommentsClock() {
        Comment older = deletedAt("먼저 지운 댓글", DELETED_AT.minusMonths(2));
        Comment live = comments.save(new Comment(post, null, writer, "남길 댓글"));
        post.delete(DELETED_AT.minusDays(31));
        post.purgeContent(DELETED_AT);
        em.flush();

        assertThat(retention.purgeExpired(EXPIRES_AT.minusMonths(1)).purgedComments()).isEqualTo(1);
        assertThat(reload(older).getContent()).isEmpty();
        assertThat(reload(live).getContent()).isEqualTo("남길 댓글");
    }

    @Test
    void softTrashedPostAloneDoesNotStartCommentPurge() {
        Comment live = comments.save(new Comment(post, null, writer, "복구 대기 댓글"));
        post.delete(DELETED_AT.minusYears(2));
        em.flush();
        assertThat(retention.purgeExpired(EXPIRES_AT).purgedComments()).isZero();
        assertThat(reload(live).getContent()).isEqualTo("복구 대기 댓글");
    }
}
