package me.jsjlog.blog.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import me.jsjlog.blog.admin.dto.AdminSessionResponse;
import me.jsjlog.blog.admin.dto.CsrfTokenResponse;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.AdminPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 세션 확인.
 *
 * 로그인과 로그아웃은 여기 없다. 둘 다 Spring Security 의 필터가 처리한다 —
 * 로그인은 {@code AdminLoginFilter}, 로그아웃은 {@code LogoutFilter} 다.
 * 컨트롤러로 만들면 세션 ID 재발급, CSRF 토큰 회전, SecurityContext 저장을
 * 우리가 순서대로 기억해야 한다.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    /**
     * 새로고침했을 때 세션이 아직 살아 있는지 확인한다. 프론트 라우터 가드가 이걸 본다.
     *
     * 세션이 없으면 여기까지 오지 않는다 — 인가 규칙에 걸려 401 이 먼저 나간다.
     */
    @GetMapping("/me")
    public ApiResponse<AdminSessionResponse> me(@AuthenticationPrincipal AdminPrincipal principal) {
        return ApiResponse.ok(AdminSessionResponse.from(principal));
    }

    /**
     * CSRF 토큰을 내준다. 로그인 전에도 불러야 하므로 인증 없이 열려 있다.
     *
     * 토큰은 요청 속성에 지연 생성 상태로 들어 있다. 여기서 getToken() 을 부르는 순간
     * 실제로 만들어지고 세션에 저장된다 — 아무도 읽지 않으면 만들지 않는 구조다.
     *
     * 로그인에 성공하면 토큰이 새로 발급되므로 화면은 그때 다시 받아야 한다.
     * 옛 토큰을 계속 쓰면 첫 저장이 403 으로 막힌다.
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        return ApiResponse.ok(CsrfTokenResponse.from(token));
    }
}
