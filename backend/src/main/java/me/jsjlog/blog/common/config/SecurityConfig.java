package me.jsjlog.blog.common.config;

import me.jsjlog.blog.admin.domain.AdminRole;
import me.jsjlog.blog.common.security.AdminLoginConfigurer;
import me.jsjlog.blog.common.security.AdminLoginFailureHandler;
import me.jsjlog.blog.common.security.AdminLoginSuccessHandler;
import me.jsjlog.blog.common.security.AdminLogoutSuccessHandler;
import me.jsjlog.blog.common.security.AdminUserDetailsService;
import me.jsjlog.blog.common.security.JsonAccessDeniedHandler;
import me.jsjlog.blog.common.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            AdminLoginSuccessHandler loginSuccessHandler,
            AdminLoginFailureHandler loginFailureHandler,
            AdminLogoutSuccessHandler logoutSuccessHandler,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper
    ) throws Exception {
        return httpSecurity
                .securityMatcher(ApiPaths.API_ALL)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                ApiPaths.Public.HEALTH,
                                ApiPaths.Public.BLOG_ALL,
                                ApiPaths.Auth.CSRF
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.Auth.LOGIN).permitAll()
                        .requestMatchers(ApiPaths.Admin.ALL).hasRole(AdminRole.ADMIN.name())
                        .anyRequest().denyAll()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 기본값(세션 저장소)을 그대로 쓴다. 쿠키 저장소는 화면이 읽을 수 있게
                // httpOnly 를 풀어야 하는데, 토큰을 내주는 API 가 어차피 필요해서 얻는 게 없다.
                //
                // 따로 열어 주는 경로는 없다. GET·HEAD 는 원래 검사 대상이 아니라
                // 공개 블로그와 토큰 조회 API 는 그대로 열려 있다
                .csrf(Customizer.withDefaults())
                // 막힌 요청을 기억해 뒀다가 로그인 뒤 그리로 보내는 기능.
                // 화면 이동은 프론트 라우터가 하므로 서버가 기억할 이유가 없고,
                // 켜 두면 익명 요청마다 세션이 하나씩 생긴다
                .requestCache(RequestCacheConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // 세션 무효화와 컨텍스트 비우기는 LogoutFilter 가 한다. 응답 형식만 맞춘다
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(
                                HttpMethod.POST, ApiPaths.Auth.LOGOUT))
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .with(AdminLoginConfigurer.of(
                        objectMapper, authenticationManager, loginSuccessHandler, loginFailureHandler),
                        configurer -> { })
                .build();
    }

    /**
     * BCrypt 만 쓴다. 해시 문자열에 알고리즘과 강도가 같이 들어 있어
     * 나중에 강도를 올려도 기존 해시를 그대로 검증할 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 로그인 API 가 직접 호출할 인증기.
     *
     * 아이디를 못 찾은 경우와 비밀번호가 틀린 경우를 같은 예외로 덮는 것은
     * DaoAuthenticationProvider 의 기본 동작이다. 끄지 않는다 —
     * 구분해서 알려주면 어떤 아이디가 존재하는지 확인해 주는 꼴이 된다.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AdminUserDetailsService adminUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }
}
