package me.jsjlog.blog.notification.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
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
		/**
		 * 이 알림이 가리키는 <b>댓글을 쓴 사람</b>.
		 *
		 * <p>타입마다 누구인지가 달라진다. {@code REPLY} 는 답글을 단 사람,
		 * {@code POST_COMMENT} 는 내 글에 댓글을 단 사람이라 둘 다 "상대" 다.
		 * {@code REPORT_RECEIVED} 는 신고자가 아니라 <b>신고당한 댓글을 쓴 사람</b>이고
		 * — 운영자에게는 이쪽이 필요한 정보다 — {@code COMMENT_HIDDEN} 은
		 * <b>받는 사람 자신</b>이라 화면에서 쓰지 않는다.</p>
		 */
		String actorNickname,
		/** 댓글 알림일 때만 채운다. 방명록 알림이면 아래 셋이 비어 있다 */
		Long postId,
		String postTitle,
		Long commentId,
		/**
		 * 방명록 알림일 때만 채운다. {@code actorNickname} 은 새 글·신고면 그 글을 쓴 사람,
		 * 답글이면 답을 단 운영자다.
		 */
		Long guestbookEntryId,
		String preview,
		LocalDateTime createdAt,
		boolean unread
) {

	private static final int PREVIEW_LENGTH = 60;

	public static NotificationResponse from(Notification notification) {
		GuestbookEntry entry = notification.getGuestbookEntry();

		if (entry != null) {
			// 답글 알림의 상대는 답을 단 운영자이고 미리 보기도 답글이다. 내가 쓴 글을 다시 보여 줄 까닭이 없다
			boolean replied = notification.getType() == NotificationType.GUESTBOOK_REPLIED;

			return new NotificationResponse(
					notification.getId(),
					notification.getType(),
					replied ? replierNickname(entry) : entry.getMember().getNickname(),
					null,
					null,
					null,
					entry.getId(),
					notification.getType() == NotificationType.GUESTBOOK_HIDDEN
							? "" : preview(replied ? entry.getReplyContent() : entry.getContent()),
					notification.getCreatedAt(),
					notification.isUnread()
			);
		}

		Comment comment = notification.getComment();

		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				comment.getMember().getNickname(),
				comment.getPost().getId(),
				comment.getPost().getTitle(),
				comment.getId(),
				null,
				preview(comment.getContent()),
				notification.getCreatedAt(),
				notification.isUnread()
		);
	}

	private static String replierNickname(GuestbookEntry entry) {
		return entry.getReplyMember() == null ? "" : entry.getReplyMember().getNickname();
	}

	private static String preview(String content) {
		String text = content == null ? "" : content.strip();

		return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
	}
}
