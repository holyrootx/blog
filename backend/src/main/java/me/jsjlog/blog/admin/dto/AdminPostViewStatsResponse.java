package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.PostStatus;

/**
 * 기간 안에 기록이 있는 글 한 줄.
 *
 * <p>기간 값과 지금 값이 섞여 있다. {@code pageViews}·{@code uniqueMembers}·{@code anonymousViews} 는
 * 기간 안의 조회 기록이고, {@code totalViews}(세션당 누적 조회수)·{@code commentCount}·{@code likeCount} 는
 * 기간과 상관없는 지금의 값이다. 댓글·좋아요가 그 기간에 생겼다는 기록은 따로 없다.</p>
 */
public record AdminPostViewStatsResponse(
        Long postId,
        String title,
        PostStatus status,
        boolean deleted,
        long pageViews,
        long uniqueMembers,
        long anonymousViews,
        long totalViews,
        long commentCount,
        long likeCount
) {
}
