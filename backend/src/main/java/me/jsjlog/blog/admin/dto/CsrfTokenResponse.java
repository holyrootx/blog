package me.jsjlog.blog.admin.dto;

import org.springframework.security.web.csrf.CsrfToken;

/**
 * CSRF 토큰. 화면이 상태를 바꾸는 요청에 실어 보낼 값이다.
 *
 * 헤더 이름도 같이 준다. 프론트에 "X-CSRF-TOKEN" 을 적어 두면
 * 서버 설정을 바꿨을 때 양쪽이 조용히 어긋난다.
 */
public record CsrfTokenResponse(String headerName, String token) {

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
