package me.jsjlog.blog.post.dto;

import java.util.List;

/** 내가 최근 본 글 한 페이지. 마지막으로 본 시각이 늦은 글부터 */
public record RecentPostListResponse(
        List<RecentPostResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static RecentPostListResponse of(List<RecentPostResponse> items, int page, int size, long totalElements) {
        int totalPages = size < 1 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new RecentPostListResponse(items, page, size, totalElements, totalPages);
    }
}
