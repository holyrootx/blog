package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminPostViewStatsListResponse(
		int days,
		List<AdminPostViewStatsResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
