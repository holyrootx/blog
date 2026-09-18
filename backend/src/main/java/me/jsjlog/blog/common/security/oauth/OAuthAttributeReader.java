package me.jsjlog.blog.common.security.oauth;

import java.util.Map;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.util.StringUtils;

/**
 * 제공자 응답에서 회원 정보를 꺼낸다.
 *
 * <p>제공자를 하나 더 붙이는 일이 "이 클래스를 상속해서 네 메서드를 채우고 빈으로 등록한다"
 * 로 끝나게 하는 것이 목적이다. 검증과 조립은 {@link #read(Map)} 에서 한 번만 하고,
 * 하위 클래스는 <b>어디서 꺼내는지만</b> 안다.</p>
 *
 * <p>인터페이스가 아니라 추상클래스인 이유는 실제로 나눠 쓸 동작이 있어서다.
 * 세 제공자 모두 중첩된 맵을 파고들어야 하고(카카오 {@code kakao_account.profile.nickname},
 * 네이버 {@code response.nickname}), 값이 문자열이 아닐 때를 똑같이 다뤄야 한다.
 * 그 처리를 각 하위 클래스가 따로 쓰면 제공자마다 다른 방식으로 조용히 틀린다.</p>
 */
public abstract class OAuthAttributeReader {

    public abstract AuthProvider provider();

    /**
     * 하위 클래스가 덮지 못하게 final 이다. 여기서 하는 검증을 건너뛴 경로가 생기면
     * 그 제공자만 식별자 없는 회원을 만들 수 있게 된다.
     */
    public final OAuthUserInfo read(Map<String, Object> attributes) {
        String providerUserId = providerUserId(attributes);

        // 식별자가 없으면 회원을 찾을 수도, 만들 수도 없다. 그냥 넘기면 provider_user_id 가
        // 빈 회원이 생기고, 그 뒤로 식별자 없이 로그인한 사람들이 전부 그 회원으로 붙는다
        if (!StringUtils.hasText(providerUserId)) {
            throw new BlogException(
                    ErrorCode.OAUTH_ACCOUNT_ID_MISSING,
                    "%s 가 계정 식별자를 주지 않았습니다.".formatted(provider())
            );
        }

        return new OAuthUserInfo(
                provider(),
                providerUserId,
                nickname(attributes),
                email(attributes),
                profileImageUrl(attributes)
        );
    }

    /** 제공자가 정한 회원 고유 번호. 이것만 필수다 */
    protected abstract String providerUserId(Map<String, Object> attributes);

    /** 화면에 보일 이름. 동의를 안 받으면 없을 수 있다 */
    protected abstract String nickname(Map<String, Object> attributes);

    /** 없을 수 있다. 회원을 찾는 데 쓰지 않는다 — 받아서 저장만 한다 */
    protected abstract String email(Map<String, Object> attributes);

    /** 없을 수 있다 */
    protected abstract String profileImageUrl(Map<String, Object> attributes);

    /**
     * 중첩된 응답에서 문자열 하나를 꺼낸다. 길 어딘가가 없으면 {@code null}.
     *
     * <p>{@code String} 으로 형변환하지 않고 {@link String#valueOf} 를 쓴다.
     * 카카오의 {@code id} 는 JSON 숫자로 와서 맵에는 {@code Long} 으로 들어 있고,
     * 형변환하면 그 자리에서 {@link ClassCastException} 이 난다.</p>
     */
    protected static String stringAt(Map<String, Object> attributes, String... path) {
        Object value = valueAt(attributes, path);

        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();

        // 제공자가 빈 문자열을 주는 경우가 있다. "없음" 과 같게 다룬다
        return text.isEmpty() ? null : text;
    }

    /**
     * 중첩된 응답에서 하위 맵을 꺼낸다. 없으면 빈 맵을 준다.
     *
     * 하위 클래스가 맵을 직접 다뤄야 할 때 {@code null} 검사를 각자 하지 않게 한다.
     */
    protected static Map<String, Object> mapAt(Map<String, Object> attributes, String... path) {
        Object value = valueAt(attributes, path);

        if (value instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) nested;
            return typed;
        }

        return Map.of();
    }

    private static Object valueAt(Map<String, Object> attributes, String... path) {
        if (attributes == null || path.length == 0) {
            return null;
        }

        Object current = attributes;

        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current = map.get(key);
        }

        return current;
    }
}
