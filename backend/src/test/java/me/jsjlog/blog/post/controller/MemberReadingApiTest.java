package me.jsjlog.blog.post.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostView;
import me.jsjlog.blog.post.dto.ReadingPositionRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostReadPositionRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.repository.PostViewRepository;
import me.jsjlog.blog.post.service.MemberReadingService;
import me.jsjlog.blog.post.service.PostViewRecorder;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 본인의 최근 본 글과 읽던 자리.
 *
 * <p>같은 회원·같은 글의 자리를 동시에 저장하는 경로는 MySQL 문법을 쓴다. 그래서 MySQL 검사
 * ({@code mysqlTest})에서도 돈다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberReadingApiTest {

    private static final String RECENT = "/api/v1/auth/me/recent-posts";
    private static final String VIEWED = "/api/v1/auth/me/viewed-posts";
    private static final String POSITION = "/api/v1/auth/me/reading-positions/{postId}";
    private static final LocalDateTime BASE = LocalDateTime.now().minusDays(1).withNano(0);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry, "member_reading");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private PostRepository postRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private PostReadPositionRepository positionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberReadingService memberReadingService;
    @Autowired private PostViewRecorder postViewRecorder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Post first;
    private Post second;
    private Post hidden;
    private Post deleted;
    private Member reader;
    private Member other;

    @BeforeEach
    void setUp() {
        postViewRepository.deleteAll();
        positionRepository.deleteAll();

        Category category = categoryRepository.save(new Category("읽기 " + System.nanoTime(), 1L));
        first = published(category, "첫 글");
        second = published(category, "둘째 글");
        hidden = postRepository.saveAndFlush(new Post("공개 전 글", "요약", "본문", category, null, null));
        deleted = published(category, "지운 글");
        deleted.delete(BASE);
        deleted = postRepository.saveAndFlush(deleted);

        reader = member("읽는사람");
        other = member("다른사람");

        record(reader, first, BASE.plusMinutes(1));
        record(reader, second, BASE.plusMinutes(2));
        record(reader, first, BASE.plusMinutes(3));
        record(reader, hidden, BASE.plusMinutes(4));
        record(reader, deleted, BASE.plusMinutes(5));
        record(other, second, BASE.plusMinutes(6));
        record(null, first, BASE.plusMinutes(7));
    }

    @Test
    @DisplayName("로그인하지 않으면 읽기 기록 API 는 모두 401 이다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get(RECENT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(VIEWED).param("postIds", String.valueOf(first.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(POSITION, first.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(put(POSITION, first.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(positionJson(1, 0, "0a1b2c3d")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("최근 본 글은 내 기록만, 마지막으로 본 시각이 늦은 순서로, 지금 공개된 글만 준다")
    void recentPostsAreMineAndVisibleOnly() throws Exception {
        mockMvc.perform(get(RECENT).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].postId").value(first.getId()))
                .andExpect(jsonPath("$.data.items[0].title").value("첫 글"))
                .andExpect(jsonPath("$.data.items[0].lastViewedAt").exists())
                .andExpect(jsonPath("$.data.items[1].postId").value(second.getId()));

        mockMvc.perform(get(RECENT).param("page", "1").param("size", "1")
                        .with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].postId").value(second.getId()));

        mockMvc.perform(get(RECENT).with(user(MemberPrincipal.ofSocial(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].postId").value(second.getId()));
    }

    @Test
    @DisplayName("카드용 일괄 조회는 내가 본 공개 글의 마지막 시각만 주고, 한 번에 50개를 넘기면 400 이다")
    void viewedPostsInOneRequest() throws Exception {
        mockMvc.perform(get(VIEWED)
                        .param("postIds", first.getId() + "," + second.getId() + "," + hidden.getId() + ",999999")
                        .with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        assertThat(memberReadingService.getViewedPosts(reader.getId(), List.of(first.getId())))
                .singleElement()
                .satisfies(viewed -> assertThat(viewed.lastViewedAt().toLocalDateTime())
                        .isEqualTo(BASE.plusMinutes(3)));

        String tooMany = LongStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(get(VIEWED).param("postIds", tooMany).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(VIEWED).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("기존 기록은 최초 시각으로 표시하고, 재방문한 글만 최신 시각과 순서로 바뀐다")
    void legacyRecordsRemainReadableAndUpdateOnRevisit() throws Exception {
        jdbcTemplate.update("update post_view set last_viewed_at = null where member_id = ?", reader.getId());

        assertThat(memberReadingService.getViewedPosts(reader.getId(), List.of(first.getId())))
                .singleElement().satisfies(item -> assertThat(item.lastViewedAt().toLocalDateTime())
                        .isEqualTo(BASE.plusMinutes(3)));
        mockMvc.perform(get(RECENT).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(first.getId()));

        postViewRecorder.updateLastViewedAt(second.getId(), reader.getId(), BASE.plusMinutes(8));
        postViewRecorder.updateLastViewedAt(hidden.getId(), reader.getId(), BASE.plusMinutes(9));
        postViewRecorder.updateLastViewedAt(deleted.getId(), reader.getId(), BASE.plusMinutes(10));

        mockMvc.perform(get(RECENT).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].postId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].postId").value(first.getId()));
        assertThat(memberReadingService.getViewedPosts(reader.getId(), List.of(second.getId())))
                .singleElement().satisfies(item -> assertThat(item.lastViewedAt().toLocalDateTime())
                        .isEqualTo(BASE.plusMinutes(8)));
        assertThat(memberReadingService.getViewedPosts(other.getId(), List.of(second.getId())))
                .singleElement().satisfies(item -> assertThat(item.lastViewedAt().toLocalDateTime())
                        .isEqualTo(BASE.plusMinutes(6)));
        assertThat(memberReadingService.getViewedPosts(reader.getId(), List.of(hidden.getId(), deleted.getId())))
                .isEmpty();
    }

    @Test
    @DisplayName("읽던 자리는 저장한 본인만 보고, 다시 저장하면 한 줄을 고친다")
    void readingPositionIsPersonal() throws Exception {
        mockMvc.perform(get(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        savePosition(reader, first, 3, 250, "0a1b2c3d");
        savePosition(reader, first, 7, 900, "ffee0011");

        mockMvc.perform(get(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockIndex").value(7))
                .andExpect(jsonPath("$.data.blockOffset").value(900))
                .andExpect(jsonPath("$.data.blockHash").value("ffee0011"))
                .andExpect(jsonPath("$.data.savedAt").exists());
        assertThat(positionRepository.count()).isEqualTo(1);

        mockMvc.perform(get(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("공개되지 않은 글과 지운 글은 자리를 읽지도 쓰지도 못한다")
    void hiddenPostPositionIsNotFound() throws Exception {
        for (Post post : List.of(hidden, deleted)) {
            mockMvc.perform(get(POSITION, post.getId()).with(user(MemberPrincipal.ofSocial(reader))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(POSITION, post.getId()).with(user(MemberPrincipal.ofSocial(reader))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(positionJson(1, 0, "0a1b2c3d")))
                    .andExpect(status().isNotFound());
        }
        assertThat(positionRepository.count()).isZero();
    }

    @Test
    @DisplayName("자리 값이 범위를 벗어나거나 CSRF 토큰이 없으면 저장하지 않는다")
    void invalidPositionIsRejected() throws Exception {
        mockMvc.perform(put(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(reader))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(positionJson(1, 1001, "0a1b2c3d")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(reader))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(positionJson(1, 10, "NOT-HEX!")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(POSITION, first.getId()).with(user(MemberPrincipal.ofSocial(reader)))
                        .contentType(MediaType.APPLICATION_JSON).content(positionJson(1, 10, "0a1b2c3d")))
                .andExpect(status().isForbidden());

        assertThat(positionRepository.count()).isZero();
    }

    @Test
    @DisplayName("창 여러 개에서 같은 글의 자리를 동시에 저장해도 실패 없이 한 줄만 남는다")
    void concurrentSavesKeepOneRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                int blockIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    memberReadingService.saveReadingPosition(reader.getId(), second.getId(),
                            new ReadingPositionRequest(blockIndex, 100, "0a1b2c3d"));
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

        assertThat(positionRepository.findAll())
                .singleElement()
                .satisfies(position -> assertThat(position.getPostId()).isEqualTo(second.getId()));
    }

    private Post published(Category category, String title) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(BASE.minusDays(1));
        return postRepository.saveAndFlush(post);
    }

    private Member member(String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.GOOGLE, "reading-" + System.nanoTime(), nickname, null, null));
    }

    private void record(Member member, Post post, LocalDateTime viewedAt) {
        postViewRepository.saveAndFlush(PostView.of(post.getId(), member == null ? null : member.getId(), viewedAt));
    }

    private void savePosition(Member member, Post post, int blockIndex, int blockOffset, String hash) throws Exception {
        mockMvc.perform(put(POSITION, post.getId()).with(user(MemberPrincipal.ofSocial(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(positionJson(blockIndex, blockOffset, hash)))
                .andExpect(status().isOk());
    }

    private static String positionJson(int blockIndex, int blockOffset, String hash) {
        return """
                {"blockIndex": %d, "blockOffset": %d, "blockHash": "%s"}
                """.formatted(blockIndex, blockOffset, hash);
    }
}
