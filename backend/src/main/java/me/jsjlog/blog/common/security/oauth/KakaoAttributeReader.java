package me.jsjlog.blog.common.security.oauth;

import java.util.Map;

import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * 카카오는 식별자만 최상위에 있고 나머지는 {@code kakao_account} 아래에 들어 있다.
 *
 * <pre>
 * {
 *   "id": 1234567890,
 *   "kakao_account": {
 *     "email": "...",
 *     "profile": { "nickname": "...", "profile_image_url": "https://..." }
 *   }
 * }
 * </pre>
 *
 * <p>주의할 것 둘.</p>
 *
 * <p>{@code id} 가 <b>JSON 숫자</b>다. 맵에는 {@code Long} 으로 들어 있어서
 * {@code (String) attributes.get("id")} 는 실행 즉시 터진다.
 * {@link OAuthAttributeReader#stringAt} 가 그 처리를 한다.</p>
 *
 * <p>닉네임과 이메일은 <b>선택 동의</b> 항목이다. 동의를 안 받으면 {@code kakao_account}
 * 안에 키 자체가 없거나 {@code ..._needs_agreement: true} 만 온다. 그래서 둘 다 없는 경우를
 * 정상 흐름으로 두고, 있으면 쓴다.</p>
 */
@Component
public class KakaoAttributeReader extends OAuthAttributeReader {

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    protected String providerUserId(Map<String, Object> attributes) {
        return stringAt(attributes, "id");
    }

    @Override
    protected String nickname(Map<String, Object> attributes) {
        return stringAt(attributes, "kakao_account", "profile", "nickname");
    }

    @Override
    protected String email(Map<String, Object> attributes) {
        return stringAt(attributes, "kakao_account", "email");
    }

    @Override
    protected String profileImageUrl(Map<String, Object> attributes) {
        // thumbnail_image_url 도 같은 자리에 오지만 작다. 화면에서 줄이는 게 낫다
        return stringAt(attributes, "kakao_account", "profile", "profile_image_url");
    }
}
