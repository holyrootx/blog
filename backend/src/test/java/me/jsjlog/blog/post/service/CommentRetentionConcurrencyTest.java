package me.jsjlog.blog.post.service;

import me.jsjlog.blog.admin.dto.AdminCommentVisibilityRequest;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 파기와 되살리기가 겹칠 때.
 *
 * 실제로 커밋하고 스레드를 나눠야 잠금이 걸린다. 그래서 롤백하는 테스트로 두지 않고
 * 따로 만든 DB 를 쓴다. H2 결과이므로 운영 MySQL 에서의 동작을 대신 보증하지는 않는다.
 */
@SpringBootTest
class CommentRetentionConcurrencyTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:comment_retention_concurrency;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @Autowired private CommentRetention retention;
    @Autowired private CommentRepository comments;
    @Autowired private AdminCommentService adminComments;
    @Autowired private CategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private MemberRepository members;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long commentId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        String key = UUID.randomUUID().toString();

        commentId = tx.execute(status -> {
            Category category = categories.save(new Category("동시 " + key, 970L));
            Post post = new Post("동시 글", "요약", "본문", category, null, null);
            post.publish(LocalDateTime.now().minusDays(1));
            posts.save(post);
            Member writer = members.save(Member.ofSocial(AuthProvider.NAVER, "w-" + key, "글쓴이", null, null));
            adminId = members.save(Member.ofLocalAdmin("a-" + key, "encoded-password", "운영자")).getId();

            Comment comment = comments.save(new Comment(post, null, writer, "지운 말"));
            comment.delete(LocalDateTime.now().minusMonths(7));
            return comment.getId();
        });
    }

    private Comment load() {
        return comments.findById(commentId).orElseThrow();
    }

    @Test
    @DisplayName("운영자가 되살리는 중에 정리가 돌면, 되살린 댓글을 파기하지 않는다")
    void restoreHoldingTheLockWins() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);

        CompletableFuture<Void> restoring = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            Comment comment = comments.findLockedById(commentId).orElseThrow();
            locked.countDown();
            sleep(800);
            comment.restore();
        }));

        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        CommentRetention.Result result = retention.purgeExpired(LocalDateTime.now());
        restoring.get(10, TimeUnit.SECONDS);

        Comment saved = load();
        assertThat(result.purgedComments()).isZero();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getContent()).isEqualTo("지운 말");
        assertThat(saved.isContentPurged()).isFalse();
    }

    @Test
    @DisplayName("파기한 뒤에 되살리려 하면 거절하고 원문은 비어 있는 그대로다")
    void restoreAfterPurgeIsRejected() {
        retention.purgeExpired(LocalDateTime.now());

        assertThatThrownBy(() -> adminComments.updateVisibility(
                commentId, new AdminCommentVisibilityRequest(false, "되돌림"), adminId))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.COMMENT_CONTENT_PURGED));
        assertThatThrownBy(() -> adminComments.updateVisibility(
                commentId, new AdminCommentVisibilityRequest(true, "가림"), adminId))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.COMMENT_CONTENT_PURGED));

        Comment saved = load();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getContent()).isEmpty();
    }

    @Test
    @DisplayName("정리가 두 번 겹쳐 돌아도 한 번만 파기한다")
    void concurrentPurgesPurgeOnce() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        // DB에 저장되는 마이크로초 정밀도에 맞춰 비교한다.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        CompletableFuture<CommentRetention.Result> first = CompletableFuture.supplyAsync(() -> {
            await(start);
            return retention.purgeExpired(now);
        });
        CompletableFuture<CommentRetention.Result> second = CompletableFuture.supplyAsync(() -> {
            await(start);
            return retention.purgeExpired(now.plusSeconds(1));
        });

        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        // 늦게 온 쪽의 UPDATE 는 조건에서 걸러져 파기 시각을 덮어쓰지 않는다
        Comment saved = load();
        assertThat(saved.getContent()).isEmpty();
        assertThat(saved.getContentPurgedAt()).isIn(now, now.plusSeconds(1));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
