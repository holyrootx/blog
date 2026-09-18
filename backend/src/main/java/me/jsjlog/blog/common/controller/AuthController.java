package me.jsjlog.blog.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.dto.CsrfTokenResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자와 회원이 함께 쓰는 인증 보조 API. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * 상태 변경 요청에 사용할 CSRF 토큰을 내준다.
     *
     * <p>로그인 전에도 필요하므로 인증 없이 열려 있다. 토큰은 요청 속성에 지연 생성 상태로
     * 들어 있으며, 여기서 읽는 순간 실제로 생성되어 세션에 저장된다. 로그인과 로그아웃으로
     * 세션 인증 상태가 바뀌면 프론트는 이 값을 다시 받아야 한다.</p>
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        return ApiResponse.ok(CsrfTokenResponse.from(token));
    }
}
