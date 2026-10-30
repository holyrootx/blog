package me.jsjlog.blog.post.service;

import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.home.repository.BlogProfileRepository;
import tools.jackson.databind.ObjectMapper;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
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
	private final BlogProfileRepository profiles;
	private final ObjectMapper json;
	private final Path indexPath;
	private final URI origin;

	public PostPageService(PostRepository postRepository,
						   BlogProfileRepository profiles,
						   ObjectMapper json,
						   @Value("${blog.frontend.index-path:/var/www/blog/index.html}") String indexPath,
						   @Value("${blog.frontend.base-url:https://jsjlog.me}") String publicOrigin) {
		this.postRepository = postRepository;
		this.profiles = profiles;
		this.json = json;
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
			// JSON 문자열 안의 </script>도 HTML 파서에는 종료 태그다. 문자 자체를 이스케이프한다.
			String data = json.writeValueAsString(structuredData(post))
					.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
					.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
			meta.append("<script id=\"post-structured-data\" type=\"application/ld+json\">")
					.append(data).append("</script>\n");
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

	/** 최초 HTML과 앱 안에서 이동한 글에 같은 공개 정보를 제공한다. */
	public Map<String, Object> structuredData(Post post) {
		if (!post.isPubliclyVisible(LocalDateTime.now())) {
			return Map.of();
		}
		String url = origin.resolve("/posts/" + post.getId()).toString();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("@context", "https://schema.org");
		data.put("@type", "BlogPosting");
		data.put("@id", url + "#article");
		data.put("url", url);
		data.put("mainEntityOfPage", url);
		data.put("headline", post.getTitle());
		data.put("description", description(post));
		data.put("image", imageUrl(post.getThumbnailImageUrl()));
		data.put("inLanguage", "ko-KR");
		data.put("datePublished", articleTime(post.getPublishedAt()));
		if (post.getUpdatedAt() != null && !post.getUpdatedAt().isBefore(post.getPublishedAt())) {
			data.put("dateModified", articleTime(post.getUpdatedAt()));
		}
		// 공개 화면의 글쓴이도 이 프로필을 쓴다. 회원 로그인 정보와 이메일은 포함하지 않는다.
		profiles.findById(1L).filter(profile -> profile.getName() != null && !profile.getName().isBlank())
				.ifPresent(profile -> data.put("author", Map.of("@type", "Person", "name", profile.getName())));
		return data;
	}

	private String articleTime(LocalDateTime value) {
		return value.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private Post readablePost(String id) {
		if (!id.matches("[1-9][0-9]{0,18}")) {
			return null;
		}
		try {
			return postRepository.findById(Long.parseLong(id))
					.filter(post -> post.isPubliclyVisible(LocalDateTime.now()))
					.orElse(null);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String description(Post post) {
		String text = post.getExcerpt();
		if (text == null || text.isBlank()) {
			// 대표 설명이 없으면 코드·이미지를 제외한 본문에서 짧은 설명을 만든다.
			// 편집기가 쓰는 HTML 모양(<u> 밑줄, <span data-color> 색, <details> 토글)은 태그만 걷고 글은 남긴다.
			// 표는 칸 구분선(|)과 --- 줄을 걷는다. 콜아웃(:::tip)·할 일([ ]) 표기도 뺀다.
			text = post.getContent().replaceAll("(?s)```.*?(?:```|$)", " ")
					.replaceAll("!\\[[^\\]]*]\\([^\\n]*\\)", " ")
					.replaceAll("\\[([^\\]]+)]\\([^\\n)]*\\)", "$1")
					.replaceAll("</?(?:u|span|details|summary)(?:\\s[^>]*)?>", " ")
					.replaceAll("(?m)^\\s*\\|?(?:\\s*:?-+:?\\s*\\|)+(?:\\s*:?-+:?\\s*)?$", " ")
					.replaceAll("(?m)^\\s*:::\\s*\\w*\\s*$", " ")
					.replaceAll("(?m)^\\s*(?:#{1,6}\\s+|>\\s*|[-*+]\\s+(?:\\[[ xX]]\\s+)?|[0-9]+\\.\\s+)", "")
					.replace("|", " ")
					.replaceAll("[*_`~]", "");
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
