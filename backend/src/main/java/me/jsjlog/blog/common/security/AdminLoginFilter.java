package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.jsjlog.blog.admin.dto.AdminLoginRequest;
import me.jsjlog.blog.common.config.ApiPaths;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 아이디·비밀번호를 JSON 으로 받는 로그인 필터.
 *
 * 폼 로그인 대신 이걸 쓰는 이유는 나머지 API 가 전부 JSON 이라서다.
 * 로그인 하나만 form-urlencoded 로 두면 프론트가 그 자리만 다르게 보내야 한다.
 *
 * 컨트롤러로 직접 만들지 않은 이유는, 로그인에 성공했을 때 해야 할 일이
 * 인증 한 번으로 끝나지 않기 때문이다 — 세션 ID 재발급, CSRF 토큰 회전,
 * SecurityContext 를 세션에 저장까지 해야 한다. 하나라도 빠지면 조용히 뚫리거나
 * 다음 요청에서 로그인이 풀린다. AbstractAuthenticationProcessingFilter 가 그 순서를 안다.
 */
public class AdminLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper;

    public AdminLoginFilter(ObjectMapper objectMapper) {
        super(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, ApiPaths.Auth.LOGIN));
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        AdminLoginRequest loginRequest = readBody(request);

        if (!StringUtils.hasText(loginRequest.username()) || !StringUtils.hasText(loginRequest.password())) {
            // 빈 값도 로그인 실패로 다룬다. 400 과 401 을 나눠 주면
            // 어떤 요청이 "형식은 맞았다"는 정보가 된다
            throw new BadCredentialsException("아이디와 비밀번호를 입력해 주세요.");
        }

        return getAuthenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));
    }

    private AdminLoginRequest readBody(HttpServletRequest request) {
        try {
            return objectMapper.readValue(request.getInputStream(), AdminLoginRequest.class);
        } catch (Exception exception) {
            // 본문이 JSON 이 아니어도 실패 응답은 같아야 한다
            throw new BadCredentialsException("로그인 요청을 읽지 못했습니다.");
        }
    }
}
