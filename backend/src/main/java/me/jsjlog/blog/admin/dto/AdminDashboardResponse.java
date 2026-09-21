package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대시보드.
 *
 * 만들 수 없는 위젯(오늘 방문·방문 그래프·구독자)은 0으로 내려주지 않고 아예 빼둔다.
 * 0으로 주면 진짜 0인지 미구현인지 구분할 수 없다.
 */
public record AdminDashboardResponse(
        Long daysSinceStart,
        Long draftPostCount,
        Long totalViews,
        Long publishedPostCount,
        Long postCountThisMonth,
        Long unansweredCommentCount,
        LocalDateTime oldestUnansweredAt,
        String mostViewedCategory,
        List<AdminCategoryShareResponse> categoryShares,
        List<AdminUnansweredCommentResponse> unansweredComments
) {
}
