package me.jsjlog.blog.admin.dto;

import java.util.List;

/**
 * 댓글 하나를 판단하는 데 필요한 것 전부.
 *
 * <p>신고 내역과 조치 이력을 함께 내려준다. 따로 부르게 하면 화면이 두 번 기다리고,
 * 둘 중 하나만 보고 판단하는 일이 생긴다 — 이미 가린 댓글인 줄 모르고 또 가리는 식으로.</p>
 */
public record AdminCommentModerationDetail(
        Long commentId,
        List<AdminCommentReportResponse> reports,
        List<AdminCommentModerationResponse> moderations
) {
}
