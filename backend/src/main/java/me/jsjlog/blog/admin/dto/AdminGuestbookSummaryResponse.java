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
        long unhandledReportCount
) {
}
