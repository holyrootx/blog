package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 방명록 글 상세. 지금 본문과 신고 목록 */
public record AdminGuestbookDetail(
		AdminGuestbookSummaryResponse entry,
		LocalDateTime deletedAt,
		/** 쓴 사람이 지운 글의 원문을 파기할 날. 가린 글·보이는 글은 null */
		LocalDateTime retainedUntil,
		List<AdminGuestbookReportResponse> reports
) {
}
