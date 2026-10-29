package me.jsjlog.blog.admin.dto;

import java.util.List;

/** 기간 안 조회 기록이 많은 글부터 한 페이지 */
public record AdminPostViewStatsListResponse(
		int days,
		List<AdminPostViewStatsResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
