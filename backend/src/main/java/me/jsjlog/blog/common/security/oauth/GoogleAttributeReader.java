package me.jsjlog.blog.common.security.oauth;

import java.util.Map;

import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * 구글은 표준 OIDC 라 값이 전부 최상위에 평평하게 온다.
 *
 * <pre>
 * { "sub": "1087...", "name": "정성주", "email": "...", "picture": "https://..." }
 * </pre>
 *
 * {@code sub} 는 구글이 우리 애플리케이션에 고정으로 주는 번호다. 사용자가 구글 계정의
 * 이메일이나 이름을 바꿔도 그대로다. 그래서 이것만 정체성으로 쓴다.
 */
@Component
public class GoogleAttributeReader extends OAuthAttributeReader {

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    protected String providerUserId(Map<String, Object> attributes) {
        return stringAt(attributes, "sub");
    }

    @Override
    protected String nickname(Map<String, Object> attributes) {
        return stringAt(attributes, "name");
    }

    @Override
    protected String email(Map<String, Object> attributes) {
        // email_verified 를 보지 않는다. 확인된 이메일이어도 회원을 찾는 데 쓰지 않기로 했고,
        // 저장만 하는 값에 조건을 달면 "확인된 것만 저장" 같은 규칙이 생겨 나중에 병합 로직을 부른다
        return stringAt(attributes, "email");
    }

    @Override
    protected String profileImageUrl(Map<String, Object> attributes) {
        return stringAt(attributes, "picture");
    }
}
