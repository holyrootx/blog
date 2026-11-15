package me.jsjlog.blog.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.jsjlog.blog.common.config.ApiPaths;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/** 가입 완료의 최종 세션 갱신과 로그인·로그아웃 필터의 세션 전환을 같은 순서로 처리한다. */
public class AuthenticationSessionLockFilter extends OncePerRequestFilter {

	private final RequestMatcher transitions = new OrRequestMatcher(
			PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.LOGIN),
			PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.LOGOUT),
			PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.MEMBER_LOGOUT),
			PathPatternRequestMatcher.pathPattern(ApiPaths.Auth.OAUTH2_CALLBACK_ALL));

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !transitions.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Object mutex = null;
		try {
			HttpSession session = request.getSession(false);
			if (session != null) {
				mutex = WebUtils.getSessionMutex(session);
			}
		} catch (IllegalStateException expired) {
			// 컨테이너가 먼저 만료했다면 기존 세션을 갱신할 수 없다. 인증 필터가 정상 처리한다.
		}
		if (mutex == null) {
			chain.doFilter(request, response);
			return;
		}
		synchronized (mutex) {
			chain.doFilter(request, response);
		}
	}
}
