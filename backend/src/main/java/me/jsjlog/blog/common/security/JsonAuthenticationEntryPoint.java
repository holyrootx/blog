package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.response.ErrorResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인하지 않은 요청에 401 을 준다.
 *
 * 이게 없으면 Spring Security 는 기본값인 Http403ForbiddenEntryPoint 를 써서
 * 본문 없는 403 을 보낸다. 그러면 프론트는 "로그인이 안 된 것"과
 * "로그인은 했는데 권한이 없는 것"을 구분하지 못한다.
 *
 * @see JsonAccessDeniedHandler 로그인은 했지만 권한이 없는 경우
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        // 실패 사유(아이디 없음·비밀번호 틀림)는 내려보내지 않는다.
        // ErrorResponse 의 고정 문구만 나간다
        responseWriter.writeError(request, response, ErrorCode.UNAUTHORIZED);
    }
}
