package me.jsjlog.blog.member.dto;

import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;

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
