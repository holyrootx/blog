package me.jsjlog.blog.admin.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.ScheduledPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PostPublicationRulesTest {
    @Autowired AdminPostService adminPosts;
    @Autowired ScheduledPublisher publisher;
    @Autowired PostRepository posts;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;
    Category category;
    Post post;

    @BeforeEach
    void seed() {
        category = categories.save(new Category("예약 회귀", 880L));
        post = posts.save(new Post("예약 글", "요약", "본문", category, null, null));
        adminPosts.publishPost(post.getId(), LocalDateTime.now().plusDays(1));
    }

    @Test
    void scheduledEditRequiresContentAndPreservesSavedContentOnFailure() {
        assertThatThrownBy(() -> adminPosts.updatePost(post.getId(), request("요약", " ")))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_CONTENT_REQUIRED));
        assertThat(post.getContent()).isEqualTo("본문");
        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
    }

    @Test
    void scheduledEditRequiresExcerptAndCanBeRetriedWithValidInput() {
        assertThatThrownBy(() -> adminPosts.updatePost(post.getId(), request(" ", "수정 본문")))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_EXCERPT_REQUIRED));
        adminPosts.updatePost(post.getId(), request("수정 요약", "수정 본문"));
        assertThat(post.getExcerpt()).isEqualTo("수정 요약");
        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
    }

    @Test
    void cancelScheduleClearsTimeAndAllowsIncompleteDraft() {
        adminPosts.unpublishPost(post.getId());
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getPublishedAt()).isNull();
        adminPosts.updatePost(post.getId(), request("", ""));
        assertThat(post.getContent()).isEmpty();
        publisher.publishDuePosts();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void publishedUnpublishKeepsOriginalPublicationDateAndPrivateStatus() {
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(3);
        post.publish(publishedAt);
        adminPosts.unpublishPost(post.getId());
        assertThat(post.getStatus()).isEqualTo(PostStatus.PRIVATE);
        assertThat(post.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void schedulerSkipsInvalidLegacyReservationsAndStillPublishesValidDuePost() {
        post.schedule(LocalDateTime.now().minusMinutes(1));
        post.update(post.getTitle(), "요약", " ", category, null);
        Post noExcerpt = posts.save(new Post("요약 없는 예약", "", "본문", category, null, null));
        noExcerpt.schedule(LocalDateTime.now().minusMinutes(1));
        Post valid = posts.save(new Post("정상 예약", "요약", "본문", category, null, null));
        valid.schedule(LocalDateTime.now().minusMinutes(1));
        entityManager.flush();
        publisher.publishDuePosts();
        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(noExcerpt.getStatus()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(valid.getStatus()).isEqualTo(PostStatus.PUBLISHED);

        // 다시 돌아도 이미 공개한 글을 또 건드리지 않고, 잘못된 예약도 그대로 둔다
        LocalDateTime firstPublishedAt = valid.getPublishedAt();
        publisher.publishDuePosts();
        assertThat(valid.getPublishedAt()).isEqualTo(firstPublishedAt);
        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
    }

    @Test
    void cancelledPostCanBeScheduledAgainWithoutUsingStaleTime() {
        adminPosts.unpublishPost(post.getId());
        LocalDateTime rescheduledAt = LocalDateTime.now().plusDays(4);
        adminPosts.publishPost(post.getId(), rescheduledAt);
        assertThat(post.getStatus()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(post.getPublishedAt()).isEqualTo(rescheduledAt);
    }

    private AdminPostRequest request(String excerpt, String content) {
        return new AdminPostRequest(null, "예약 글", category.getId(), excerpt, content, null);
    }
}
