package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.guestbook.domain.GuestbookReport;

/** 방명록 신고 한 건. 누가 신고했는지는 내보내지 않는다 */
public record AdminGuestbookReportResponse(
        Long id,
        String reason,
        String reasonLabel,
        String detail,
        LocalDateTime reportedAt,
        boolean handled,
        /** 신고 당시 본문. 신고 뒤에 고쳐도 무엇이 신고됐는지 보이게 한다. 파기했으면 null */
        String reportedContent
) {

    public static AdminGuestbookReportResponse from(GuestbookReport report) {
        return new AdminGuestbookReportResponse(
                report.getId(),
                report.getReason().name(),
                report.getReason().getLabel(),
                report.getDetail(),
                report.getCreatedAt(),
                report.getHandledAt() != null,
                report.getReportedContent()
        );
    }
}
