package me.jsjlog.blog.post.service;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PostPageServiceTest {

    @TempDir
    Path directory;

    private final PostRepository repository = mock(PostRepository.class);
    private Path index;
    private PostPageService service;
    private Post post;

    @BeforeEach
    void setUp() throws IOException {
        index = directory.resolve("index.html");
        Files.writeString(index, "<html><head><!-- blog-meta:start --><title>기본 제목</title>"
                + "<!-- blog-meta:end --><script src=\"/assets/current.js\"></script></head>"
                + "<body><div id=\"app\"></div></body></html>");
        service = new PostPageService(repository, index.toString(), "https://blog.example");
        post = new Post("공개 글", "글 설명", "본문", new Category("테스트", 0L),
                "/images/cover.webp", null);
        ReflectionTestUtils.setField(post, "id", 1L);
        post.publish(LocalDateTime.now().minusDays(1));
        when(repository.findById(1L)).thenReturn(Optional.of(post));
    }

    @Test
    void firstHtmlContainsPostMetadataAndCurrentAssetsWithoutCountingAView() {
        var page = service.render("1");
        assertThat(page.found()).isTrue();
        assertThat(page.html()).contains("<title>공개 글 · 정성주의 기록</title>",
                "property=\"og:title\" content=\"공개 글\"",
                "property=\"og:description\" content=\"글 설명\"",
                "property=\"og:image\" content=\"https://blog.example/images/cover.webp\"",
                "rel=\"canonical\" href=\"https://blog.example/posts/1\"",
                "/assets/current.js", "<div id=\"app\"></div>");
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
        assertThat(post.getViews()).isZero();
    }

    @Test
    void privateScheduledDeletedAndMissingPostsDoNotDiscloseTheirMetadata() {
        post.unpublish();
        assertNotFound(service.render("1"));
        post.schedule(LocalDateTime.now().plusDays(1));
        assertNotFound(service.render("1"));
        post.publish(LocalDateTime.now().plusDays(1));
        assertNotFound(service.render("1"));
        post.publish(LocalDateTime.now().minusDays(1));
        post.delete(LocalDateTime.now());
        assertNotFound(service.render("1"));
        assertNotFound(service.render("2"));
    }

    @Test
    void invalidOrOverflowingIdsAreNotFoundWithoutQueryingTheDatabase() {
        for (String id : new String[]{"-1", "0", "01", "new", "9223372036854775808", "99999999999999999999"}) {
            assertNotFound(service.render(id));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void titleAndDescriptionCannotInjectHtmlAndUnsafeImageFallsBack() {
        post.update("</title><script>alert('x')</script>", "\" /><img src=x onerror=alert(1)>",
                "본문", post.getCategory(), "javascript:alert(1)");
        String html = service.render("1").html();
        assertThat(html).contains("&lt;/title&gt;&lt;script&gt;", "&quot; /&gt;&lt;img",
                "https://blog.example/images/blog-hero-workspace.webp")
                .doesNotContain("<script>alert", "<img src=x", "javascript:alert");
    }

    @Test
    void missingExcerptUsesTextWithoutFencedCodeOrImageMarkup() {
        post.update("공개 글", " ", "```java\nprivateKey();\n```\n![이미지](https://img.example/x)\n"
                + "## 설명\n**본문**과 [링크](https://example.com)", post.getCategory(), "");
        String html = service.render("1").html();
        assertThat(html).contains("content=\"설명 본문과 링크\"")
                .doesNotContain("privateKey", "img.example", "**본문**");
    }

    @Test
    void missingExcerptDropsEditorTagsTableLinesAndCalloutMarkers() {
        post.update("공개 글", "", "<details>\n<summary>접은 <u>제목</u></summary>\n\n"
                + "<span data-color=\"red-bg\">빨간</span> ~~옛~~ 글\n\n</details>\n\n"
                + "| 이름 | 값 |\n| --- | :---: |\n| a | 1 |\n\n:::tip\n팁\n:::\n\n- [x] 할 일", post.getCategory(), "");
        String html = service.render("1").html();
        assertThat(html).contains("content=\"접은 제목 빨간 옛 글 이름 값 a 1 팁 할 일\"")
                .doesNotContain("data-color", "&lt;", "---", ":::", "[x]");
    }

    @Test
    void metadataIsReadAgainAfterUnpublishingAndAfterFrontendDeployment() throws IOException {
        assertThat(service.render("1").found()).isTrue();
        Files.writeString(index, Files.readString(index).replace("current.js", "next.js"));
        assertThat(service.render("1").html()).contains("/assets/next.js").doesNotContain("/assets/current.js");
        post.unpublish();
        assertNotFound(service.render("1"));
    }

    @Test
    void missingFrontendBuildIsReportedAsUnavailable() throws IOException {
        Files.delete(index);
        assertThatThrownBy(() -> service.render("1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void publicOriginCannotContainCredentialsPathsOrQueryParameters() {
        for (String origin : new String[]{"javascript:alert(1)", "https://user:secret@example.com",
                "https://example.com/path", "https://example.com?host=other"}) {
            assertThatThrownBy(() -> new PostPageService(repository, index.toString(), origin))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void assertNotFound(PostPageService.Page page) {
        assertThat(page.found()).isFalse();
        assertThat(page.html()).contains("content=\"noindex,follow\"", "찾는 글이 없습니다")
                .doesNotContain("공개 글", "글 설명", "cover.webp", "rel=\"canonical\"");
    }
}
