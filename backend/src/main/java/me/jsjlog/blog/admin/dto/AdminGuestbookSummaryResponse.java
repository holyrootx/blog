package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/** 관리자 방명록 목록 한 줄. 운영자는 비밀글도 본문을 본다 */
public record AdminGuestbookSummaryResponse(
		Long id,
		Long memberId,
		String nickname,
		boolean writtenByAdmin,
		String content,
		boolean secret,
		boolean edited,
		LocalDateTime createdAt,
		boolean deleted,
		boolean hiddenByAdmin,
		boolean contentPurged,
		String replyContent,
		LocalDateTime repliedAt,
		long reportCount,
		long unhandledReportCount,
		/** 쓴 사람이 지운 글인가(지운 뒤 운영자가 가린 것 포함). 운영자는 자기가 가린 글만 되돌린다 */
		boolean deletedByWriter
) {
}
