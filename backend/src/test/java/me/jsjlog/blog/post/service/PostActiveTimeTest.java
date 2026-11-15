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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PostActiveTimeTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		MySqlTestDatabase.configure(registry, "post_active_time");
	}

	@Autowired private PostService postService;
	@Autowired private PostViewRecorder recorder;
	@Autowired private PostViewRepository views;
	@Autowired private PostRepository posts;
	@Autowired private CategoryRepository categories;
	@Autowired private MemberRepository members;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;

	private Post post;
	private MockHttpSession session;
	private MutableClock clock;
	private PostViewService service;

	@BeforeEach
	void setUp() {
		Category category = categories.save(new Category("활성 시간 " + System.nanoTime(), 1L));
		post = new Post("측정할 글", "요약", "본문", category, null, null);
		post.publish(LocalDateTime.of(2026, 1, 1, 0, 0));
		post = posts.saveAndFlush(post);
		session = new MockHttpSession();
		clock = new MutableClock();
		service = new PostViewService(postService, recorder, clock);
	}

	@Test
	void cumulativeRetriesAndConcurrentRequestsDoNotAddTwiceOrTouchReadingTime() throws Exception {
		Member member = members.save(Member.ofSocial(AuthProvider.KAKAO, "active-" + System.nanoTime(), "독자", null, null));
		service.getPostDetail(post.getId(), session, member.getId(), DeviceType.DESKTOP);
		PostView initial = records().getFirst();
		String token = service.startActiveTime(post.getId(), session, member.getId());
		clock.advance(Duration.ofSeconds(30));
		try (var pool = Executors.newFixedThreadPool(8)) {
			List<Callable<Void>> tasks = IntStream.rangeClosed(1, 8).mapToObj(index -> (Callable<Void>) () -> {
				service.recordActiveTime(post.getId(), session, member.getId(), token, index * 3000L);
				return null;
			}).toList();
			for (var future : pool.invokeAll(tasks)) {
				future.get();
			}
		}
		service.recordActiveTime(post.getId(), session, member.getId(), token, 24_000);
		service.recordActiveTime(post.getId(), session, member.getId(), token, 3_000);
		assertThat(records()).singleElement().satisfies(view -> {
			assertThat(view.getActiveTimeMillis()).isEqualTo(24_000);
			assertThat(view.getViewedAt()).isEqualTo(initial.getViewedAt());
			assertThat(view.getLastViewedAt()).isEqualTo(initial.getLastViewedAt());
		});
		assertThat(posts.findById(post.getId()).orElseThrow().getViews()).isEqualTo(1);
	}

	@Test
	void refreshMergesTimeAndOldPageStaysAttachedToItsOriginalRecord() {
		service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP);
		String first = service.startActiveTime(post.getId(), session, null);
		clock.advance(Duration.ofSeconds(15));
		service.recordActiveTime(post.getId(), session, null, first, 15_000);
		service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP);
		String refreshed = service.startActiveTime(post.getId(), session, null);
		clock.advance(Duration.ofSeconds(15));
		service.recordActiveTime(post.getId(), session, null, refreshed, 10_000);
		assertThat(records()).singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isEqualTo(25_000));
		Long originalId = records().getFirst().getId();

		clock.advance(Duration.ofMinutes(30));
		service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP);
		service.recordActiveTime(post.getId(), session, null, first, 20_000);
		assertThat(views.findById(originalId).orElseThrow().getActiveTimeMillis()).isEqualTo(30_000);
		assertThat(records().stream().filter(view -> !view.getId().equals(originalId)))
				.singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isNull());
		assertThat(posts.findById(post.getId()).orElseThrow().getViews()).isEqualTo(1);
	}

	@Test
	void anotherSessionAccountPostOrExpiredTokenCannotWrite() {
		service.getPostDetail(post.getId(), session, null, DeviceType.MOBILE);
		String token = service.startActiveTime(post.getId(), session, null);
		clock.advance(Duration.ofSeconds(15));
		service.recordActiveTime(post.getId(), new MockHttpSession(), null, token, 10_000);
		service.recordActiveTime(post.getId(), session, 999L, token, 10_000);
		service.recordActiveTime(Long.MAX_VALUE, session, null, token, 10_000);
		service.recordActiveTime(post.getId(), session, null, "unknown", 10_000);
		service.recordActiveTime(post.getId(), session, null, token, 1_000_000);
		clock.advance(Duration.ofHours(2));
		service.recordActiveTime(post.getId(), session, null, token, 10_000);
		assertThat(service.startActiveTime(post.getId(), session, null)).isNull();
		assertThat(records()).singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isNull());
	}

	@Test
	void failedPersistenceCanRetryWithoutLosingOrDuplicatingTime() {
		service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP);
		String token = service.startActiveTime(post.getId(), session, null);
		clock.advance(Duration.ofSeconds(15));
		jdbc.execute("alter table post_view add constraint active_time_reject check (post_id <> " + post.getId()
				+ " or active_time_millis is null)");
		try {
			assertThatThrownBy(() -> service.recordActiveTime(post.getId(), session, null, token, 15_000))
					.isInstanceOf(RuntimeException.class);
			assertThat(service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP).title()).isEqualTo(post.getTitle());
		} finally {
			jdbc.execute("alter table post_view drop constraint active_time_reject");
		}
		service.recordActiveTime(post.getId(), session, null, token, 15_000);
		service.recordActiveTime(post.getId(), session, null, token, 15_000);
		assertThat(records()).singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isEqualTo(15_000));
	}

	@Test
	void measurementsDoNotWriteToAnUnpublishedOrDeletedPost() {
		service.getPostDetail(post.getId(), session, null, DeviceType.DESKTOP);
		String token = service.startActiveTime(post.getId(), session, null);
		clock.advance(Duration.ofSeconds(15));
		jdbc.update("update post set status = 'PRIVATE' where id = ?", post.getId());
		service.recordActiveTime(post.getId(), session, null, token, 10_000);
		jdbc.update("update post set status = 'PUBLISHED', deleted_at = ? where id = ?", LocalDateTime.now(), post.getId());
		service.recordActiveTime(post.getId(), session, null, token, 15_000);
		assertThat(records()).singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isNull());
	}

	@Test
	void anonymousApiRequiresCsrfAndValidInputButNeverCreatesAView() throws Exception {
		String path = "/api/v1/blog/posts/" + post.getId() + "/active-time";
		mvc.perform(post(path).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
		assertThat(records()).isEmpty();
		mvc.perform(get("/api/v1/blog/posts/{id}", post.getId()).session(session)).andExpect(status().isOk());
		mvc.perform(post(path).session(session)).andExpect(status().isForbidden());
		String response = mvc.perform(post(path).session(session).with(csrf())).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = mapper.readTree(response).path("data").path("token").asText();
		String body = "{\"token\":\"" + token + "\",\"activeTimeMillis\":0}";
		mvc.perform(put(path).session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
		mvc.perform(put(path).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
		for (String invalid : List.of("-1", "7200001", "null")) {
			mvc.perform(put(path).session(session).with(csrf()).contentType("application/json")
					.content("{\"token\":\"" + token + "\",\"activeTimeMillis\":" + invalid + "}"))
					.andExpect(status().isBadRequest());
		}
		assertThat(records()).singleElement().satisfies(view -> assertThat(view.getActiveTimeMillis()).isZero());
	}

	@Test
	void adminsBotsAndUnviewedPostsReceiveNoMeasurement() throws Exception {
		assertThat(service.startActiveTime(post.getId(), session, null)).isNull();
		service.getPostDetail(post.getId(), session, null, DeviceType.BOT);
		assertThat(service.startActiveTime(post.getId(), session, null)).isNull();
		Member admin = members.save(Member.ofLocalAdmin("active-admin-" + System.nanoTime(), "hash", "관리자"));
		service.getPostDetail(post.getId(), session, admin.getId(), DeviceType.DESKTOP);
		mvc.perform(post("/api/v1/blog/posts/{id}/active-time", post.getId()).session(session).with(csrf())
					.with(user(MemberPrincipal.ofLocal(admin))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
	}

	private List<PostView> records() {
		return views.findAll().stream().filter(view -> view.getPostId().equals(post.getId())).toList();
	}

	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-11-14T14:00:00Z");
		void advance(Duration duration) { now = now.plus(duration); }
		@Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
