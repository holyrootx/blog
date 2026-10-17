package me.jsjlog.blog.admin.service;

import me.jsjlog.blog.admin.dto.AdminDailyViewResponse;
import me.jsjlog.blog.admin.dto.AdminDeviceViewResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewStatsListResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewStatsResponse;
import me.jsjlog.blog.admin.dto.AdminViewStatsResponse;
import me.jsjlog.blog.admin.repository.AdminPostViewQueryRepository;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository.DailyCount;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository.PostCount;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository.PostInfo;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository.Totals;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.DeviceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 조회 통계.
 *
 * <p>날짜 경계는 서울 자정이다. 서버 시간대가 무엇이든 "오늘" 과 "최근 7일" 이 관리자가 사는 날짜와
 * 같아야 한다. 경계 시각은 서울 자정을 서버 시간대의 시각으로 옮겨 쿼리에 넘긴다 — 저장된 시각이
 * 서버 시간대의 값이기 때문이다.</p>
 *
 * <p>배포 전 누적 조회수를 기간별 기록으로 나누지 않는다. 그 값은 언제 쌓였는지 모른다.</p>
 */
@Service
public class AdminViewStatsService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30);
    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_POST_SIZE = 10;
    private static final int MAX_POST_SIZE = 50;

    private final AdminViewStatsQueryRepository statsQueryRepository;
    private final AdminPostViewQueryRepository postViewQueryRepository;
    private final Clock clock;

    @Autowired
    public AdminViewStatsService(
            AdminViewStatsQueryRepository statsQueryRepository,
            AdminPostViewQueryRepository postViewQueryRepository
    ) {
        this(statsQueryRepository, postViewQueryRepository, Clock.systemDefaultZone());
    }

    AdminViewStatsService(
            AdminViewStatsQueryRepository statsQueryRepository,
            AdminPostViewQueryRepository postViewQueryRepository,
            Clock clock
    ) {
        this.statsQueryRepository = statsQueryRepository;
        this.postViewQueryRepository = postViewQueryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminViewStatsResponse getSummary(Integer days) {
        Period period = period(days);
        Totals totals = statsQueryRepository.totals(period.from(), period.to());
        Map<LocalDate, DailyCount> daily = statsQueryRepository.daily(period.from(), period.to()).stream()
                .collect(Collectors.toMap(DailyCount::date, Function.identity()));
        Map<DeviceType, Long> devices = statsQueryRepository.devices(period.from(), period.to());

        List<AdminDailyViewResponse> dailyViews = new ArrayList<>();
        for (LocalDate date = period.firstDate(); !date.isAfter(period.lastDate()); date = date.plusDays(1)) {
            DailyCount count = daily.get(date);
            long pageViews = count == null ? 0 : count.pageViews();
            long memberViews = count == null ? 0 : count.memberViews();
            dailyViews.add(new AdminDailyViewResponse(date, pageViews, memberViews, pageViews - memberViews));
        }

        return new AdminViewStatsResponse(
                period.days(),
                period.firstDate(),
                period.lastDate(),
                postViewQueryRepository.findRecordingStartedAt(),
                totals.pageViews(),
                totals.memberViews(),
                totals.pageViews() - totals.memberViews(),
                totals.uniqueMembers(),
                statsQueryRepository.adminViews(period.from(), period.to()),
                dailyViews,
                Arrays.stream(DeviceType.values())
                        .map(type -> new AdminDeviceViewResponse(type, devices.getOrDefault(type, 0L)))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AdminPostViewStatsListResponse getPosts(Integer days, Integer page, Integer size) {
        Period period = period(days);
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? DEFAULT_POST_SIZE : Math.min(size, MAX_POST_SIZE);

        List<PostCount> counts = statsQueryRepository.postCounts(period.from(), period.to(), pageNumber, pageSize);
        long total = statsQueryRepository.countPosts(period.from(), period.to());
        List<Long> postIds = counts.stream().map(PostCount::postId).toList();

        Map<Long, PostInfo> posts = postIds.isEmpty() ? Map.of() : statsQueryRepository.postInfos(postIds);
        Map<Long, Long> comments = postIds.isEmpty() ? Map.of() : statsQueryRepository.commentCounts(postIds);
        Map<Long, Long> likes = postIds.isEmpty() ? Map.of() : statsQueryRepository.likeCounts(postIds);

        List<AdminPostViewStatsResponse> items = counts.stream()
                .map(count -> {
                    PostInfo post = posts.get(count.postId());
                    return new AdminPostViewStatsResponse(
                            count.postId(),
                            post == null ? null : post.title(),
                            post == null ? null : post.status(),
                            post == null || post.deleted(),
                            count.pageViews(),
                            count.uniqueMembers(),
                            count.pageViews() - count.memberViews(),
                            post == null ? 0 : post.totalViews(),
                            comments.getOrDefault(count.postId(), 0L),
                            likes.getOrDefault(count.postId(), 0L)
                    );
                })
                .toList();

        return new AdminPostViewStatsListResponse(
                period.days(),
                items,
                pageNumber,
                pageSize,
                total,
                total == 0 ? 0 : (int) Math.ceil((double) total / pageSize)
        );
    }

    /** 오늘을 포함한 서울 날짜 {@code days} 일. 7일과 30일만 받는다 — 화면이 그 둘만 고른다 */
    private Period period(Integer days) {
        int length = days == null ? DEFAULT_DAYS : days;
        if (!ALLOWED_DAYS.contains(length)) {
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        LocalDate first = today.minusDays(length - 1L);

        return new Period(length, first, today, serverTime(first), serverTime(today.plusDays(1)));
    }

    private LocalDateTime serverTime(LocalDate seoulDate) {
        return seoulDate.atStartOfDay(SEOUL).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private record Period(int days, LocalDate firstDate, LocalDate lastDate, LocalDateTime from, LocalDateTime to) {
    }
}
