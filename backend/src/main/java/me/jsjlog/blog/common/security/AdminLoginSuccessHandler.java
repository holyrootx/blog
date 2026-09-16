package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminSessionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 성공 응답.
 *
 * 기본 핸들러는 302 로 페이지를 옮긴다. SPA 라서 그러면 프론트가 할 수 있는 게 없다.
 * 나머지 API 와 같은 JSON 으로 답한다.
 */
@Component
@RequiredArgsConstructor
public class AdminLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        responseWriter.writeOk(response, AdminSessionResponse.from((AdminPrincipal) authentication.getPrincipal()));
    }
}
