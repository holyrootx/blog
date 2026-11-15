package me.jsjlog.blog.common.security.oauth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2FrontendRedirectUriFactoryTest {

	@ParameterizedTest
	@ValueSource(strings = {"success", "signup-required", "reactivation-required", "login-failed", "account-suspended"})
	void eachCallbackBypassesPreviouslyCachedHtmlWithoutChangingTheResult(String result) {
		var factory = new OAuth2FrontendRedirectUriFactory("https://jsjlog.me/");
		var first = UriComponentsBuilder.fromUriString(factory.create(result)).build();
		var next = UriComponentsBuilder.fromUriString(factory.create(result)).build();

		assertThat(first.getScheme()).isEqualTo("https");
		assertThat(first.getHost()).isEqualTo("jsjlog.me");
		assertThat(first.getPath()).isEqualTo("/oauth/callback");
		assertThat(first.getQueryParams().getFirst("result")).isEqualTo(result);
		assertThat(next.getQueryParams().getFirst("result")).isEqualTo(result);
		assertThat(first.getQueryParams().getFirst("_cb")).isNotBlank();
		assertThat(next.getQueryParams().getFirst("_cb"))
				.isNotBlank().isNotEqualTo(first.getQueryParams().getFirst("_cb"));
	}
}
