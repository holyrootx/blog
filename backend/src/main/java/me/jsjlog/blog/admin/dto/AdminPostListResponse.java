package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminPostListResponse(
        List<AdminPostSummaryResponse> items,
        AdminPostStatusCounts statusCounts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
