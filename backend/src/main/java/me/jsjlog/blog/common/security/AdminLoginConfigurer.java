package me.jsjlog.blog.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON 로그인 필터를 보안 체인에 끼운다.
 *
 * 필터를 직접 new 해서 addFilterBefore 로 넣지 않고 설정기(Configurer)로 만든 이유는
 * {@link #configure} 가 불리는 시점에야 {@code SessionAuthenticationStrategy} 공유 객체가
 * 준비되기 때문이다. 그 안에는 세션 ID 재발급과 CSRF 토큰 회전이 함께 들어 있다.
 *
 * 직접 만들어 넣으면 그 목록을 우리가 외워야 하고, CSRF 를 켜는 날 토큰 회전이 조용히 빠진다.
 * 폼 로그인 설정기도 같은 방식으로 이 공유 객체를 가져다 쓴다.
 */
@RequiredArgsConstructor
public class AdminLoginConfigurer extends AbstractHttpConfigurer<AdminLoginConfigurer, HttpSecurity> {

    private final ObjectMapper objectMapper;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationSuccessHandler successHandler;
    private final AuthenticationFailureHandler failureHandler;

    @Override
    public void configure(HttpSecurity http) {
        AdminLoginFilter filter = new AdminLoginFilter(objectMapper);

        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationSuccessHandler(successHandler);
        filter.setAuthenticationFailureHandler(failureHandler);

        // 기본값은 요청 하나 동안만 유지되는 저장소다. 그대로 두면 로그인 응답은 200 인데
        // 다음 요청에서 다시 401 이 된다
        filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());

        SessionAuthenticationStrategy sessionStrategy =
                http.getSharedObject(SessionAuthenticationStrategy.class);

        if (sessionStrategy != null) {
            filter.setSessionAuthenticationStrategy(sessionStrategy);
        }

        // 폼 로그인 필터가 서는 자리에 대신 세운다
        http.addFilterBefore(postProcess(filter), UsernamePasswordAuthenticationFilter.class);
    }

    /** {@code http.with(...)} 에서 쓰기 좋게 감싼다 */
    public static AdminLoginConfigurer of(
            ObjectMapper objectMapper,
            AuthenticationManager authenticationManager,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler
    ) {
        return new AdminLoginConfigurer(objectMapper, authenticationManager, successHandler, failureHandler);
    }
}
