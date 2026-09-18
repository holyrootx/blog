package me.jsjlog.blog.common.security.oauth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.jsjlog.blog.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 소셜 인증 결과를 회원 로그인 완료 또는 가입 대기 상태로 나눈다.
 *
 * <p>OAuth2 콜백은 브라우저 최상위 이동으로 들어오므로 JSON 대신 프론트 콜백 화면으로
 * 돌려보낸다. 가입 대기 사용자는 아직 회원이 아니므로 인증 정보를 세션에서 제거하고,
 * 가입에 필요한 값만 {@link PendingOAuthSession} 으로 보관한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2FrontendRedirectUriFactory redirectUriFactory;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        Object principal = authentication.getPrincipal();

        if (principal instanceof MemberPrincipal) {
            clearPendingSession(request);
            redirect(response, "success");
            return;
        }

        if (principal instanceof PendingOAuthPrincipal pendingPrincipal) {
            keepPendingSessionOnly(request, pendingPrincipal);
            redirect(response, resultOf(pendingPrincipal.getLoginState()));
            return;
        }

        throw new IllegalStateException(
                "처리할 수 없는 OAuth2 principal 입니다: " + principal.getClass().getName()
        );
    }

    private void keepPendingSessionOnly(
            HttpServletRequest request,
            PendingOAuthPrincipal pendingPrincipal
    ) {
        HttpSession session = request.getSession();
        session.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME, PendingOAuthSession.from(pendingPrincipal));

        // 인증 필터가 성공 핸들러를 호출하기 전에 SecurityContext 를 세션에 저장한다.
        // Holder 만 비우면 다음 요청에서 대기 사용자가 다시 로그인 상태로 복원되므로
        // 세션에 저장된 컨텍스트도 함께 제거한다.
        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.clearContext();
    }

    private void clearPendingSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.removeAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
        }
    }

    private void redirect(HttpServletResponse response, String result) throws IOException {
        response.sendRedirect(redirectUriFactory.create(result));
    }

    private static String resultOf(OAuthLoginState loginState) {
        return switch (loginState) {
            case SIGNUP_REQUIRED -> "signup-required";
            case REACTIVATION_REQUIRED -> "reactivation-required";
        };
    }

}
