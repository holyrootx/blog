package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminCommentListResponse(
        List<AdminCommentSummaryResponse> items,
        AdminCommentCounts statusCounts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
