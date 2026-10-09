package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminCommonCodeGroupListResponse(
        List<AdminCommonCodeGroupSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
