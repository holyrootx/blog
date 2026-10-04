package me.jsjlog.blog.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.jsjlog.blog.common.config.ApiPaths;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** CSRF보다 먼저 만료된 인증을 정리하되 유효한 세션의 CSRF 검사는 그대로 둔다. */
public class MemberSessionFilter extends OncePerRequestFilter {

    private final MemberSessionManager sessions;
    private final SecurityResponseWriter writer;
    private final CookieClearingLogoutHandler clearCookie = new CookieClearingLogoutHandler("JSESSIONID");
    private final RequestMatcher publicRequest = publicRequests();

    public static RequestMatcher publicRequests() {
        return new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, ApiPaths.Public.HEALTH),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, ApiPaths.Public.BLOG_ALL),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, ApiPaths.Auth.MEMBER_ME),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, ApiPaths.Auth.CSRF),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, ApiPaths.Auth.OAUTH_PENDING),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.LOGIN),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.LOGOUT),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.MEMBER_LOGOUT),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.OAUTH_SIGNUP),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.OAUTH_REACTIVATE),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.OAUTH_REJOIN),
            PathPatternRequestMatcher.pathPattern(ApiPaths.Auth.OAUTH2_AUTHORIZATION_ALL),
            PathPatternRequestMatcher.pathPattern(ApiPaths.Auth.OAUTH2_CALLBACK_ALL));
    }

    public MemberSessionFilter(MemberSessionManager sessions, SecurityResponseWriter writer) {
        this.sessions = sessions;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal principal) {
            try {
                if (!sessions.validateAndRegister(request.getSession(false), principal)) {
                    SecurityContextHolder.clearContext();
                    clearCookie.logout(request, response, authentication);
                    authentication = null;
                }
            } catch (DataAccessException unavailable) {
                // DB 장애를 정상 인증이나 계정 탈퇴로 해석하지 않는다. 쓰기는 진행하지 않는다.
                writer.writeError(request, response, ErrorCode.INTERNAL_SERVER_ERROR);
                return;
            }
        }
        if (authentication == null && !publicRequest.matches(request)) {
            writer.writeError(request, response, ErrorCode.UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
