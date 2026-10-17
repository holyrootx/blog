package me.jsjlog.blog.post.service;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostView;
import me.jsjlog.blog.post.repository.CategoryRepository;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 글 상세가 조회 기록을 언제 남기는지.
 *
 * <p>기록은 상세 트랜잭션과 따로 커밋되므로 테스트 트랜잭션으로 되돌릴 수 없다. 다른 테스트에
 * 줄이 번지지 않게 DB 를 나누고, 테스트마다 새 글을 만들어 그 글의 줄만 센다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostViewRecordTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry, "post_view_record");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private PostService postService;
    @Autowired private PostViewService postViewService;
    @Autowired private PostViewRecorder postViewRecorder;
    @Autowired private PostRepository postRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(new Category("조회 기록 " + System.nanoTime(), 1L));
    }

    @Test
    @DisplayName("비회원이 같은 세션에서 새로고침해도 기록은 한 줄, 조회수도 1이다")
    void anonymousRefreshRecordsOnce() throws Exception {
        Post post = publishedPost();
        MockHttpSession session = new MockHttpSession();

        view(post, session, null).andExpect(jsonPath("$.data.views").value(1));
        view(post, session, null).andExpect(jsonPath("$.data.views").value(1));

        List<PostView> views = viewsOf(post);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().getMemberId()).isNull();
    }

    @Test
    @DisplayName("서로 다른 세션은 각각 기록하고 조회수에도 각각 반영한다")
    void differentSessionsRecordSeparately() throws Exception {
        Post post = publishedPost();

        view(post, new MockHttpSession(), null);
        view(post, new MockHttpSession(), null).andExpect(jsonPath("$.data.views").value(2));

        assertThat(viewsOf(post)).hasSize(2);
    }

    @Test
    @DisplayName("비회원으로 보다가 로그인하면 회원 기록이 따로 생기고, 앞선 비회원 기록은 회원에게 붙지 않는다")
    void signInAfterAnonymousView() throws Exception {
        Post post = publishedPost();
        Member reader = member("로그인독자");
        MockHttpSession session = new MockHttpSession();

        view(post, session, null);
        view(post, session, reader).andExpect(jsonPath("$.data.views").value(1));

        assertThat(viewsOf(post))
                .extracting(PostView::getMemberId)
                .containsExactlyInAnyOrder(null, reader.getId());
    }

    @Test
    @DisplayName("같은 세션에서 계정을 바꾸면 요청마다 그때의 회원으로 기록한다")
    void accountSwitchInSameSession() throws Exception {
        Post post = publishedPost();
        Member first = member("첫계정");
        Member second = member("둘째계정");
        MockHttpSession session = new MockHttpSession();

        view(post, session, first);
        view(post, session, second);
        view(post, session, first);

        assertThat(viewsOf(post))
                .extracting(PostView::getMemberId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("브라우저 정보는 저장하지 않고 기기 분류로만 남기며, 검색 봇은 봇으로 남긴다")
    void deviceTypeFromRequestHeaders() throws Exception {
        Post post = publishedPost();

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", post.getId()).session(new MockHttpSession())
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/blog/posts/{postId}", post.getId()).session(new MockHttpSession())
                        .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
                .andExpect(status().isOk());

        assertThat(viewsOf(post))
                .extracting(PostView::getDeviceType)
                .containsExactlyInAnyOrder(DeviceType.MOBILE, DeviceType.BOT);
    }

    @Test
    @DisplayName("공개되지 않은 글과 없는 글은 기록하지 않는다")
    void unreadablePostIsNotRecorded() throws Exception {
        Post draft = postRepository.saveAndFlush(new Post("공개 전", "요약", "본문", category, null, null));
        long before = postViewRepository.count();

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", draft.getId()).session(new MockHttpSession()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/blog/posts/{postId}", Long.MAX_VALUE).session(new MockHttpSession()))
                .andExpect(status().isNotFound());

        assertThat(postViewRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("같은 세션의 동시 요청은 기록 한 줄, 조회수 1만 남긴다")
    void concurrentSameSessionRecordsOnce() throws Exception {
        Post post = publishedPost();
        MockHttpSession session = new MockHttpSession();

        runConcurrently(8, index -> postViewService.getPostDetail(post.getId(), session, null, DeviceType.UNKNOWN));

        assertThat(viewsOf(post)).hasSize(1);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViews()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 세션의 동시 요청은 모두 기록하고 조회수도 모두 반영한다")
    void concurrentDifferentSessionsAllRecorded() throws Exception {
        Post post = publishedPost();

        runConcurrently(8, index -> postViewService.getPostDetail(post.getId(), new MockHttpSession(), null, DeviceType.UNKNOWN));

        assertThat(viewsOf(post)).hasSize(8);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViews()).isEqualTo(8L);
    }

    @Test
    @DisplayName("기록 저장이 실패해도 글은 열리고, 다음 요청에서 다시 기록을 시도한다")
    void recordingFailureDoesNotBlockPost() throws Exception {
        Post post = publishedPost();
        MockHttpSession session = new MockHttpSession();
        jdbcTemplate.execute("alter table post_view add constraint post_view_test_reject"
                + " check (post_id <> " + post.getId() + ")");

        try {
            view(post, session, null).andExpect(jsonPath("$.data.title").value(post.getTitle()));
            assertThat(viewsOf(post)).isEmpty();
        } finally {
            jdbcTemplate.execute("alter table post_view drop constraint post_view_test_reject");
        }

        view(post, session, null).andExpect(jsonPath("$.data.views").value(1));
        assertThat(viewsOf(post)).hasSize(1);
    }

    @Test
    @DisplayName("같은 세션이라도 30분이 지나면 다시 기록한다. 조회수는 그대로 세션당 한 번이다")
    void recordsAgainAfterWindow() {
        Post post = publishedPost();
        MockHttpSession session = new MockHttpSession();
        MovableClock clock = new MovableClock(Instant.parse("2026-10-17T03:00:00Z"));
        PostViewService service = new PostViewService(postService, postViewRecorder, clock);

        service.getPostDetail(post.getId(), session, null, DeviceType.UNKNOWN);
        clock.advance(Duration.ofMinutes(10));
        service.getPostDetail(post.getId(), session, null, DeviceType.UNKNOWN);
        assertThat(viewsOf(post)).hasSize(1);

        clock.advance(Duration.ofMinutes(21));
        service.getPostDetail(post.getId(), session, null, DeviceType.UNKNOWN);

        assertThat(viewsOf(post))
                .extracting(PostView::getViewedAt)
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(2026, 10, 17, 12, 0),
                        LocalDateTime.of(2026, 10, 17, 12, 31)
                );
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViews()).isEqualTo(1L);
    }

    private Post publishedPost() {
        Post post = new Post("기록할 글 " + System.nanoTime(), "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusMinutes(1));
        return postRepository.saveAndFlush(post);
    }

    private Member member(String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.KAKAO, "view-" + System.nanoTime(), nickname, null, null));
    }

    private List<PostView> viewsOf(Post post) {
        return postViewRepository.findAll().stream()
                .filter(view -> view.getPostId().equals(post.getId()))
                .toList();
    }

    private org.springframework.test.web.servlet.ResultActions view(Post post, MockHttpSession session, Member member)
            throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/blog/posts/{postId}", post.getId()).session(session);
        if (member != null) {
            request.with(authentication(login(member)));
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private static Authentication login(Member member) {
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static void runConcurrently(int count, ThrowingTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                int current = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    task.run(current);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run(int index) throws Exception;
    }

    /** 서울 시간대로 고정한 시계. 저장된 시각을 사람이 읽는 값으로 바로 비교하려는 것이다 */
    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
