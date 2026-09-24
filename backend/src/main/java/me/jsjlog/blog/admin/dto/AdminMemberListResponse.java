package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminMemberListResponse(
        List<AdminMemberSummaryResponse> items,
        AdminMemberStatusCounts statusCounts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
