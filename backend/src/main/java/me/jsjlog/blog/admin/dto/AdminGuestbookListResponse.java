package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminGuestbookListResponse(
		List<AdminGuestbookSummaryResponse> items,
		AdminGuestbookCounts statusCounts,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
