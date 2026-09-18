package me.jsjlog.blog.common.security.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 소셜 로그인 결과를 받을 프론트 콜백 주소를 만든다. */
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
                .build()
                .encode()
                .toUriString();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
