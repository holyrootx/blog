package me.jsjlog.blog.common.security.oauth;

import java.util.Map;

import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * 네이버는 값이 전부 {@code response} 아래 한 겹 들어가 있다.
 *
 * <pre>
 * {
 *   "resultcode": "00",
 *   "message": "success",
 *   "response": { "id": "32742776", "nickname": "...", "email": "...", "profile_image": "https://..." }
 * }
 * </pre>
 *
 * <p>최상위에는 우리가 쓸 값이 하나도 없다. 그래서 Spring Security 설정의
 * {@code user-name-attribute} 에 {@code response} 를 적게 된다 — 최상위에 있는
 * 식별자 후보가 그것뿐이기 때문이다. 그 값은 맵이라 이름으로 쓸 수 없고, 어차피
 * 우리가 principal 을 직접 만들어 {@code getName()} 을 정하므로 문제가 되지 않는다.</p>
 *
 * <p>프로필 이미지 키가 구글·카카오와 달리 {@code profile_image} 로 {@code _url} 이 없다.
 * 이름이 비슷해서 틀리기 쉬운 자리다.</p>
 */
@Component
public class NaverAttributeReader extends OAuthAttributeReader {

    private static final String RESPONSE = "response";

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    protected String providerUserId(Map<String, Object> attributes) {
        return stringAt(attributes, RESPONSE, "id");
    }

    @Override
    protected String nickname(Map<String, Object> attributes) {
        return stringAt(attributes, RESPONSE, "nickname");
    }

    @Override
    protected String email(Map<String, Object> attributes) {
        return stringAt(attributes, RESPONSE, "email");
    }

    @Override
    protected String profileImageUrl(Map<String, Object> attributes) {
        return stringAt(attributes, RESPONSE, "profile_image");
    }
}
