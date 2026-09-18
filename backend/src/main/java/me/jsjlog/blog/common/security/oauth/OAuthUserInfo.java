package me.jsjlog.blog.common.security.oauth;

import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.util.StringUtils;

/**
 * 제공자마다 다른 응답을 하나의 모양으로 정리한 결과.
 *
 * 구글·카카오·네이버는 같은 정보를 서로 다른 키와 깊이에 담아 준다. 회원을 만들거나 찾는
 * 코드가 그 차이를 알아야 하면 제공자를 추가할 때마다 그 코드까지 고쳐야 한다.
 * 차이는 {@link OAuthAttributeReader} 안에서 끝내고, 밖으로는 이 타입만 나간다.
 *
 * <p>{@code providerUserId} 만 반드시 있다. 나머지 셋은 제공자 정책과 사용자 동의에 따라
 * 비어 올 수 있다 — 특히 카카오·네이버의 이메일과 닉네임은 선택 동의 항목이다.</p>
 */
public record OAuthUserInfo(
        AuthProvider provider,
        String providerUserId,
        String nickname,
        String email,
        String profileImageUrl
) {

    /**
     * 닉네임은 회원의 필수 값인데 제공자가 안 줄 수 있다. 부르는 쪽이 대체 이름을
     * 정해야 하는 상황을 빠뜨리지 않도록 물어볼 자리를 만들어 둔다.
     */
    public boolean hasNickname() {
        return StringUtils.hasText(nickname);
    }

    public boolean hasEmail() {
        return StringUtils.hasText(email);
    }

    /**
     * 이메일과 프로필 주소는 찍지 않는다.
     *
     * 이 객체는 로그인 실패를 쫓을 때 로그에 얹히기 쉬운 자리에 있다. record 의 기본
     * toString 을 그대로 두면 로그 파일에 회원 이메일이 그대로 쌓인다.
     */
    @Override
    public String toString() {
        return "OAuthUserInfo{provider=%s, providerUserId=%s, hasNickname=%s, hasEmail=%s}"
                .formatted(provider, providerUserId, hasNickname(), hasEmail());
    }
}
