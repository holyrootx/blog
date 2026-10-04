package me.jsjlog.blog.member.controller;

import java.util.Locale;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.common.security.MemberSessionManager;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.dto.MemberSessionResponse;
import me.jsjlog.blog.member.dto.OAuthPendingResponse;
import me.jsjlog.blog.member.dto.OAuthSignupRequest;
import me.jsjlog.blog.member.service.OAuthSignupService;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthSignupController {

    private final OAuthSignupService signupService;
    private final MemberSessionManager memberSessions;
    // 가입 대기 상태에서 정식 인증으로 넘어가는 경계도 필터 로그인과 같은 보호를 적용한다.
    private final SessionAuthenticationStrategy completionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository())));

    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @GetMapping("/pending")
    public ApiResponse<OAuthPendingResponse> pending(HttpServletRequest request) {
        return ApiResponse.ok(OAuthPendingResponse.from(requiredPendingSession(request)));
    }

    @PostMapping("/signup")
    public ApiResponse<MemberSessionResponse> signup(
            @Valid @RequestBody OAuthSignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PendingOAuthSession pendingSession = requiredPendingSession(request);
        long authenticationStartedAtNanos = System.nanoTime();
        Member member = signupService.signup(pendingSession, signupRequest.nickname());
        MemberPrincipal principal = MemberPrincipal.ofSocial(member, authenticationStartedAtNanos);

        completeLogin(request, response, pendingSession, principal);

        return ApiResponse.ok("회원 가입이 완료되었습니다.", MemberSessionResponse.from(principal));
    }

    @PostMapping("/reactivate")
    public ApiResponse<MemberSessionResponse> reactivate(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PendingOAuthSession pendingSession = requiredPendingSession(request);
        long authenticationStartedAtNanos = System.nanoTime();
        Member member = signupService.reactivate(pendingSession);

        return loginCompletedMember(
                request,
                response,
                pendingSession,
                member,
                authenticationStartedAtNanos,
                "기존 계정이 복구되었습니다."
        );
    }

    @PostMapping("/rejoin")
    public ApiResponse<MemberSessionResponse> rejoin(
            @Valid @RequestBody OAuthSignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PendingOAuthSession pendingSession = requiredPendingSession(request);
        long authenticationStartedAtNanos = System.nanoTime();
        Member member = signupService.rejoin(pendingSession, signupRequest.nickname());

        return loginCompletedMember(
                request,
                response,
                pendingSession,
                member,
                authenticationStartedAtNanos,
                "새 계정으로 가입되었습니다."
        );
    }

    private ApiResponse<MemberSessionResponse> loginCompletedMember(
            HttpServletRequest request,
            HttpServletResponse response,
            PendingOAuthSession pendingSession,
            Member member,
            long authenticationStartedAtNanos,
            String message
    ) {
        MemberPrincipal principal = MemberPrincipal.ofSocial(member, authenticationStartedAtNanos);
        completeLogin(request, response, pendingSession, principal);

        return ApiResponse.ok(message, MemberSessionResponse.from(principal));
    }

    private PendingOAuthSession requiredPendingSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
        }

        Object value = session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);

        if (!(value instanceof PendingOAuthSession pendingSession)) {
            throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
        }

        return pendingSession;
    }

    private void completeLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            PendingOAuthSession pendingSession,
            MemberPrincipal principal
    ) {
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                pendingSession.provider().name().toLowerCase(Locale.ROOT)
        );

        try {
            completionStrategy.onAuthentication(authentication, request, response);
            memberSessions.onAuthentication(request, principal);
        } catch (SessionAuthenticationException changedAccount) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession();
        session.removeAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
        securityContextRepository.saveContext(securityContext, request, response);
    }
}
