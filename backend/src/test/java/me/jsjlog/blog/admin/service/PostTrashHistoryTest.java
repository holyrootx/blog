package me.jsjlog.blog.admin.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.*;
import me.jsjlog.blog.admin.repository.*;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.domain.ContentHistory.Action;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.*;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.*;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.dto.*;
import me.jsjlog.blog.post.repository.*;
import me.jsjlog.blog.post.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostTrashHistoryTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("TRASH_TEST_DB_URL");
        if (url != null && !url.startsWith("jdbc:mysql://127.0.0.1:3306/blog_trash_verify_20261003")) {
            throw new IllegalArgumentException("Only the dedicated local verification database is allowed");
        }
        registry.add("spring.datasource.url", () -> url == null
                ? "jdbc:h2:mem:trash_history;MODE=MySQL;DB_CLOSE_DELAY=-1" : url);
        registry.add("spring.datasource.driver-class-name",
                () -> url == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username",
                () -> url == null ? "sa" : System.getenv("TRASH_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password",
                () -> url == null ? "" : System.getenv("TRASH_TEST_DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("blog.scheduled-publish.interval-ms", () -> "3600000");
    }

    @Autowired AdminPostService adminPosts;
    @Autowired AdminCommentService adminComments;
    @Autowired PostService publicPosts;
    @Autowired CommentService comments;
    @Autowired ScheduledPublisher scheduler;
    @Autowired PostRepository posts;
    @Autowired CommentRepository commentRepository;
    @Autowired CategoryRepository categories;
    @Autowired MemberRepository members;
    @Autowired CommentReportRepository reports;
    @Autowired CommentModerationRepository moderations;
    @Autowired CommentReactionRepository commentReactions;
    @Autowired PostReactionRepository postReactions;
    @Autowired NotificationRepository notifications;
    @Autowired AdminDashboardQueryRepository dashboard;
    @Autowired ImageUsageRepository images;
    @Autowired ImageCleanupService cleanup;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean ContentHistoryRepository histories;

    private Post post;
    private Member admin;
    private Member member;
    private Category category;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            category = categories.save(new Category("trash-" + key, 1L));
            admin = members.save(Member.ofLocalAdmin("admin-" + key, "unused-test-hash", "관리자"));
            member = members.save(Member.ofSocial(AuthProvider.GOOGLE, key, "회원", null, null));
            post = posts.save(new Post("trash-" + key, "요약", "본문", category, null, admin));
            post.publish(LocalDateTime.now().minusDays(1));
            em.flush();
        });
    }

    private List<ContentHistory> history(Long postId) {
        return histories.findAll().stream().filter(h -> h.getPostId().equals(postId))
                .sorted(java.util.Comparator.comparing(ContentHistory::getId)).toList();
    }

    private void expectCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BlogException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void deleteAndRestoreRetainEveryRelationAndCommentState() {
        Comment root = commentRepository.save(new Comment(post, null, member, "댓글"));
        Comment reply = commentRepository.save(new Comment(post, root, admin, "답글"));
        Comment deleted = commentRepository.save(new Comment(post, null, member, "회원 삭제"));
        Comment hidden = commentRepository.save(new Comment(post, null, member, "관리자 숨김"));
        deleted.delete();
        hidden.hideByAdmin();
        CommentReport report = reports.save(new CommentReport(root, admin, CommentReportReason.values()[0], null));
        CommentModeration moderation = moderations.save(
                CommentModeration.of(hidden, admin, CommentModerationAction.HIDE, "보존"));
        CommentReaction reaction = commentReactions.save(new CommentReaction(root, admin, CommentReactionType.LIKE));
        PostReaction postReaction = postReactions.save(new PostReaction(post, member, PostReactionType.LIKE));
        Notification notification = notifications.save(new Notification(member, NotificationType.values()[0], reply));
        em.flush();

        adminPosts.deletePost(post.getId());
        em.flush();
        em.clear();

        assertThat(posts.findById(post.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(commentRepository.existsById(root.getId())).isTrue();
        assertThat(commentRepository.existsById(reply.getId())).isTrue();
        assertThat(reports.existsById(report.getId())).isTrue();
        assertThat(moderations.existsById(moderation.getId())).isTrue();
        assertThat(commentReactions.existsById(reaction.getId())).isTrue();
        assertThat(postReactions.existsById(postReaction.getId())).isTrue();
        assertThat(notifications.existsById(notification.getId())).isTrue();
        assertThat(notifications.findMine(member.getId(), PageRequest.of(0, 20))).isEmpty();
        assertThat(notifications.countUnread(member.getId())).isZero();
        assertThat(commentRepository.findMyComments(member.getId(), LocalDateTime.now(), PageRequest.of(0, 20))).isEmpty();

        adminPosts.restorePost(post.getId());
        em.flush();
        em.clear();

        Post restored = posts.findById(post.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(restored.getPublishedAt()).isNull();
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(commentRepository.findById(deleted.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(commentRepository.findById(hidden.getId()).orElseThrow().isHiddenByAdmin()).isTrue();
        // 글 변경 기록은 남기지 않는다. 쓰는 곳이 없어 2026-025 에서 뺐다
        assertThat(history(post.getId())).isEmpty();
        expectCode(() -> publicPosts.getPostDetail(post.getId(), false, null), ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void deletedPostsAreAbsentFromEveryListAndScheduler() {
        adminPosts.deletePost(post.getId());
        em.flush();
        scheduler.publishDuePosts();

        assertThat(posts.getPostsForHomePage("latest", 50L)).extracting(PostSummaryResponse::id).doesNotContain(post.getId());
        assertThat(posts.getPostsForHomePage("popular", 50L)).extracting(PostSummaryResponse::id).doesNotContain(post.getId());
        assertThat(posts.getPublicPostSuggestions(post.getTitle(), 10)).isEmpty();
        assertThat(posts.countPublicPosts(new PostListCondition(0, 20, category.getId(), null, null))).isZero();
        assertThat(posts.getPublicPosts(new PostListCondition(0, 20, category.getId(), null, null))).isEmpty();
        assertThat(posts.findPublicPostIdsForSitemap(PostStatus.PUBLISHED, LocalDateTime.now())).doesNotContain(post.getId());
        assertThat(posts.getPostDetail(post.getId())).isNull();
        assertThat(posts.increaseViewCount(post.getId(), PostStatus.PUBLISHED, LocalDateTime.now())).isZero();
        assertThat(posts.findDueScheduledPosts(LocalDateTime.now())).extracting(Post::getId).doesNotContain(post.getId());
        assertThat(posts.existsByCategoryId(category.getId())).isTrue();
        assertThat(posts.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);

        var active = adminPosts.getPostList(new AdminPostSearchCondition(0, 20, null, category.getId(), null, false));
        var trash = adminPosts.getPostList(new AdminPostSearchCondition(0, 20, null, category.getId(), null, true));
        assertThat(active.items()).isEmpty();
        assertThat(active.statusCounts().all()).isZero();
        assertThat(active.statusCounts().trash()).isEqualTo(1);
        assertThat(trash.items()).hasSize(1);
        assertThat(trash.items().getFirst().restorable()).isTrue();
        assertThat(trash.items().getFirst().restoreUntil()).isNotNull();
    }

    @Test
    void deletedPostRejectsAllPublicAndAdminMutations() {
        Comment comment = commentRepository.save(new Comment(post, null, member, "댓글"));
        adminPosts.deletePost(post.getId());
        em.flush();
        Long id = post.getId();

        expectCode(() -> comments.createComment(id, new CommentCreateRequest("댓글", null), member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> comments.updateComment(comment.getId(), "수정", member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> comments.deleteComment(comment.getId(), member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> comments.reportComment(comment.getId(), CommentReportReason.values()[0], null, admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> comments.setReaction(comment.getId(), CommentReactionType.LIKE, admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> comments.removeReaction(comment.getId(), CommentReactionType.LIKE, admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> publicPosts.setReaction(id, PostReactionType.LIKE, member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> publicPosts.removeReaction(id, PostReactionType.LIKE, member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> publicPosts.getRelatedPosts(id), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> publicPosts.getAdjacentPost(id), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> publicPosts.getCommentInPostDetail(id, null, 20L, member.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.getPost(id), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.updatePost(id, new AdminPostRequest(null, "수정", category.getId(), "요약", "본문", null)), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.publishPost(id, null), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.unpublishPost(id), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminPosts.deletePost(id), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminComments.reply(comment.getId(), new AdminCommentReplyRequest("답글"), admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminComments.updateVisibility(comment.getId(), new AdminCommentVisibilityRequest(true, null), admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminComments.dismissReports(comment.getId(), null, admin.getId()), ErrorCode.POST_NOT_FOUND);
        expectCode(() -> adminComments.getDetail(comment.getId()), ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void restoreWindowEndsExactlyThirtyDaysAfterDeletion() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        post.delete(deletedAt);
        assertThat(post.canRestore(deletedAt.plusDays(30).minusNanos(1))).isTrue();
        assertThat(post.canRestore(deletedAt.plusDays(30))).isFalse();
        assertThatThrownBy(() -> post.restore(deletedAt.plusDays(30))).isInstanceOf(IllegalStateException.class);
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void expiredRestoreAndSecondRestoreAreRejected() {
        expectCode(() -> adminPosts.restorePost(post.getId()), ErrorCode.POST_NOT_DELETED);
        post.delete(LocalDateTime.now().minusDays(31));
        em.flush();
        expectCode(() -> adminPosts.restorePost(post.getId()), ErrorCode.POST_RESTORE_EXPIRED);
        assertThat(posts.existsById(post.getId())).isTrue();
        assertThat(history(post.getId())).isEmpty();
    }

    @Test
    void imageRemovedByEditIsNoLongerHeldByHistory() {
        String url = "https://images.test/2026/10/" + UUID.randomUUID() + "-thumbnail-v1.0.webp";
        UploadImage image = images.save(new UploadImage("2026/10/source.jpg", url, "image.jpg", "image/jpeg", 100));
        post.update(post.getTitle(), "요약", "![image](" + url + ")", category, url);
        em.flush();

        adminPosts.updatePost(post.getId(), new AdminPostRequest(null, "수정된 제목", category.getId(), "요약", "새 본문", null));
        em.flush();

        assertThat(history(post.getId())).isEmpty();
        assertThat(images.findStillUsed(Set.of(url))).isEmpty();
        assertThat(images.findUnusedOlderThan(LocalDateTime.now().plusDays(1), PageRequest.of(0, 100)).getContent())
                .extracting(UploadImage::getId).contains(image.getId());
        assertThat(cleanup.cleanup(List.of(image.getId())).skippedUsedCount()).isZero();
    }

    @Test
    void commentLifecycleSavesHistoryButScheduledPublishDoesNot() {
        Long commentId = comments.createComment(post.getId(), new CommentCreateRequest("처음", null), member.getId()).id();
        comments.updateComment(commentId, "변경", member.getId());
        adminComments.updateVisibility(commentId, new AdminCommentVisibilityRequest(true, "숨김"), admin.getId());
        adminComments.updateVisibility(commentId, new AdminCommentVisibilityRequest(false, "복원"), admin.getId());
        comments.deleteComment(commentId, member.getId());
        em.flush();
        assertThat(history(post.getId())).extracting(ContentHistory::getAction)
                .containsExactly(Action.CREATE, Action.UPDATE, Action.HIDE, Action.RESTORE, Action.DELETE);
        assertThat(history(post.getId()).get(1).getBeforeSnapshot()).contains("처음");
        assertThat(history(post.getId()).get(1).getAfterSnapshot()).contains("변경");

        post.schedule(LocalDateTime.now().minusMinutes(1));
        em.flush();
        scheduler.publishDuePosts();
        assertThat(posts.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(history(post.getId()).getLast().getAction()).isEqualTo(Action.DELETE);
    }

    @Test
    void trashedScheduledPostIsNeverPublished() {
        post.schedule(LocalDateTime.now().minusMinutes(1));
        adminPosts.deletePost(post.getId());
        em.flush();
        scheduler.publishDuePosts();
        assertThat(posts.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(history(post.getId())).isEmpty();
    }

    @Test
    void postChangesAreNotRecorded() {
        Long id = adminPosts.createPost(new AdminPostRequest(null, "이력", category.getId(), "요약", "본문", null), admin.getId());
        adminPosts.updatePost(id, new AdminPostRequest(null, "수정", category.getId(), "요약", "다른 본문", null));
        adminPosts.publishPost(id, null);
        adminPosts.unpublishPost(id);
        adminPosts.publishPost(id, LocalDateTime.now().plusDays(1));
        em.flush();
        assertThat(history(id)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void historyFailureAlsoRollsBackCommentEdits() {
        Long id = new TransactionTemplate(transactionManager).execute(status ->
                commentRepository.save(new Comment(posts.findById(post.getId()).orElseThrow(), null,
                        members.findById(member.getId()).orElseThrow(), "원래 내용")).getId());
        doThrow(new IllegalStateException("history unavailable")).when(histories).save(any(ContentHistory.class));
        assertThatThrownBy(() -> comments.updateComment(id, "실패한 수정", member.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(commentRepository.findById(id).orElseThrow().getContent()).isEqualTo("원래 내용");
    }

    @Test
    void restoreApiRequiresAdminAndCsrfAndReturnsTrashMetadata() throws Exception {
        adminPosts.deletePost(post.getId());
        em.flush();
        String path = "/api/v1/admin/blog/posts/" + post.getId() + "/restore";
        mvc.perform(post(path).with(user(MemberPrincipal.ofSocial(member))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/blog/posts?trash=true&categoryId=" + category.getId())
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].restoreUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].restorable").value(true));
        mvc.perform(post(path).with(user(MemberPrincipal.ofLocal(admin))).with(csrf()))
                .andExpect(status().isOk());
        assertThat(posts.findById(post.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void postDeletionDoesNotDependOnHistory() {
        doThrow(new IllegalStateException("history unavailable")).when(histories).save(any(ContentHistory.class));
        adminPosts.deletePost(post.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(posts.findById(post.getId()).orElseThrow().getDeletedAt()).isNotNull());
    }

    @Test
    void commentAdminCountsRelatedPostsAndCategoryShareExcludeTrash() {
        Comment root = commentRepository.save(new Comment(post, null, member, "댓글"));
        reports.save(new CommentReport(root, admin, CommentReportReason.values()[0], null));
        Post other = posts.save(new Post("비교 글", "요약", "본문", category, null, admin));
        other.publish(LocalDateTime.now());
        em.flush();
        long beforeReports = dashboard.countReportedComments();
        long beforePosts = dashboard.countPostsByStatus(PostStatus.PUBLISHED);
        adminPosts.deletePost(post.getId());
        em.flush();

        var result = adminComments.getComments(new AdminCommentSearchCondition(0, 20, null, post.getTitle(), null));
        assertThat(result.items()).isEmpty();
        assertThat(result.statusCounts()).isEqualTo(new AdminCommentCounts(0, 0, 0, 0));
        assertThat(dashboard.countReportedComments()).isEqualTo(beforeReports - 1);
        assertThat(dashboard.countPostsByStatus(PostStatus.PUBLISHED)).isEqualTo(beforePosts - 1);
        assertThat(dashboard.getCategoryShares().stream().filter(c -> c.categoryId().equals(category.getId()))
                .findFirst().orElseThrow().postCount()).isEqualTo(1);
        assertThat(posts.getRelatedPosts(other.getId(), category.getId())).isEmpty();
        var previous = posts.getAdjacentPost(other.getId()).previousPost();
        if (previous != null) assertThat(previous.id()).isNotEqualTo(post.getId());
        assertThat(commentRepository.getCommentPageByPostId(post.getId(), null, 20L, null).items()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void commentCreationWaitsForDeletionAndCannotWriteAfterItCommits() throws Exception {
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writing = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> deletion = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        adminPosts.deletePost(post.getId());
                        deleted.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }));
            assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> creation = executor.submit(() -> {
                writing.countDown();
                comments.createComment(post.getId(), new CommentCreateRequest("동시 댓글", null), member.getId());
            });
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> creation.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            deletion.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> creation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BlogException.class);
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertThat(commentRepository.findAll().stream()
                            .filter(c -> c.getPost().getId().equals(post.getId()))).isEmpty());
        } finally {
            release.countDown();
        }
    }
}
