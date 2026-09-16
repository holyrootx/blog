package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 보안 필터가 직접 응답을 써야 할 때 쓴다.
 *
 * 로그인·로그아웃·인증 실패는 컨트롤러에 닿기 전에 끝나므로 GlobalExceptionHandler 도
 * @RestController 의 반환값 변환도 거치지 않는다. 그렇다고 형식을 따로 두면
 * 프론트가 "필터에서 끝난 응답"과 "컨트롤러에서 온 응답"을 다르게 읽어야 한다.
 *
 * ObjectMapper 는 Spring 이 만든 것을 그대로 쓴다. 새로 만들면 날짜 모듈이 빠져
 * timestamp 형식이 다른 응답과 어긋난다.
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public void writeError(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        write(response, errorCode.getHttpStatus(), ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    public void writeOk(HttpServletResponse response, Object data) throws IOException {
        write(response, HttpStatus.OK, ApiResponse.ok(data));
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
