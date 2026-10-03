package me.jsjlog.blog.post.service;

import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;

import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitemapServiceTest {

    @Mock
    private PostRepository postRepository;

    @Test
    void includesPublicPagesAndSelectedPostIds() throws Exception {
        when(postRepository.findPublicPostIdsForSitemap(eq(PostStatus.PUBLISHED), any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 7L));

        var xml = new SitemapService(postRepository).publicSitemapXml();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var locations = document.getElementsByTagNameNS(
                "http://www.sitemaps.org/schemas/sitemap/0.9", "loc");

        assertThat(locations.getLength()).isEqualTo(4);
        assertThat(locations.item(0).getTextContent()).isEqualTo("https://jsjlog.me/");
        assertThat(locations.item(1).getTextContent()).isEqualTo("https://jsjlog.me/posts");
        assertThat(locations.item(2).getTextContent()).isEqualTo("https://jsjlog.me/posts/1");
        assertThat(locations.item(3).getTextContent()).isEqualTo("https://jsjlog.me/posts/7");
        verify(postRepository).findPublicPostIdsForSitemap(eq(PostStatus.PUBLISHED), any(LocalDateTime.class));
    }
}
