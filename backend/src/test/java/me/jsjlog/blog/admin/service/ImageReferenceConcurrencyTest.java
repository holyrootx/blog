package me.jsjlog.blog.admin.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.home.domain.BlogProfile;
import me.jsjlog.blog.home.domain.HomePageHero;
import me.jsjlog.blog.home.dto.UpdateBlogProfileRequest;
import me.jsjlog.blog.home.dto.UpdateHomePageHeroRequest;
import me.jsjlog.blog.home.repository.BlogProfileRepository;
import me.jsjlog.blog.home.repository.HomePageHeroRepository;
import me.jsjlog.blog.home.service.BlogProfileService;
import me.jsjlog.blog.home.service.HomePageHeroService;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 실제 DB 트랜잭션으로 순서를 고정하고 저장소 삭제만 대체한다. 운영 DB URL은 받지 않는다. */
@SpringBootTest
class ImageReferenceConcurrencyTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("BLOG_IMAGE_TEST_JDBC_URL");
        if (url != null && !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13339/blog_image_test(?:\\?.*)?"))
            throw new IllegalArgumentException("이미지 검증 전용 로컬 DB만 사용할 수 있습니다.");
        properties.add("spring.datasource.url", () -> url == null
                ? "jdbc:h2:mem:image_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : url);
        properties.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> url == null ? "sa" : "root");
        properties.add("spring.datasource.password", () -> "");
    }
    @Autowired AdminPostService adminPosts;
    @Autowired AdminImageService adminImages;
    @Autowired ImageCleanupService cleanup;
    @Autowired PostRepository posts;
    @Autowired CategoryRepository categories;
    @Autowired UploadImageRepository images;
    @Autowired PlatformTransactionManager transactions;
    @Autowired HomePageHeroRepository heroes;
    @Autowired HomePageHeroService heroService;
    @Autowired BlogProfileRepository profiles;
    @Autowired BlogProfileService profileService;
    @MockitoBean ImageStorage storage;
    TransactionTemplate tx;
    UploadImage image;
    Post post;

    @BeforeEach void fixtures() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            Category category = categories.save(new Category("image-race-" + UUID.randomUUID(), 999L));
            post = posts.save(new Post("이미지 경합", "요약", "기존 본문", category, null, null));
            String key = "2026/10/" + UUID.randomUUID() + ".png";
            image = images.save(new UploadImage(key, "https://images.test/" + key.replace(".png", "-thumbnail-v1.0.webp"), "image.png", "image/png", 100));
        });
    }
    void saveReference(String url) {
        adminPosts.updatePost(post.getId(), new AdminPostRequest(null, post.getTitle(), post.getCategory().getId(),
                "요약", "본문 ![사진](" + url + ")", url));
    }
    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    void deletionWins(Consumer<UploadImage> remove) throws Exception {
        CountDownLatch deleting = new CountDownLatch(1), finishDelete = new CountDownLatch(1), writerStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (image.getStorageKey().equals(invocation.getArgument(0))) { deleting.countDown(); await(finishDelete); }
            return null;
        }).when(storage).delete(anyString());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> deletingTask = pool.submit(() -> remove.accept(image));
            try {
                await(deleting);
                Future<Throwable> savingTask = pool.submit(() -> {
                    writerStarted.countDown();
                    try { saveReference(image.getUrl()); return null; }
                    catch (Throwable error) { return error; }
                });
                await(writerStarted);
                finishDelete.countDown();
                deletingTask.get(10, TimeUnit.SECONDS);
                Throwable error = savingTask.get(10, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(BlogException.class);
                assertThat(((BlogException) error).getErrorCode().name()).isEqualTo("IMAGE_REFERENCE_UNAVAILABLE");
                assertThat(images.existsById(image.getId())).isFalse();
                assertThat(posts.findById(post.getId()).orElseThrow().getContent()).isEqualTo("기존 본문");
            } finally { finishDelete.countDown(); }
        }
    }
    @Test void manualDeletionPreventsNewReference() throws Exception { deletionWins(i -> adminImages.delete(i.getId())); }
    @Test void batchCleanupPreventsNewReference() throws Exception { deletionWins(i -> cleanup.cleanup(List.of(i.getId()))); }
    @Test void automaticCleanupPreventsNewReference() throws Exception { deletionWins(i -> cleanup.onPostImagesReleased(new ImageCleanupService.PostImagesReleasedEvent(Set.of(i.getUrl())))); }

    void writerWins(Consumer<UploadImage> remove, Runnable writer, boolean rollback) throws Exception {
        CountDownLatch saved = new CountDownLatch(1), finishSave = new CountDownLatch(1), cleanupStarted = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> savingTask = pool.submit(() -> tx.executeWithoutResult(status -> {
                writer.run(); saved.countDown(); await(finishSave);
                if (rollback) status.setRollbackOnly();
            }));
            try {
                await(saved);
                Future<?> deletingTask = pool.submit(() -> { cleanupStarted.countDown(); remove.accept(image); });
                await(cleanupStarted);
                // 삭제가 잠금에서 기다리는 동안 저장을 커밋한다. 결과 검증은 시간값에 의존하지 않는다.
                try { deletingTask.get(250, TimeUnit.MILLISECONDS); } catch (TimeoutException expected) { }
                finishSave.countDown(); savingTask.get(10, TimeUnit.SECONDS);
                try { deletingTask.get(10, TimeUnit.SECONDS); }
                catch (ExecutionException error) {
                    assertThat(error.getCause()).isInstanceOf(BlogException.class);
                    assertThat(((BlogException) error.getCause()).getErrorCode().name()).isEqualTo("IMAGE_IN_USE");
                }
                assertThat(images.existsById(image.getId())).isEqualTo(!rollback);
                if (!rollback) verify(storage, never()).delete(anyString());
                else verify(storage).delete(image.getStorageKey());
            } finally { finishSave.countDown(); }
        }
    }
    @Test void committedReferenceSurvivesManualDelete() throws Exception { writerWins(i -> adminImages.delete(i.getId()), () -> saveReference(image.getUrl()), false); }
    @Test void committedReferenceSurvivesBatchCleanup() throws Exception { writerWins(i -> cleanup.cleanup(List.of(i.getId())), () -> saveReference(image.getUrl()), false); }
    @Test void committedReferenceSurvivesAutomaticCleanup() throws Exception { writerWins(i -> cleanup.onPostImagesReleased(new ImageCleanupService.PostImagesReleasedEvent(Set.of(i.getUrl()))), () -> saveReference(image.getUrl()), false); }
    @Test void rolledBackReferenceAllowsCleanup() throws Exception { writerWins(i -> cleanup.deleteUnused(Set.of(i.getUrl())), () -> saveReference(image.getUrl()), true); }
    @Test void heroReferenceAlsoSurvivesCleanup() throws Exception {
        HomePageHero hero = tx.execute(status -> heroes.save(new HomePageHero("부제", "제목", "소개", null)));
        writerWins(i -> cleanup.deleteUnused(Set.of(i.getUrl())), () -> heroService.updateHomePageHero(
                new UpdateHomePageHeroRequest(hero.getId(), "부제", "제목", "소개", image.getUrl())), false);
    }
    @Test void profileReferenceAlsoSurvivesCleanup() throws Exception {
        BlogProfile profile = tx.execute(status -> profiles.save(new BlogProfile("이름", "소개", "개발자", null, null, null, null)));
        writerWins(i -> cleanup.deleteUnused(Set.of(i.getUrl())), () -> profileService.updateBlogProfile(
                new UpdateBlogProfileRequest(profile.getId(), "이름", "소개", "개발자", image.getUrl(), null, null, null)), false);
    }
    @Test void thumbnailDeletionFailureKeepsRecordForRetry() {
        doThrow(new IllegalStateException("storage unavailable")).when(storage).delete(image.getUrl().replace("https://images.test/", ""));
        assertThat(cleanup.cleanup(List.of(image.getId())).failedCount()).isEqualTo(1);
        assertThat(images.existsById(image.getId())).isTrue();
    }
    @Test void externalImagesAndStaticAssetsRemainAllowed() {
        saveReference("https://external.example/image.png"); saveReference("/images/default.webp");
        assertThat(posts.findById(post.getId()).orElseThrow().getThumbnailImageUrl()).isEqualTo("/images/default.webp");
    }
}
