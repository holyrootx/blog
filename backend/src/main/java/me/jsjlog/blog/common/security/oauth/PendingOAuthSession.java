package me.jsjlog.blog.common.security.oauth;

import java.io.Serializable;

import me.jsjlog.blog.member.domain.AuthProvider;

/**
 * 신규 가입 또는 재가입이 끝날 때까지 서버 세션에만 보관하는 소셜 인증 결과.
 *
 * <p>브라우저가 보내는 회원 식별자를 신뢰하지 않도록 제공자와 제공자 회원 번호를
 * 서버가 보관한다. 가입이 완료되거나 취소되면 세션에서 즉시 제거해야 한다.</p>
 */
public record PendingOAuthSession(
        AuthProvider provider,
        String providerUserId,
        String suggestedNickname,
        String email,
        String profileImageUrl,
        OAuthLoginState loginState
) implements Serializable {

    public static final String ATTRIBUTE_NAME = PendingOAuthSession.class.getName();

    public static PendingOAuthSession from(PendingOAuthPrincipal principal) {
        return new PendingOAuthSession(
                principal.getProvider(),
                principal.getProviderUserId(),
                principal.getSuggestedNickname(),
                principal.getEmail(),
                principal.getProfileImageUrl(),
                principal.getLoginState()
        );
    }
}
