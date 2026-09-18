package me.jsjlog.blog.common.security.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 제공자 인증은 끝났지만 아직 블로그 회원 로그인이 완료되지 않은 사용자.
 *
 * <p>신규 가입과 탈퇴 회원의 재가입 절차에서만 사용한다. 제공자 식별자는 브라우저에
 * 내려보내지 않고 서버 세션의 인증 객체 안에 보관한다. 이후 성공 핸들러는 이 객체를
 * 가입 대기 세션으로 옮긴 뒤 SecurityContext 를 비워야 한다.</p>
 */
public final class PendingOAuthPrincipal implements OAuth2User {

    private final OAuthUserInfo userInfo;
    private final String suggestedNickname;
    private final OAuthLoginState loginState;
    private final Map<String, Object> attributes;

    private PendingOAuthPrincipal(
            OAuthUserInfo userInfo,
            String suggestedNickname,
            OAuthLoginState loginState
    ) {
        this.userInfo = userInfo;
        this.suggestedNickname = suggestedNickname;
        this.loginState = loginState;
        this.attributes = Map.of(
                "loginState", loginState.name(),
                "suggestedNickname", suggestedNickname
        );
    }

    public static PendingOAuthPrincipal signup(OAuthUserInfo userInfo, String suggestedNickname) {
        return new PendingOAuthPrincipal(userInfo, suggestedNickname, OAuthLoginState.SIGNUP_REQUIRED);
    }

    public static PendingOAuthPrincipal reactivation(OAuthUserInfo userInfo, String nickname) {
        return new PendingOAuthPrincipal(userInfo, nickname, OAuthLoginState.REACTIVATION_REQUIRED);
    }

    public AuthProvider getProvider() {
        return userInfo.provider();
    }

    public String getProviderUserId() {
        return userInfo.providerUserId();
    }

    public String getSuggestedNickname() {
        return suggestedNickname;
    }

    public String getEmail() {
        return userInfo.email();
    }

    public String getProfileImageUrl() {
        return userInfo.profileImageUrl();
    }

    public OAuthLoginState getLoginState() {
        return loginState;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /** 제공자 식별자가 로그와 감사 컬럼에 노출되지 않게 대기 상태만 반환한다. */
    @Override
    public String getName() {
        return "pending-oauth";
    }

    @Override
    public String toString() {
        return "PendingOAuthPrincipal{provider=%s, loginState=%s}"
                .formatted(userInfo.provider(), loginState);
    }
}
