package me.jsjlog.blog.admin.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostView;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.repository.PostViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 글별 조회 기록.
 *
 * <p>기록 건수와 "기록 시작" 이 다른 테스트의 줄에 흔들리지 않게 DB 를 나누고 매번 비운다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPostViewApiTest {

    private static final String VIEWS = "/api/v1/admin/blog/posts/{postId}/views";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 17, 9, 0);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admin_post_view;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private PostRepository postRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;

    private Post post;
    private Member reader;
    private Member withdrawn;
    private Member admin;

    @BeforeEach
    void setUp() {
        postViewRepository.deleteAll();

        Category category = categoryRepository.save(new Category("기록 " + System.nanoTime(), 1L));
        post = new Post("조회 기록을 볼 글", "요약", "본문", category, null, null);
        post.publish(BASE.minusDays(1));
        post = postRepository.saveAndFlush(post);

        reader = memberRepository.save(Member.ofSocial(
                AuthProvider.GOOGLE, "reader-" + System.nanoTime(), "읽는사람", "reader@example.com", null));
        withdrawn = Member.ofSocial(
                AuthProvider.NAVER, "gone-" + System.nanoTime(), "떠난사람", "gone@example.com", null);
        withdrawn.withdraw(BASE);
        withdrawn = memberRepository.save(withdrawn);
        admin = memberRepository.save(Member.ofLocalAdmin("view-admin-" + System.nanoTime(), "hash", "블로그주인"));

        record(null, BASE);
        record(reader.getId(), BASE.plusMinutes(1));
        record(withdrawn.getId(), BASE.plusMinutes(2));
        record(admin.getId(), BASE.plusMinutes(3));
        record(null, BASE.plusMinutes(4));
    }

    @Test
    @DisplayName("로그인하지 않으면 401, 일반 회원이면 403 이다")
    void onlyAdminCanRead() throws Exception {
        mockMvc.perform(get(VIEWS, post.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(VIEWS, post.getId()).with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("최근 기록부터 페이지 단위로 주고, 누적 조회수와 기록 건수를 따로 준다")
    void newestFirstWithPaging() throws Exception {
        mockMvc.perform(get(VIEWS, post.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postTitle").value("조회 기록을 볼 글"))
                .andExpect(jsonPath("$.data.totalViews").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].viewerType").value("ANONYMOUS"))
                .andExpect(jsonPath("$.data.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].viewerType").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[1].nickname").value("블로그주인"))
                // 받는 쪽이 같은 순간으로 읽도록 시간대가 붙어 있어야 한다
                .andExpect(jsonPath("$.data.items[0].viewedAt").value(matchesPattern(".*(Z|[+-]\\d{2}:\\d{2})$")))
                .andExpect(jsonPath("$.data.recordingStartedAt").exists());
    }

    @Test
    @DisplayName("구분별 건수를 주고, 회원·비회원·관리자로 거를 수 있다")
    void countsAndFilters() throws Exception {
        mockMvc.perform(get(VIEWS, post.getId())
                        .param("viewer", "MEMBER")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.all").value(5))
                .andExpect(jsonPath("$.data.counts.member").value(2))
                .andExpect(jsonPath("$.data.counts.admin").value(1))
                .andExpect(jsonPath("$.data.counts.anonymous").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].memberId").value(withdrawn.getId()))
                .andExpect(jsonPath("$.data.items[1].memberId").value(reader.getId()));

        mockMvc.perform(get(VIEWS, post.getId())
                        .param("viewer", "ANONYMOUS")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].memberId").doesNotExist());
    }

    @Test
    @DisplayName("탈퇴 회원은 남은 닉네임과 탈퇴 상태로 보이고, 이메일은 어떤 회원도 내보내지 않는다")
    void withdrawnMemberIsMarkedWithoutPersonalData() throws Exception {
        mockMvc.perform(get(VIEWS, post.getId())
                        .param("viewer", "MEMBER")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].nickname").value("떠난사람"))
                .andExpect(jsonPath("$.data.items[0].memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.items[0].memberStatusName").exists())
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[1].email").doesNotExist());
    }

    @Test
    @DisplayName("없는 글은 404 이고, 기록이 없는 글은 빈 목록과 0 건이다")
    void missingAndEmpty() throws Exception {
        mockMvc.perform(get(VIEWS, Long.MAX_VALUE).with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isNotFound());

        Post quiet = new Post("아무도 안 본 글", "요약", "본문", post.getCategory(), null, null);
        quiet = postRepository.saveAndFlush(quiet);

        mockMvc.perform(get(VIEWS, quiet.getId()).with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.counts.all").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    private void record(Long memberId, LocalDateTime viewedAt) {
        postViewRepository.saveAndFlush(PostView.of(post.getId(), memberId, viewedAt));
    }
}
