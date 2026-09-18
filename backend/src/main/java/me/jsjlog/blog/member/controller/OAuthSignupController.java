package me.jsjlog.blog.member.controller;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
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
        Member member = signupService.signup(pendingSession, signupRequest.nickname());
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);

        completeLogin(request, response, pendingSession, principal);

        return ApiResponse.ok("회원 가입이 완료되었습니다.", MemberSessionResponse.from(principal));
    }

    @PostMapping("/reactivate")
    public ApiResponse<MemberSessionResponse> reactivate(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PendingOAuthSession pendingSession = requiredPendingSession(request);
        Member member = signupService.reactivate(pendingSession);

        return loginCompletedMember(
                request,
                response,
                pendingSession,
                member,
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
        Member member = signupService.rejoin(pendingSession, signupRequest.nickname());

        return loginCompletedMember(
                request,
                response,
                pendingSession,
                member,
                "새 계정으로 가입되었습니다."
        );
    }

    private ApiResponse<MemberSessionResponse> loginCompletedMember(
            HttpServletRequest request,
            HttpServletResponse response,
            PendingOAuthSession pendingSession,
            Member member,
            String message
    ) {
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);
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

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession();
        session.removeAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
        securityContextRepository.saveContext(securityContext, request, response);
    }
}
