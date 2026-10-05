package me.jsjlog.blog.post.service;

import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 링크를 공유하는 서비스도 글 정보를 읽을 수 있도록 첫 HTML에 메타 정보를 넣는다. */
@Service
public class PostPageService {

    private static final String SITE_NAME = "정성주의 기록";
    private static final String DEFAULT_DESCRIPTION = "코드와 장비, 일과 생활 사이에서 배운 것을 적어 둡니다.";
    private static final String DEFAULT_IMAGE = "/images/blog-hero-workspace.webp";
    private static final Pattern META_BLOCK = Pattern.compile(
            "<!-- blog-meta:start -->.*?<!-- blog-meta:end -->", Pattern.DOTALL);

    private final PostRepository postRepository;
    private final Path indexPath;
    private final URI origin;

    public PostPageService(PostRepository postRepository,
                           @Value("${blog.frontend.index-path:/var/www/blog/index.html}") String indexPath,
                           @Value("${blog.frontend.base-url:https://jsjlog.me}") String publicOrigin) {
        this.postRepository = postRepository;
        this.indexPath = Path.of(indexPath);
        this.origin = URI.create(publicOrigin);
        if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                || origin.getHost() == null || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("blog.frontend.base-url에는 공개 사이트의 origin만 지정합니다.");
        }
    }

    @Transactional(readOnly = true)
    public Page render(String id) {
        Post post = readablePost(id);
        boolean found = post != null;
        String title = found ? post.getTitle() : "찾는 글이 없습니다";
        String description = found ? description(post) : DEFAULT_DESCRIPTION;
        String image = imageUrl(found ? post.getThumbnailImageUrl() : null);
        String url = found ? origin.resolve("/posts/" + post.getId()).toString() : null;

        StringBuilder meta = new StringBuilder("<title>")
                .append(escape(title + " · " + SITE_NAME)).append("</title>\n");
        tag(meta, "name", "description", description);
        tag(meta, "name", "robots", found ? "index,follow" : "noindex,follow");
        tag(meta, "property", "og:type", found ? "article" : "website");
        tag(meta, "property", "og:site_name", SITE_NAME);
        tag(meta, "property", "og:title", title);
        tag(meta, "property", "og:description", description);
        tag(meta, "property", "og:image", image);
        tag(meta, "name", "twitter:card", "summary_large_image");
        tag(meta, "name", "twitter:title", title);
        tag(meta, "name", "twitter:description", description);
        tag(meta, "name", "twitter:image", image);
        if (found) {
            tag(meta, "property", "og:url", url);
            meta.append("<link rel=\"canonical\" href=\"").append(escape(url)).append("\" />\n");
        }

        try {
            // 배포 때 바뀐 파일을 읽는다. 예전 JS 경로가 프로세스 안에 캐시되지 않게 한다.
            String index = Files.readString(indexPath, StandardCharsets.UTF_8);
            Matcher block = META_BLOCK.matcher(index);
            if (!block.find()) {
                throw new IOException("Frontend index is missing the blog metadata block");
            }
            return new Page(found, block.replaceFirst(Matcher.quoteReplacement(meta.toString())));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "공개 페이지를 준비하지 못했습니다.", exception);
        }
    }

    private Post readablePost(String id) {
        if (!id.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return postRepository.findById(Long.parseLong(id))
                    .filter(Post::isPublished)
                    .filter(post -> post.getPublishedAt() != null
                            && !post.getPublishedAt().isAfter(LocalDateTime.now()))
                    .orElse(null);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String description(Post post) {
        String text = post.getExcerpt();
        if (text == null || text.isBlank()) {
            // 대표 설명이 없으면 코드·이미지를 제외한 본문에서 짧은 설명을 만든다.
            text = post.getContent().replaceAll("(?s)```.*?(?:```|$)", " ")
                    .replaceAll("!\\[[^\\]]*]\\([^\\n]*\\)", " ")
                    .replaceAll("\\[([^\\]]+)]\\([^\\n)]*\\)", "$1")
                    .replaceAll("(?m)^\\s*(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|[0-9]+\\.\\s+)", "")
                    .replaceAll("[*_`]", "");
        }
        text = text.replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) {
            return DEFAULT_DESCRIPTION;
        }
        return text.codePointCount(0, text.length()) > 160
                ? text.substring(0, text.offsetByCodePoints(0, 160)).stripTrailing() + "…" : text;
    }

    private String imageUrl(String value) {
        if (value != null && !value.isBlank()) {
            try {
                URI candidate = URI.create(value.strip());
                if (candidate.getUserInfo() == null && candidate.getHost() != null
                        && ("https".equals(candidate.getScheme()) || "http".equals(candidate.getScheme()))) {
                    return candidate.toString();
                }
                if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) {
                    return origin.resolve(candidate).toString();
                }
            } catch (IllegalArgumentException ignored) {
                // 잘못된 이미지 주소 때문에 글 전체를 열지 못하는 일은 피한다.
            }
        }
        return origin.resolve(DEFAULT_IMAGE).toString();
    }

    private static void tag(StringBuilder target, String key, String name, String value) {
        target.append("<meta ").append(key).append("=\"").append(name)
                .append("\" content=\"").append(escape(value)).append("\" />\n");
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    public record Page(boolean found, String html) { }
}
