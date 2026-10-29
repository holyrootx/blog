package me.jsjlog.blog.guestbook.dto;

import java.time.LocalDateTime;

/** 운영자 답글. 답글을 쓴 운영자의 닉네임을 같이 준다 */
public record GuestbookReplyResponse(
		String nickname,
		String content,
		LocalDateTime repliedAt
) {
}
