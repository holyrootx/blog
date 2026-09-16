package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 실패 응답.
 *
 * 왜 실패했는지는 내려보내지 않는다. 아이디가 없는 것과 비밀번호가 틀린 것을
 * 구분해 주면 어떤 아이디가 존재하는지 확인해 주는 꼴이 된다.
 *
 * 세션 만료(UNAUTHORIZED)와 코드를 나누는 이유는 화면이 할 일이 달라서다.
 * 세션 만료는 로그인 화면으로 보내야 하고, 이건 지금 보고 있는 폼에 사유를 적어야 한다.
 */
@Component
@RequiredArgsConstructor
public class AdminLoginFailureHandler implements AuthenticationFailureHandler {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        responseWriter.writeError(request, response, ErrorCode.ADMIN_LOGIN_FAILED);
    }
}
