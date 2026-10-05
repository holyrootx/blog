package me.jsjlog.blog.admin.service;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 글을 고쳐 본문에서 뺀 이미지는 커밋 뒤에 지운다.
 *
 * 커밋 뒤에 도는 일이라 롤백하는 테스트로는 확인이 안 된다. 그래서 이 테스트는 실제로
 * 커밋하고, 다른 테스트와 섞이지 않게 따로 만든 DB 를 쓴다.
 */
@SpringBootTest
class PostUpdateImageReleaseTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:post_image_release;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @MockitoBean
    private ImageStorage imageStorage;

    @Autowired
    private AdminPostService adminPosts;

    @Autowired
    private PostRepository posts;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UploadImageRepository images;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Category category;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        category = tx.execute(status -> categories.save(new Category("이미지 정리 " + UUID.randomUUID(), 950L)));
    }

    private UploadImage upload() {
        String key = "2026/10/" + UUID.randomUUID() + ".jpg";
        String url = "https://images.test/" + key;
        return tx.execute(status -> images.save(new UploadImage(key, url, "image.jpg", "image/jpeg", 100)));
    }

    private Post postWith(String content, String thumbnail) {
        return tx.execute(status -> posts.save(new Post("이미지 글", "요약", content, category, thumbnail, null)));
    }

    private void edit(Post post, String content, String thumbnail) {
        adminPosts.updatePost(post.getId(),
                new AdminPostRequest(null, "이미지 글", category.getId(), "요약", content, thumbnail));
    }

    @Test
    @DisplayName("본문에서 뺀 이미지는 커밋 뒤에 저장소와 기록에서 지운다")
    void deletesImageRemovedByEdit() {
        UploadImage image = upload();
        Post post = postWith("![사진](" + image.getUrl() + ")", null);

        edit(post, "사진을 뺀 본문", null);

        verify(imageStorage).delete(image.getStorageKey());
        assertThat(images.existsById(image.getId())).isFalse();
    }

    @Test
    @DisplayName("본문에서 빼도 대표 이미지로 남아 있으면 지우지 않는다")
    void keepsImageStillUsedAsThumbnail() {
        UploadImage image = upload();
        Post post = postWith("![사진](" + image.getUrl() + ")", image.getUrl());

        edit(post, "사진을 뺀 본문", image.getUrl());

        verify(imageStorage, never()).delete(anyString());
        assertThat(images.existsById(image.getId())).isTrue();
    }

    @Test
    @DisplayName("다른 글이 같은 이미지를 쓰고 있으면 지우지 않는다")
    void keepsImageSharedByAnotherPost() {
        UploadImage image = upload();
        Post post = postWith("![사진](" + image.getUrl() + ")", null);
        postWith("다른 글 ![사진](" + image.getUrl() + ")", null);

        edit(post, "사진을 뺀 본문", null);

        verify(imageStorage, never()).delete(anyString());
        assertThat(images.existsById(image.getId())).isTrue();
    }

    @Test
    @DisplayName("휴지통에 있는 글이 쓰는 이미지는 지우지 않는다 — 30일 안에 되살릴 수 있다")
    void keepsImageUsedByTrashedPost() {
        UploadImage image = upload();
        Post post = postWith("![사진](" + image.getUrl() + ")", null);
        Post trashed = postWith("휴지통 글 ![사진](" + image.getUrl() + ")", null);
        adminPosts.deletePost(trashed.getId());

        edit(post, "사진을 뺀 본문", null);

        verify(imageStorage, never()).delete(anyString());
        assertThat(images.existsById(image.getId())).isTrue();
    }

    @Test
    @DisplayName("글을 휴지통으로 보내도 이미지는 지우지 않는다")
    void trashingPostKeepsImages() {
        UploadImage image = upload();
        Post post = postWith("![사진](" + image.getUrl() + ")", null);

        adminPosts.deletePost(post.getId());

        verify(imageStorage, never()).delete(anyString());
        assertThat(images.existsById(image.getId())).isTrue();
        assertThat(posts.findById(post.getId()).orElseThrow().getDeletedAt())
                .isBefore(LocalDateTime.now().plusSeconds(1));
    }
}
