package me.jsjlog.blog.post.controller;

import java.time.LocalDateTime;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 글 목록.
 *
 * <p>가장 중요한 것은 <b>안 보여야 할 글이 안 보이는 것</b>이다. 임시저장·비공개·예약 글이
 * 목록에 새면 발행 전 원고가 그대로 노출된다. 조회조건으로 상태를 받지 않는 이유도 그것이다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostListApiTest {

    private static final String POSTS = "/api/v1/blog/posts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long openCategoryId;

    @BeforeEach
    void seed() {
        postRepository.deleteAll();

        Category open = category("공개목록", 800L);
        Category other = category("다른분류", 801L);

        openCategoryId = open.getId();

        save("공개된 글 A", open, LocalDateTime.now().minusDays(2), true);
        save("공개된 글 B", open, LocalDateTime.now().minusDays(1), true);
        save("다른 분류 글", other, LocalDateTime.now().minusDays(3), true);

        // 아래 셋은 목록에 나오면 안 된다
        save("임시저장 글", open, null, false);
        savePublishedAt("예약된 글", open, LocalDateTime.now().plusDays(7));
        saveUnpublished("내린 글", open);
    }

    /** 이름에 unique 가 걸려 있어 테스트마다 새로 만들면 충돌한다. 있으면 그대로 쓴다 */
    private Category category(String name, long sortOrder) {
        return categoryRepository.findAll().stream()
                .filter(found -> name.equals(found.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(name, sortOrder)));
    }

    private void save(String title, Category category, LocalDateTime publishedAt, boolean publish) {
        Post post = new Post(title, "요약", "본문", category, null, null);

        if (publish) {
            post.publish(publishedAt);
        }

        postRepository.save(post);
    }

    /** 상태는 PUBLISHED 인데 시각이 미래다. 시각까지 봐야 걸러진다 */
    private void savePublishedAt(String title, Category category, LocalDateTime publishedAt) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(publishedAt);
        postRepository.save(post);
    }

    private void saveUnpublished(String title, Category category) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post.unpublish();
        postRepository.save(post);
    }

    @Test
    @DisplayName("로그인하지 않아도 볼 수 있다")
    void openToAnonymous() throws Exception {
        mockMvc.perform(get(POSTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("발행되고 시각이 지난 글만 나온다")
    void showsOnlyPublished() throws Exception {
        mockMvc.perform(get(POSTS).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[?(@.title == '임시저장 글')]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.title == '예약된 글')]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.title == '내린 글')]").isEmpty());
    }

    @Test
    @DisplayName("상태를 조회조건으로 넣어도 비공개 글은 안 나온다")
    void ignoresStatusParameter() throws Exception {
        mockMvc.perform(get(POSTS).param("status", "DRAFT").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("카테고리로 거를 수 있다 — 상단 카테고리 링크가 여기로 온다")
    void filtersByCategory() throws Exception {
        mockMvc.perform(get(POSTS).param("categoryId", String.valueOf(openCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("페이지를 나눠 준다")
    void paginates() throws Exception {
        mockMvc.perform(get(POSTS).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get(POSTS).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("size 를 크게 넣어도 상한에서 막힌다")
    void capsSize() throws Exception {
        mockMvc.perform(get(POSTS).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }
}
