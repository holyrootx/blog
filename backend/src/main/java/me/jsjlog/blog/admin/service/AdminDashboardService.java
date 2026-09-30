package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminDashboardResponse;
import me.jsjlog.blog.admin.repository.AdminDashboardQueryRepository;
import me.jsjlog.blog.home.service.BlogProfileService;
import me.jsjlog.blog.post.domain.PostStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminDashboardQueryRepository dashboardQueryRepository;
    private final BlogProfileService blogProfileService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return new AdminDashboardResponse(
                blogProfileService.getDaysSinceStart(),
                dashboardQueryRepository.countPostsByStatus(PostStatus.DRAFT),
                dashboardQueryRepository.sumViews(),
                dashboardQueryRepository.countPostsByStatus(PostStatus.PUBLISHED),
                dashboardQueryRepository.countPostsCreatedSince(monthStart),
                dashboardQueryRepository.countReportedComments(),
                dashboardQueryRepository.getMostViewedCategoryName(),
                dashboardQueryRepository.getCategoryShares()
        );
    }
}
