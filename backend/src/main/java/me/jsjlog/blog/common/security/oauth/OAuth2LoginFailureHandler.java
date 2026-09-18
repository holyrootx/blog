package me.jsjlog.blog.common.security.oauth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/** 소셜 로그인 실패 사유를 제한된 결과 코드로 바꿔 프론트 콜백 화면으로 보낸다. */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String ACCOUNT_SUSPENDED = "account_suspended";

    private final OAuth2FrontendRedirectUriFactory redirectUriFactory;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        clearAuthenticationState(request);
        response.sendRedirect(redirectUriFactory.create(resultOf(exception)));
    }

    private void clearAuthenticationState(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.removeAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }

        SecurityContextHolder.clearContext();
    }

    private String resultOf(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException
                && ACCOUNT_SUSPENDED.equals(oauthException.getError().getErrorCode())) {
            return "account-suspended";
        }

        return "login-failed";
    }
}
