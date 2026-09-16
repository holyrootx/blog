package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인은 했지만 권한이 모자란 요청에 403 을 준다.
 *
 * 401 과 나누는 이유는 화면이 할 일이 다르기 때문이다.
 * 401 은 로그인 화면으로 보내면 되고, 403 은 다시 로그인해도 소용이 없다.
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        responseWriter.writeError(request, response, ErrorCode.FORBIDDEN);
    }
}
