package me.jsjlog.blog.admin.service;

import me.jsjlog.blog.admin.dto.AdminDailyViewResponse;
import me.jsjlog.blog.admin.dto.AdminDeviceViewResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewStatsListResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewStatsResponse;
import me.jsjlog.blog.admin.dto.AdminViewStatsResponse;
import me.jsjlog.blog.admin.repository.AdminPostViewQueryRepository;
import me.jsjlog.blog.admin.repository.AdminViewStatsQueryRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostReaction;
import me.jsjlog.blog.post.domain.PostReactionType;
import me.jsjlog.blog.post.domain.PostView;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostReactionRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.repository.PostViewRepository;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 조회 통계의 기간 경계와 집계.
 *
 * <p>"오늘" 을 고정하려고 시계를 넣은 서비스를 직접 만든다. 저장 시각은 운영과 같은 방식
 * ({@code hibernate.jdbc.time_zone=Asia/Seoul})으로 쓰게 해서, 서버 시간대가 UTC 인 CI 에서도
 * 서울 날짜로 묶이는지 본다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminViewStatsTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 서울 2026-10-17 12:00 */
    private static final Clock NOON_OCT_17 = Clock.fixed(
            ZonedDateTime.of(2026, 10, 17, 12, 0, 0, 0, SEOUL).toInstant(), ZoneId.systemDefault());

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry, "view_stats");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "Asia/Seoul");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminViewStatsQueryRepository statsQueryRepository;
    @Autowired private AdminPostViewQueryRepository postViewQueryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PostReactionRepository reactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private AdminViewStatsService service;
    private Post popular;
    private Post quiet;
    private Member first;
    private Member second;
    private Member admin;

    @BeforeEach
    void setUp() {
        postViewRepository.deleteAll();
        service = new AdminViewStatsService(statsQueryRepository, postViewQueryRepository, NOON_OCT_17);

        Category category = categoryRepository.save(new Category("통계 " + System.nanoTime(), 1L));
        popular = published(category, "많이 본 글", 120);
        quiet = published(category, "조용한 글", 5);
        first = member("첫독자");
        second = member("둘째독자");
        admin = memberRepository.save(Member.ofLocalAdmin("stats-admin-" + System.nanoTime(), "hash", "블로그주인"));

        // 7일 경계 바깥과 안쪽
        record(popular, first, seoul(10, 10, 23, 59), DeviceType.MOBILE);
        record(popular, first, seoul(10, 11, 0, 0), DeviceType.MOBILE);
        record(popular, first, seoul(10, 12, 9, 0), DeviceType.DESKTOP);
        record(popular, second, seoul(10, 12, 21, 0), DeviceType.TABLET);
        record(quiet, null, seoul(10, 15, 8, 0), DeviceType.DESKTOP);
        record(quiet, null, seoul(10, 15, 8, 40), DeviceType.UNKNOWN);
        record(popular, admin, seoul(10, 16, 10, 0), DeviceType.DESKTOP);
        record(popular, null, seoul(10, 17, 23, 59), DeviceType.MOBILE);
        record(popular, null, seoul(10, 18, 0, 0), DeviceType.MOBILE);
        // 30일 경계 바깥과 안쪽
        record(quiet, second, seoul(9, 17, 23, 59), DeviceType.DESKTOP);
        record(quiet, second, seoul(9, 18, 0, 0), DeviceType.DESKTOP);

        commentRepository.save(new Comment(popular, null, first, "남아 있는 댓글"));
        Comment removed = new Comment(popular, null, second, "지운 댓글");
        removed.delete();
        commentRepository.save(removed);
        reactionRepository.save(new PostReaction(popular, first, PostReactionType.LIKE));
        reactionRepository.save(new PostReaction(popular, second, PostReactionType.DISLIKE));
    }

    @Test
    @DisplayName("최근 7일은 서울 자정 기준 오늘 포함 7일이고, 관리자 본인 조회는 빼고 따로 센다")
    void sevenDaySummary() {
        AdminViewStatsResponse summary = service.getSummary(7);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(summary.to()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(summary.pageViews()).isEqualTo(6);
        assertThat(summary.memberViews()).isEqualTo(3);
        assertThat(summary.anonymousViews()).isEqualTo(3);
        assertThat(summary.uniqueMembers()).isEqualTo(2);
        assertThat(summary.excludedAdminViews()).isEqualTo(1);
        assertThat(summary.recordingStartedAt()).isNotNull();

        assertThat(summary.daily()).extracting(AdminDailyViewResponse::date)
                .containsExactly(
                        LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16),
                        LocalDate.of(2026, 10, 17));
        assertThat(summary.daily()).extracting(AdminDailyViewResponse::pageViews)
                .containsExactly(1L, 2L, 0L, 0L, 2L, 0L, 1L);
        assertThat(summary.daily().get(4).anonymousViews()).isEqualTo(2);

        assertThat(summary.devices()).extracting(AdminDeviceViewResponse::deviceType)
                .containsExactly(DeviceType.MOBILE, DeviceType.TABLET, DeviceType.DESKTOP, DeviceType.UNKNOWN);
        assertThat(summary.devices()).extracting(AdminDeviceViewResponse::pageViews)
                .containsExactly(2L, 1L, 2L, 1L);
    }

    @Test
    @DisplayName("최근 30일은 9월 18일 0시부터 센다")
    void thirtyDaySummary() {
        AdminViewStatsResponse summary = service.getSummary(30);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(summary.daily()).hasSize(30);
        assertThat(summary.pageViews()).isEqualTo(8);
        assertThat(summary.daily().getFirst().pageViews()).isEqualTo(1);
    }

    @Test
    @DisplayName("인기 글은 기간 기록이 많은 순이고, 누적 조회수와 댓글·좋아요는 지금 값으로 붙는다")
    void popularPosts() {
        AdminPostViewStatsListResponse posts = service.getPosts(7, 0, 10);

        assertThat(posts.totalElements()).isEqualTo(2);
        assertThat(posts.items()).extracting(AdminPostViewStatsResponse::postId)
                .containsExactly(popular.getId(), quiet.getId());

        AdminPostViewStatsResponse top = posts.items().getFirst();
        assertThat(top.pageViews()).isEqualTo(4);
        assertThat(top.uniqueMembers()).isEqualTo(2);
        assertThat(top.anonymousViews()).isEqualTo(1);
        assertThat(top.totalViews()).isEqualTo(120);
        assertThat(top.commentCount()).isEqualTo(1);
        assertThat(top.likeCount()).isEqualTo(1);

        AdminPostViewStatsResponse next = posts.items().get(1);
        assertThat(next.pageViews()).isEqualTo(2);
        assertThat(next.uniqueMembers()).isZero();
        assertThat(next.commentCount()).isZero();

        assertThat(service.getPosts(7, 1, 1).items()).extracting(AdminPostViewStatsResponse::postId)
                .containsExactly(quiet.getId());
    }

    @Test
    @DisplayName("기록이 없는 기간은 0 과 빈 목록이고, 7·30 이 아닌 기간은 받지 않는다")
    void emptyAndInvalidPeriod() {
        AdminViewStatsService later = new AdminViewStatsService(statsQueryRepository, postViewQueryRepository,
                Clock.fixed(ZonedDateTime.of(2027, 1, 1, 9, 0, 0, 0, SEOUL).toInstant(), ZoneId.systemDefault()));

        AdminViewStatsResponse summary = later.getSummary(7);
        assertThat(summary.pageViews()).isZero();
        assertThat(summary.uniqueMembers()).isZero();
        assertThat(summary.daily()).hasSize(7).allSatisfy(day -> assertThat(day.pageViews()).isZero());
        assertThat(summary.devices()).allSatisfy(device -> assertThat(device.pageViews()).isZero());

        AdminPostViewStatsListResponse posts = later.getPosts(30, 0, 10);
        assertThat(posts.items()).isEmpty();
        assertThat(posts.totalPages()).isZero();

        assertThatThrownBy(() -> service.getSummary(14))
                .isInstanceOfSatisfying(BlogException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("통계 API 는 관리자만 본다. 비로그인 401, 일반 회원 403, 기간이 틀리면 400 이다")
    void apiPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/admin/blog/stats/views")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/blog/stats/views/posts").with(user(MemberPrincipal.ofSocial(first))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/blog/stats/views").param("days", "30")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.daily.length()").value(30))
                .andExpect(jsonPath("$.data.devices.length()").value(4));
        mockMvc.perform(get("/api/v1/admin/blog/stats/views/posts").param("days", "7")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7));
        mockMvc.perform(get("/api/v1/admin/blog/stats/views").param("days", "14")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isBadRequest());
    }

    private Post published(Category category, String title, long views) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(LocalDateTime.of(2026, 9, 1, 9, 0));
        post = postRepository.saveAndFlush(post);
        // 배포 전부터 쌓인 누적 조회수를 흉내 낸다. 기록 표와 상관없는 값이다
        jdbcTemplate.update("update post set views = ? where id = ?", views, post.getId());
        return post;
    }

    private Member member(String nickname) {
        return memberRepository.save(Member.ofSocial(AuthProvider.KAKAO, "stats-" + System.nanoTime(), nickname,
                null, null));
    }

    private void record(Post post, Member member, LocalDateTime viewedAt, DeviceType deviceType) {
        postViewRepository.saveAndFlush(PostView.of(post.getId(), member == null ? null : member.getId(), viewedAt,
                deviceType));
    }

    /** 서울 시각을 서버 시간대의 시각으로. 운영 코드가 저장하는 값과 같은 꼴이다 */
    private static LocalDateTime seoul(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, SEOUL)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
