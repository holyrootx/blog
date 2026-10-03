package me.jsjlog.blog.admin.service;

import java.time.LocalDateTime;
import java.util.Set;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.repository.ImageUsageRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어떤 이미지를 지우고 어떤 이미지를 남기는지.
 *
 * 지운 파일은 복구할 수 없어서 "남겨야 하는 경우" 를 더 많이 확인한다.
 */
@SpringBootTest
@Transactional
class ImageCleanupServiceTest {

    private static final String BASE = "https://images.jsjlog.me/2026/09/";

    @Autowired
    private ImageCleanupService cleaner;

    @Autowired
    private ImageUsageRepository imageUsageRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void seed() {
        category = categoryRepository.save(new Category("정리테스트", 950L));
    }

    private UploadImage image(String name) {
        return imageUsageRepository.save(
                new UploadImage("2026/09/" + name, BASE + name, name, "image/webp", 1000L));
    }

    private Post post(String content, String thumbnail) {
        Post post = new Post("정리 테스트 글", "요약", content, category, thumbnail, null);
        post.publish(LocalDateTime.now());

        return postRepository.save(post);
    }

    @Test
    @DisplayName("본문에 있으면 참조 중이다")
    void keepsImageUsedInContent() {
        UploadImage used = image("a.webp");
        post("앞글 ![사진](" + used.getUrl() + ") 뒷글", null);

        assertThat(imageUsageRepository.findStillUsed(Set.of(used.getUrl())))
                .extracting(UploadImage::getId)
                .containsExactly(used.getId());
    }

    @Test
    @DisplayName("본문에서 뺐어도 대표 이미지면 참조 중이다")
    void keepsImageStillSetAsThumbnail() {
        UploadImage thumbnail = image("b.webp");

        // 본문에는 없고 대표 이미지로만 걸려 있다
        post("사진 없는 본문", thumbnail.getUrl());

        assertThat(imageUsageRepository.findStillUsed(Set.of(thumbnail.getUrl())))
                .as("본문만 보면 목록 썸네일이 깨진다")
                .hasSize(1);
    }

    @Test
    @DisplayName("다른 글이 같은 이미지를 쓰면 참조 중이다")
    void keepsImageSharedByAnotherPost() {
        UploadImage shared = image("c.webp");
        post("첫 글 ![사진](" + shared.getUrl() + ")", null);
        post("둘째 글 ![같은사진](" + shared.getUrl() + ")", null);

        assertThat(imageUsageRepository.findStillUsed(Set.of(shared.getUrl())))
                .as("블록 복붙으로 같은 URL 이 두 글에 들어간다")
                .hasSize(1);
    }

    @Test
    @DisplayName("아무 데도 없으면 미참조다")
    void reportsUnusedImage() {
        UploadImage orphan = image("d.webp");

        assertThat(imageUsageRepository.findStillUsed(Set.of(orphan.getUrl()))).isEmpty();
    }

    @Test
    @DisplayName("본문에서 마크다운과 img 태그 주소를 모두 찾는다")
    void collectsUrlsFromBothForms() {
        String content = """
                ![마크다운](%sa.webp)
                <img src="%sb.webp" alt="태그">
                """.formatted(BASE, BASE);

        assertThat(cleaner.collectUsedUrls(content, BASE + "c.webp"))
                .containsExactlyInAnyOrder(BASE + "a.webp", BASE + "b.webp", BASE + "c.webp");
    }

    @Test
    @DisplayName("이미지가 없으면 빈 결과다")
    void collectsNothingFromEmptyPost() {
        assertThat(cleaner.collectUsedUrls(null, null)).isEmpty();
        assertThat(cleaner.collectUsedUrls("사진 없는 글", "")).isEmpty();
    }

    @Test
    @DisplayName("업로드한 이미지가 아니면 건드리지 않는다")
    void leavesExternalUrlsAlone() {
        // 외부 URL 을 본문에 직접 쓴 경우
        cleaner.deleteUnused(Set.of("https://example.com/남의사진.png"));

        assertThat(imageUsageRepository.count()).isZero();
    }
}
