package me.jsjlog.blog.guestbook.dto;

import java.time.LocalDateTime;

public record GuestbookReplyResponse(
		String nickname,
		String content,
		LocalDateTime repliedAt
) {
}
