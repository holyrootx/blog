package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.common.config.QueryDSLConfig;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QueryDSLConfig.class)
class SitemapRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void selectsOnlyPublishedPostsWhosePublishTimeHasPassed() {
        var now = LocalDateTime.now();
        var category = categoryRepository.save(new Category("사이트맵", 1L));

        var published = postRepository.save(post("공개", category, now.minusDays(1)));
        postRepository.save(post("미래 발행", category, now.plusDays(1)));
        postRepository.save(new Post("초안", "", "본문", category, null, null));
        var privatePost = post("내린 글", category, now.minusDays(2));
        privatePost.unpublish();
        postRepository.save(privatePost);
        var scheduled = new Post("예약", "", "본문", category, null, null);
        scheduled.schedule(now.minusHours(1));
        postRepository.save(scheduled);

        assertThat(postRepository.findPublicPostIdsForSitemap(PostStatus.PUBLISHED, now))
                .containsExactly(published.getId());
    }

    private Post post(String title, Category category, LocalDateTime publishedAt) {
        var post = new Post(title, "", "본문", category, null, null);
        post.publish(publishedAt);
        return post;
    }
}
