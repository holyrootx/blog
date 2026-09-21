package me.jsjlog.blog.notification.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.post.domain.Comment;

/**
 * 알림 한 건.
 *
 * <p>문구는 서버가 만들지 않는다. 화면이 {@code type} 을 보고 말을 고른다 —
 * 문구를 서버가 정하면 화면 문구를 고칠 때마다 서버를 배포해야 한다.</p>
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        /** 알림을 만든 사람. 답글을 단 사람이거나 내 글에 댓글을 단 사람 */
        String actorNickname,
        Long postId,
        String postTitle,
        Long commentId,
        /** 목록에서 미리 보여 줄 만큼만 */
        String preview,
        LocalDateTime createdAt,
        boolean unread
) {

    private static final int PREVIEW_LENGTH = 60;

    public static NotificationResponse from(Notification notification) {
        Comment comment = notification.getComment();

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                comment.getMember().getNickname(),
                comment.getPost().getId(),
                comment.getPost().getTitle(),
                comment.getId(),
                preview(comment.getContent()),
                notification.getCreatedAt(),
                notification.isUnread()
        );
    }

    private static String preview(String content) {
        String text = content == null ? "" : content.strip();

        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }
}
