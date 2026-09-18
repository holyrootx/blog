package me.jsjlog.blog.member.dto;

import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;

/** 가입 또는 재가입 화면에 필요한 공개 정보만 내려준다. */
public record OAuthPendingResponse(
        OAuthLoginState loginState,
        String suggestedNickname
) {

    public static OAuthPendingResponse from(PendingOAuthSession pendingSession) {
        return new OAuthPendingResponse(
                pendingSession.loginState(),
                pendingSession.suggestedNickname()
        );
    }
}
