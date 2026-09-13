package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminDashboardResponse;
import me.jsjlog.blog.admin.repository.AdminDashboardQueryRepository;
import me.jsjlog.blog.post.domain.PostStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminDashboardService {

    /** 답변 대기 댓글은 목록 전체가 아니라 몇 건만 보여준다 */
    private static final long UNANSWERED_PREVIEW_SIZE = 3;

    private final AdminDashboardQueryRepository dashboardQueryRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return new AdminDashboardResponse(
                // BlogProfile 에 blogStartedAt 이 없어서 아직 셀 수 없다.
                // 0 으로 주면 "오늘 시작한 블로그"로 읽히므로 null 로 둔다
                null,
                dashboardQueryRepository.countPostsByStatus(PostStatus.DRAFT),
                dashboardQueryRepository.sumViews(),
                dashboardQueryRepository.countPostsByStatus(PostStatus.PUBLISHED),
                dashboardQueryRepository.countPostsCreatedSince(monthStart),
                dashboardQueryRepository.countUnansweredComments(),
                dashboardQueryRepository.getOldestUnansweredAt(),
                dashboardQueryRepository.getMostViewedCategoryName(),
                dashboardQueryRepository.getCategoryShares(),
                dashboardQueryRepository.getUnansweredComments(UNANSWERED_PREVIEW_SIZE)
        );
    }
}
