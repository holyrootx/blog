package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.CommentModeration;

import java.time.LocalDateTime;

public record AdminCommentModerationResponse(
		Long id,
		String action,
		String actionLabel,
		String reason,
		String adminNickname,
		LocalDateTime actedAt
) {

	public static AdminCommentModerationResponse from(CommentModeration moderation) {
		return new AdminCommentModerationResponse(
				moderation.getId(),
				moderation.getAction().name(),
				moderation.getAction().getLabel(),
				moderation.getReason(),
				moderation.getAdmin().getNickname(),
				moderation.getActedAt()
		);
	}
}
