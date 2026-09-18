package me.jsjlog.blog.common.security.dto;

import org.springframework.security.web.csrf.CsrfToken;

/** 상태 변경 요청에 필요한 CSRF 헤더 이름과 토큰 값. */
public record CsrfTokenResponse(String headerName, String token) {

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
