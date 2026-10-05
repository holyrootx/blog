package me.jsjlog.blog.admin.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import me.jsjlog.blog.admin.dto.*;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.*;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.repository.*;
import me.jsjlog.blog.post.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostPurgeTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:post_purge;MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    @Autowired AdminPostService adminPosts;
    @Autowired AdminCommentService adminComments;
    @Autowired PostService publicPosts;
    @Autowired PostRepository posts;
    @Autowired CategoryRepository categories;
    @Autowired MemberRepository members;
    @Autowired CommentRepository comments;
    @Autowired CommentReportRepository reports;
    @Autowired PostReactionRepository reactions;
    @Autowired ContentHistoryRepository histories;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    private Post target;
    private Category category;
    private Member admin;
    private Member member;

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();
        category = categories.save(new Category("purge-" + key, 1L));
        admin = members.save(Member.ofLocalAdmin("admin-" + key, "unused", "관리자"));
        member = members.save(Member.ofSocial(AuthProvider.GOOGLE, key, "회원", null, null));
        target = posts.save(new Post("purge-title", "비밀 요약", "비밀 원문", category,
                "https://external.invalid/image.jpg", admin));
        target.publish(LocalDateTime.now().minusMonths(2));
        em.flush();
    }

    private void trash() {
        target.delete(LocalDateTime.now().minusDays(31).withNano(0));
        em.flush();
    }

    private AdminPostPurgeRequest confirmation() {
        return new AdminPostPurgeRequest(target.getTitle(), target.getDeletedAt());
    }

    private void expectCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BlogException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    @Test
    void erasesOriginalAndLegacyPostCopiesButKeepsCommentEvidence() {
        Comment comment = comments.save(new Comment(target, null, member, "신고 조사할 댓글"));
        CommentReport report = reports.save(new CommentReport(comment, admin, CommentReportReason.ABUSE, "사유"));
        reactions.save(new PostReaction(target, member, PostReactionType.LIKE));
        histories.save(new ContentHistory(ContentHistory.Target.POST, target.getId(), target.getId(),
                ContentHistory.Action.UPDATE, "옛 글 사본", "최근 글 사본"));
        ContentHistory commentHistory = histories.save(new ContentHistory(ContentHistory.Target.COMMENT,
                comment.getId(), target.getId(), ContentHistory.Action.CREATE, null,
                "{\"content\":\"신고 조사할 댓글\",\"deleted\":false}"));
        trash();
        LocalDateTime deletedAt = target.getDeletedAt();

        adminPosts.purgePost(target.getId(), confirmation());
        em.flush();
        em.clear();

        Post purged = posts.findById(target.getId()).orElseThrow();
        assertThat(purged.isContentPurged()).isTrue();
        assertThat(purged.getContent()).isEmpty();
        assertThat(purged.getTitle()).isEqualTo("영구 삭제된 글");
        assertThat(purged.getExcerpt()).isNull();
        assertThat(purged.getThumbnailImageUrl()).isNull();
        assertThat(purged.getCategory()).isNull();
        assertThat(purged.getAuthor()).isNull();
        assertThat(purged.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(purged.getPublishedAt()).isNull();
        assertThat(reactions.countByPostIdAndType(target.getId(), PostReactionType.LIKE)).isZero();
        assertThat(histories.findByTargetTypeAndTargetIdOrderByIdDesc(ContentHistory.Target.POST, target.getId())).isEmpty();
        assertThat(histories.existsById(commentHistory.getId())).isTrue();
        assertThat(comments.findById(comment.getId()).orElseThrow().getContent()).isEqualTo("신고 조사할 댓글");
        assertThat(reports.existsById(report.getId())).isTrue();
        assertThat(posts.existsByCategoryId(category.getId())).isFalse();
        categories.deleteById(category.getId());
        em.flush();

        var trash = adminPosts.getPostList(new AdminPostSearchCondition(0, 20, null, null, null, true));
        assertThat(trash.items()).isEmpty();
        assertThat(trash.statusCounts().trash()).isZero();
        assertThat(trash.statusCounts().all()).isZero();
        expectCode(() -> publicPosts.getPostDetail(target.getId(), false, null), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.restorePost(target.getId()), ErrorCode.POST_CONTENT_PURGED);
    }

    @Test
    void rejectsActivePostsAndPostsWithinRestoreWindow() {
        expectCode(() -> adminPosts.purgePost(target.getId(), confirmation()), ErrorCode.POST_NOT_DELETED);
        target.delete(LocalDateTime.now());
        expectCode(() -> adminPosts.purgePost(target.getId(), confirmation()), ErrorCode.POST_PURGE_TOO_EARLY);
        assertThat(target.getContent()).isEqualTo("비밀 원문");
    }

    @Test
    void requiresExactTitleAndDeletionTimestampAndRejectsRepeat() {
        trash();
        expectCode(() -> adminPosts.purgePost(target.getId(), new AdminPostPurgeRequest("wrong", target.getDeletedAt())),
                ErrorCode.POST_PURGE_CONFIRMATION_MISMATCH);
        expectCode(() -> adminPosts.purgePost(target.getId(), new AdminPostPurgeRequest(target.getTitle(), target.getDeletedAt().minusSeconds(1))),
                ErrorCode.POST_PURGE_CONFIRMATION_MISMATCH);
        expectCode(() -> adminPosts.purgePost(target.getId(), null), ErrorCode.POST_PURGE_CONFIRMATION_MISMATCH);
        AdminPostPurgeRequest request = confirmation();
        adminPosts.purgePost(target.getId(), request);
        expectCode(() -> adminPosts.purgePost(target.getId(), request), ErrorCode.POST_CONTENT_PURGED);
    }

    @Test
    void administratorCanReviewRetainedReportsButCannotRestoreOrReply() {
        Comment comment = comments.save(new Comment(target, null, member, "조사 중인 댓글"));
        CommentReport report = reports.save(new CommentReport(comment, admin, CommentReportReason.ABUSE, "사유"));
        trash();
        adminPosts.purgePost(target.getId(), confirmation());
        em.flush();

        var list = adminComments.getComments(new AdminCommentSearchCondition(0, 20, null, null, null));
        assertThat(list.items()).singleElement().satisfies(item -> assertThat(item.postPurged()).isTrue());
        assertThat(list.statusCounts().unanswered()).isZero();
        assertThat(list.statusCounts().reported()).isEqualTo(1);
        var detail = adminComments.getDetail(comment.getId());
        assertThat(detail.postPurged()).isTrue();
        assertThat(detail.contentRetainedUntil()).isEqualTo(target.getContentPurgedAt().plusMonths(6));
        expectCode(() -> adminComments.updateVisibility(comment.getId(), new AdminCommentVisibilityRequest(false, "복구"), admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminComments.reply(comment.getId(), new AdminCommentReplyRequest("답글"), admin.getId()), ErrorCode.POST_NOT_FOUND);
        adminComments.dismissReports(comment.getId(), "확인 완료", admin.getId());
        assertThat(reports.findById(report.getId()).orElseThrow().getHandledAt()).isNotNull();
    }

    @Test
    void existingCommentDeletionDeadlineIsNotExtendedByPostPurge() {
        Comment comment = comments.save(new Comment(target, null, member, "먼저 삭제한 댓글"));
        LocalDateTime deletedAt = LocalDateTime.now().minusMonths(5).withNano(0);
        comment.delete(deletedAt);
        trash();
        adminPosts.purgePost(target.getId(), confirmation());
        assertThat(adminComments.getDetail(comment.getId()).contentRetainedUntil()).isEqualTo(deletedAt.plusMonths(6));
    }

    @Test
    void permanentDeleteEndpointRequiresAdminCsrfAndConfirmation() throws Exception {
        trash();
        String path = "/api/v1/admin/blog/posts/" + target.getId() + "/permanent";
        String request = "{\"title\":\"purge-title\",\"deletedAt\":\"" + target.getDeletedAt() + "\"}";
        mvc.perform(delete(path).with(user(MemberPrincipal.ofSocial(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user(MemberPrincipal.ofLocal(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/blog/posts?trash=true").with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].purgeable").value(true));
        mvc.perform(delete(path).with(user(MemberPrincipal.ofLocal(admin))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        assertThat(posts.findById(target.getId()).orElseThrow().isContentPurged()).isTrue();
    }

    @Test
    void thirtyDayBoundarySwitchesFromRestorableToPurgeable() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        target.delete(deletedAt);
        assertThat(target.canRestore(deletedAt.plusDays(30).minusNanos(1))).isTrue();
        assertThat(target.canPurge(deletedAt.plusDays(30).minusNanos(1))).isFalse();
        assertThat(target.canRestore(deletedAt.plusDays(30))).isFalse();
        assertThat(target.canPurge(deletedAt.plusDays(30))).isTrue();
        target.purgeContent(deletedAt.plusDays(30));
        assertThat(target.canRestore(deletedAt.plusDays(1))).isFalse();
        assertThat(target.canPurge(deletedAt.plusDays(31))).isFalse();
    }
}
