package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.CommentReport;

import java.time.LocalDateTime;

/**
 * 관리자 화면에 보여 줄 신고 한 건.
 *
 * <p><b>누가 신고했는지는 담지 않는다.</b> 운영자가 신고자를 알면 그다음 그 사람의 댓글을
 * 읽는 눈이 달라지고, 신고한 쪽도 알려진다는 것을 알면 신고를 망설인다. 판단에 필요한
 * 것은 무엇이 문제라는 주장이지 누가 했는지가 아니다.</p>
 */
public record AdminCommentReportResponse(
        Long id,
        String reason,
        String reasonLabel,
        String detail,
        LocalDateTime reportedAt,
        boolean handled
) {

    public static AdminCommentReportResponse from(CommentReport report) {
        return new AdminCommentReportResponse(
                report.getId(),
                report.getReason().name(),
                report.getReason().getLabel(),
                report.getDetail(),
                report.getCreatedAt(),
                report.getHandledAt() != null
        );
    }
}
