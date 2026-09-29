package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.security.authentication.LockedException;
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
    private final LoginAttemptGuard attemptGuard;
    private final ClientIpResolver clientIpResolver;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String clientIp = clientIpResolver.resolve(request);

        // 막혀서 돌아온 것이라면 세지 않는다. 세면 막힌 동안 계속 두드리는 것만으로
        // 차단 시간이 끝없이 늘어난다
        if (exception instanceof LockedException) {
            responseWriter.writeError(request, response, ErrorCode.ADMIN_LOGIN_BLOCKED);
            return;
        }

        attemptGuard.recordFailure(clientIp);

        // 몇 번 남았는지는 알려 주지 않는다. 알려 주면 차단 직전에 멈췄다가
        // 다시 시작하는 식으로 피해 갈 수 있다
        responseWriter.writeError(request, response, ErrorCode.ADMIN_LOGIN_FAILED);
    }
}
