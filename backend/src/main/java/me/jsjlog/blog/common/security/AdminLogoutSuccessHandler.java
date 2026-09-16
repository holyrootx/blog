package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그아웃 성공 응답.
 *
 * 세션 무효화와 컨텍스트 비우기는 Spring Security 의 LogoutFilter 가 한다.
 * 여기서는 응답 형식만 맞춘다.
 */
@Component
@RequiredArgsConstructor
public class AdminLogoutSuccessHandler implements LogoutSuccessHandler {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        responseWriter.writeOk(response, null);
    }
}
