package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.CommentModeration;

import java.time.LocalDateTime;

/** 이 댓글에 운영자가 한 조치 한 건 */
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
