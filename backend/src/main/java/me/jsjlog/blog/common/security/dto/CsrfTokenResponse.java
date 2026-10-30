package me.jsjlog.blog.common.security.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(String headerName, String token) {

	public static CsrfTokenResponse from(CsrfToken csrfToken) {
		return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
	}
}
