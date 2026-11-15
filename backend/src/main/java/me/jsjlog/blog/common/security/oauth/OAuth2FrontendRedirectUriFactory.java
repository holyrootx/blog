package me.jsjlog.blog.common.security.oauth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2FrontendRedirectUriFactory {

	private static final String CALLBACK_PATH = "/oauth/callback";

	private final String frontendBaseUrl;

	public OAuth2FrontendRedirectUriFactory(
			@Value("${blog.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
	) {
		this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
	}

	public String create(String result) {
		return UriComponentsBuilder
				.fromUriString(frontendBaseUrl)
				.path(CALLBACK_PATH)
				.queryParam("result", result)
				// 새 no-store 응답을 받기 전에 과거 콜백 HTML 캐시가 사용되는 것을 막는다.
				.queryParam("_cb", UUID.randomUUID().toString())
				.build()
				.encode()
				.toUriString();
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
