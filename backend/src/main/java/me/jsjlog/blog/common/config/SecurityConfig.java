package me.jsjlog.blog.common.config;

import me.jsjlog.blog.common.security.AdminLoginConfigurer;
import me.jsjlog.blog.common.security.AdminLoginFailureHandler;
import me.jsjlog.blog.common.security.AdminLoginSuccessHandler;
import me.jsjlog.blog.common.security.AdminLogoutSuccessHandler;
import me.jsjlog.blog.common.security.AdminUserDetailsService;
import me.jsjlog.blog.common.security.JsonAccessDeniedHandler;
import me.jsjlog.blog.common.security.JsonAuthenticationEntryPoint;
import me.jsjlog.blog.common.security.oauth.CustomOAuth2UserService;
import me.jsjlog.blog.common.security.oauth.OAuth2LoginFailureHandler;
import me.jsjlog.blog.common.security.oauth.OAuth2LoginSuccessHandler;
import me.jsjlog.blog.member.domain.MemberRole;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
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
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oauth2LoginFailureHandler,
            CustomOAuth2UserService customOAuth2UserService,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper
    ) throws Exception {
        httpSecurity
                .securityMatcher(
                        ApiPaths.API_ALL,
                        ApiPaths.Auth.OAUTH2_AUTHORIZATION_ALL,
                        ApiPaths.Auth.OAUTH2_CALLBACK_ALL
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                ApiPaths.Public.HEALTH,
                                ApiPaths.Public.BLOG_ALL,
                                ApiPaths.Auth.CSRF
                        ).permitAll()
                        .requestMatchers(
                                ApiPaths.Auth.OAUTH2_AUTHORIZATION_ALL,
                                ApiPaths.Auth.OAUTH2_CALLBACK_ALL
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.Auth.LOGIN).permitAll()
                        // 로그인하지 않은 방문자가 공개 화면을 볼 때마다 부르는 자리다.
                        // 인증을 걸면 "아무도 아님" 이 401 로 나가서 글 한 번 읽을 때마다 오류가 쌓인다
                        .requestMatchers(HttpMethod.GET, ApiPaths.Auth.MEMBER_ME).permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.Auth.OAUTH_PENDING).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                ApiPaths.Auth.OAUTH_SIGNUP,
                                ApiPaths.Auth.OAUTH_REACTIVATE,
                                ApiPaths.Auth.OAUTH_REJOIN
                        ).permitAll()
                        // 자기 계정을 고치는 자리. 관리자도 회원이라 같이 들어온다.
                        // 관리자 탈퇴는 여기서 막지 않고 서비스가 사유를 붙여 거절한다 —
                        // 403 만 주면 왜 안 되는지 화면이 설명할 수 없다
                        .requestMatchers(HttpMethod.PUT, ApiPaths.Auth.MEMBER_NICKNAME)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, ApiPaths.Auth.MEMBER_COMMENTS)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, ApiPaths.Auth.MEMBER_WITHDRAW)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        // 알림은 로그인한 사람에게만 존재하는 개념이라 조회도 인증을 건다
                        .requestMatchers(
                                ApiPaths.Auth.MEMBER_NOTIFICATIONS,
                                ApiPaths.Auth.MEMBER_NOTIFICATIONS_ALL
                        ).hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, ApiPaths.Comment.CREATE)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, ApiPaths.Comment.REACTION)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, ApiPaths.Comment.REACTION)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        // 신고는 로그인한 회원만 한다. 익명으로 열어 두면 한 사람이 창을 새로
                        // 열어 가며 몇 번이고 신고할 수 있어서 신고 수가 아무 뜻이 없어진다
                        .requestMatchers(HttpMethod.POST, ApiPaths.Comment.REPORT)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        // 내 댓글 고치기·지우기. 남의 것인지는 서비스가 본다 —
                        // 여기서는 "로그인은 했는가" 까지만 가린다
                        .requestMatchers(HttpMethod.PUT, ApiPaths.Comment.ITEM)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, ApiPaths.Comment.ITEM)
                        .hasAnyRole(MemberRole.USER.name(), MemberRole.ADMIN.name())
                        .requestMatchers(ApiPaths.Admin.ALL).hasRole(MemberRole.ADMIN.name())
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
                // 세션 무효화와 컨텍스트 비우기는 LogoutFilter 가 한다. 응답 형식만 맞춘다.
                //
                // 관리자와 회원이 같은 필터를 쓴다. 로그아웃은 세션을 버리는 일이라 둘이 같고,
                // 컨트롤러로 따로 만들면 프레임워크가 하는 일(세션 무효화·컨텍스트 정리·쿠키 삭제)을
                // 반쪽만 따라 하게 된다. 경로만 둘 다 받는다
                .logout(logout -> logout
                        .logoutRequestMatcher(new OrRequestMatcher(
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.POST, ApiPaths.Auth.LOGOUT),
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.POST, ApiPaths.Auth.MEMBER_LOGOUT)))
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .with(AdminLoginConfigurer.of(
                        objectMapper, authenticationManager, loginSuccessHandler, loginFailureHandler),
                        configurer -> { });

        ClientRegistrationRepository clientRegistrationRepository =
                clientRegistrationRepositoryProvider.getIfAvailable();

        // OAuth 클라이언트 환경변수가 없는 개발 환경에서는 기존 관리자 인증만 기동한다.
        // 등록 정보가 생긴 환경에서만 Spring Security OAuth2 필터를 연결한다.
        if (clientRegistrationRepository != null) {
            httpSecurity.oauth2Login(oauth2 -> oauth2
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService))
                    .successHandler(oauth2LoginSuccessHandler)
                    .failureHandler(oauth2LoginFailureHandler)
            );
        }

        return httpSecurity.build();
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
