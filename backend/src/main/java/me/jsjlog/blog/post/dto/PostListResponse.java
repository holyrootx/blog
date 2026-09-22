package me.jsjlog.blog.post.dto;

import java.util.List;

/**
 * 공개 글 목록 한 페이지.
 *
 * 전체 개수를 같이 준다. 화면이 마지막 페이지를 알아야 페이지 번호를 그릴 수 있고,
 * "총 N개" 도 보여 줄 수 있다.
 */
public record PostListResponse(
        List<PostSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PostListResponse of(
            List<PostSummaryResponse> items,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = size < 1 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new PostListResponse(items, page, size, totalElements, totalPages);
    }
}
