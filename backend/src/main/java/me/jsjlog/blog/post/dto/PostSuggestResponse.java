package me.jsjlog.blog.post.dto;

/**
 * 검색창 아래에 뜨는 한 줄.
 *
 * <p>목록 카드({@link PostSummaryResponse})와 달리 썸네일·날짜·조회수가 없다. 타자를 칠 때마다
 * 오가는 응답이라 화면이 실제로 그리는 것만 담는다.</p>
 */
public record PostSuggestResponse(
        Long id,
        String title,
        String categoryName
) {
}
