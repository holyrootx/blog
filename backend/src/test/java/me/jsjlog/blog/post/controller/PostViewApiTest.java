package me.jsjlog.blog.post.controller;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostViewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(new Category("조회수 테스트", 300L));
    }

    @Test
    @DisplayName("같은 세션에서 같은 글을 다시 조회해도 조회수는 한 번만 증가한다")
    void sameSessionCountsOnce() throws Exception {
        Post post = savePublishedPost("같은 세션 글");
        MockHttpSession session = new MockHttpSession();

        getPost(post, session, 1);
        getPost(post, session, 1);

        entityManager.clear();
        org.assertj.core.api.Assertions.assertThat(
                postRepository.findById(post.getId()).orElseThrow().getViews()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName("서로 다른 세션에서 조회하면 각각 조회수에 반영한다")
    void differentSessionsCountSeparately() throws Exception {
        Post post = savePublishedPost("다른 세션 글");

        getPost(post, new MockHttpSession(), 1);
        getPost(post, new MockHttpSession(), 2);
    }

    @Test
    @DisplayName("조회수 증가는 인기글 순서와 관리자 누적 조회수에 반영된다")
    void viewsAffectPopularPostsAndDashboardTotal() throws Exception {
        Post first = savePublishedPost("한 번 조회한 글");
        Post popular = savePublishedPost("두 번 조회한 글");

        getPost(first, new MockHttpSession(), 1);
        getPost(popular, new MockHttpSession(), 1);
        getPost(popular, new MockHttpSession(), 2);

        mockMvc.perform(get("/api/v1/blog/home/posts")
                        .param("sort", "popular")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(popular.getId()))
                .andExpect(jsonPath("$.data[0].views").value(2))
                .andExpect(jsonPath("$.data[1].id").value(first.getId()))
                .andExpect(jsonPath("$.data[1].views").value(1));

        Member admin = memberRepository.save(Member.ofLocalAdmin(
                "post-view-admin",
                "encoded-password",
                "조회수 관리자"
        ));

        mockMvc.perform(get("/api/v1/admin/blog/dashboard")
                        .with(user(MemberPrincipal.ofLocal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalViews").value(3));
    }

    @Test
    @DisplayName("공개되지 않은 글의 실패한 조회는 세션 조회 이력에 남지 않는다")
    void failedViewIsNotRecorded() throws Exception {
        Post post = new Post("공개 전 글", "요약", "본문", category, null, null);
        post = postRepository.save(post);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", post.getId())
                        .session(session))
                .andExpect(status().isNotFound());

        post.publish(LocalDateTime.now());
        postRepository.saveAndFlush(post);

        getPost(post, session, 1);
    }

    private Post savePublishedPost(String title) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now());
        return postRepository.saveAndFlush(post);
    }

    private void getPost(Post post, MockHttpSession session, long expectedViews) throws Exception {
        mockMvc.perform(get("/api/v1/blog/posts/{postId}", post.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.views").value(expectedViews));
    }
}
