package me.jsjlog.blog.member.controller;

import java.util.Locale;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
import org.springframework.web.util.WebUtils;

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

	private final HttpSessionSecurityContextRepository securityContextRepository = completionRepository();

	@GetMapping("/pending")
	public ApiResponse<OAuthPendingResponse> pending(HttpServletRequest request) {
		PendingOAuthSession authenticated = requiredPendingSession(request);
		PendingOAuthSession current = signupService.refreshPending(authenticated);
		// 조회는 소셜 인증 결과를 다시 쓰지 않는다. 기다리는 사이 새 로그인·가입·로그아웃이
		// 끝났다면 이전 인증으로 계산한 선택지도 반환하지 않는다.
		if (requiredPendingSession(request) != authenticated) {
			throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
		}
		return ApiResponse.ok(OAuthPendingResponse.from(current));
	}

	@PostMapping("/signup")
	public ApiResponse<MemberSessionResponse> signup(
			@Valid @RequestBody OAuthSignupRequest signupRequest,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		PendingLogin login = requiredPendingLogin(request);
		PendingOAuthSession pendingSession = login.pending();
		long authenticationStartedAtNanos = System.nanoTime();
		Member member = signupService.signup(pendingSession, signupRequest.nickname());
		MemberPrincipal principal = MemberPrincipal.ofSocial(member, authenticationStartedAtNanos);

		completeLogin(request, response, login, principal);

		return ApiResponse.ok("회원 가입이 완료되었습니다.", MemberSessionResponse.from(principal));
	}

	@PostMapping("/reactivate")
	public ApiResponse<MemberSessionResponse> reactivate(
			HttpServletRequest request,
			HttpServletResponse response
	) {
		PendingLogin login = requiredPendingLogin(request);
		PendingOAuthSession pendingSession = login.pending();
		long authenticationStartedAtNanos = System.nanoTime();
		Member member = signupService.reactivate(pendingSession);

		return loginCompletedMember(
				request,
				response,
				login,
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
		PendingLogin login = requiredPendingLogin(request);
		PendingOAuthSession pendingSession = login.pending();
		long authenticationStartedAtNanos = System.nanoTime();
		Member member = signupService.rejoin(pendingSession, signupRequest.nickname());

		return loginCompletedMember(
				request,
				response,
				login,
				member,
				authenticationStartedAtNanos,
				"새 계정으로 가입되었습니다."
		);
	}

	private ApiResponse<MemberSessionResponse> loginCompletedMember(
			HttpServletRequest request,
			HttpServletResponse response,
			PendingLogin login,
			Member member,
			long authenticationStartedAtNanos,
			String message
	) {
		MemberPrincipal principal = MemberPrincipal.ofSocial(member, authenticationStartedAtNanos);
		completeLogin(request, response, login, principal);

		return ApiResponse.ok(message, MemberSessionResponse.from(principal));
	}

	private PendingOAuthSession requiredPendingSession(HttpServletRequest request) {
		return requiredPendingLogin(request).pending();
	}

	private PendingLogin requiredPendingLogin(HttpServletRequest request) {
		try {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					if (session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME) instanceof PendingOAuthSession pending) {
						return new PendingLogin(session, session.getId(), mutex, pending);
					}
				}
			}
		} catch (IllegalStateException ignored) {
			// 세션을 읽는 동안 다른 요청에서 로그아웃한 경우도 만료로 처리한다.
		}
		throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
	}

	private void completeLogin(
			HttpServletRequest request,
			HttpServletResponse response,
			PendingLogin login,
			MemberPrincipal principal
	) {
		synchronized (login.mutex()) {
			try {
				HttpSession current = request.getSession(false);
				if (current == null || !login.sessionId().equals(current.getId())
						|| current.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME) != login.pending()) {
					throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
				}
				completeCurrentLogin(existingSessionOnly(request, login.session()), response, login.pending(), principal);
			} catch (IllegalStateException expired) {
				SecurityContextHolder.clearContext();
				throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
			}
		}
	}

	private void completeCurrentLogin(HttpServletRequest request, HttpServletResponse response,
			PendingOAuthSession pendingSession, MemberPrincipal principal) {
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

	private static HttpSessionSecurityContextRepository completionRepository() {
		var repository = new HttpSessionSecurityContextRepository();
		repository.setAllowSessionCreation(false);
		return repository;
	}

	/** 계정 폐기나 컨테이너 만료는 세션 잠금 밖에서도 발생한다. 어떤 단계도 세션을 재생성하지 않는다. */
	private static HttpServletRequest existingSessionOnly(HttpServletRequest request, HttpSession original) {
		return new HttpServletRequestWrapper(request) {
			@Override
			public HttpSession getSession(boolean create) {
				HttpSession current = super.getSession(false);
				if (current == null || !original.getId().equals(current.getId())) {
					throw new IllegalStateException("가입 대기 세션이 만료되었습니다.");
				}
				return current;
			}

			@Override
			public HttpSession getSession() {
				return getSession(false);
			}
		};
	}

	private record PendingLogin(HttpSession session, String sessionId, Object mutex, PendingOAuthSession pending) { }
}
