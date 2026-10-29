package me.jsjlog.blog.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 글 하나의 조회 기록 한 페이지.
 *
 * @param totalViews         {@code Post.views}. 세션마다 한 번 오르는 누적값이고 기록 시작 전부터 쌓였다.
 *                           화면이 기록 건수와 나란히 두고 기준이 다르다고 알리는 데 쓴다
 * @param recordingStartedAt 블로그 전체에서 가장 오래된 기록의 시각. 기록이 하나도 없으면 비어 있다
 */
public record AdminPostViewListResponse(
		Long postId,
		String postTitle,
		long totalViews,
		OffsetDateTime recordingStartedAt,
		AdminPostViewCounts counts,
		List<AdminPostViewResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
