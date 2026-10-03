package me.jsjlog.blog.post.controller;

import java.time.LocalDateTime;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.search.repository.SearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 글 검색.
 *
 * <p>목록과 같은 자리({@code /blog/posts})에 {@code q} 를 더해 쓴다. 그래서 여기서도
 * <b>안 보여야 할 글이 안 보이는 것</b>이 가장 중요하다 — 검색은 목록보다 위험하다.
 * 제목을 알고 찾는 사람에게 발행 전 원고가 걸리면 그건 유출이다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostSearchApiTest {

    private static final String POSTS = "/api/v1/blog/posts";
    private static final String SUGGEST = "/api/v1/blog/posts/suggest";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SearchLogRepository searchLogRepository;

    @BeforeEach
    void seed() {
        postRepository.deleteAll();
        searchLogRepository.deleteAll();

        Category category = category("검색분류", 900L);

        publish("Spring Boot 로 블로그 만들기", "제목에 있는 말", category);
        publish("두 번째 글", "발췌에 Spring 이 들어 있다", category);
        publish("세 번째 글", "여기엔 없다", category, "본문에만 Spring 이 있다");
        publish("소문자 spring 이야기", "관계없는 요약", category);
        // 아래 둘은 짝이다. 이스케이프가 없으면 "100%" 는 LIKE 로 "%100%%" 가 되어
        // 100 으로 시작하는 1000 짜리 글까지 끌어온다. 그래야 이 검사가 뜻이 있다
        publish("할인율 100% 정리", "특수문자가 든 제목", category);
        publish("조회수 1000 돌파", "숫자만 든 제목", category);

        draft("임시저장 Spring 원고", "아직 안 낸 글", category);
    }

    /** 이름에 unique 가 걸려 있어 테스트마다 새로 만들면 충돌한다. 있으면 그대로 쓴다 */
    private Category category(String name, long sortOrder) {
        return categoryRepository.findAll().stream()
                .filter(found -> name.equals(found.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(name, sortOrder)));
    }

    private void publish(String title, String excerpt, Category category) {
        publish(title, excerpt, category, "본문");
    }

    private void publish(String title, String excerpt, Category category, String content) {
        Post post = new Post(title, excerpt, content, category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        postRepository.save(post);
    }

    private void draft(String title, String excerpt, Category category) {
        postRepository.save(new Post(title, excerpt, "본문", category, null, null));
    }

    @Test
    @DisplayName("제목으로 찾는다")
    void findsByTitle() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "블로그 만들기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Spring Boot 로 블로그 만들기"));
    }

    @Test
    @DisplayName("발췌로도 찾는다 — 제목이 놓치는 것을 메운다")
    void findsByExcerpt() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "발췌에"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("두 번째 글"));
    }

    @Test
    @DisplayName("본문으로는 찾지 않는다 — 마크다운 원문이라 기호와 주소까지 걸린다")
    void doesNotSearchContent() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "본문에만"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("대소문자를 구분한다")
    void isCaseSensitive() throws Exception {
        // Spring 으로 찾으면 제목·발췌에 대문자 Spring 이 있는 공개 글 둘만 나온다
        mockMvc.perform(get(POSTS).param("q", "Spring").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == '소문자 spring 이야기')]").isEmpty());

        // spring 으로 찾으면 소문자로 쓴 글만 나온다
        mockMvc.perform(get(POSTS).param("q", "spring").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("소문자 spring 이야기"));
    }

    @Test
    @DisplayName("발행 안 한 글은 검색에도 안 나온다")
    void hidesUnpublished() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "임시저장").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("한 글자는 검색으로 치지 않고 평범한 목록을 준다")
    void ignoresSingleLetter() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "글").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6));
    }

    @Test
    @DisplayName("제목에 걸린 글이 발췌에 걸린 글보다 먼저 나온다")
    void putsTitleMatchesFirst() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "Spring").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Spring Boot 로 블로그 만들기"));
    }

    @Test
    @DisplayName("% 는 글자 그대로 찾는다 — LIKE 기호로 새지 않는다")
    void escapesPercent() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "100%").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("할인율 100% 정리"));
    }

    @Test
    @DisplayName("_ 도 글자 그대로 찾는다 — 아무 글자 한 자로 새지 않는다")
    void escapesUnderscore() throws Exception {
        // 이스케이프가 없으면 "1_0" 은 1, 아무 글자, 0 이 되어 "1000" 이 걸린다
        mockMvc.perform(get(POSTS).param("q", "1_0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("검색하면 무엇을 찾았는지 남는다")
    void recordsSearch() throws Exception {
        mockMvc.perform(get(POSTS).param("q", "블로그 만들기"))
                .andExpect(status().isOk());

        assertThat(searchLogRepository.findAll())
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getKeyword()).isEqualTo("블로그 만들기");
                    assertThat(saved.getResultCount()).isEqualTo(1L);
                });
    }

    @Test
    @DisplayName("검색어가 없는 평범한 목록은 남기지 않는다")
    void doesNotRecordPlainList() throws Exception {
        mockMvc.perform(get(POSTS))
                .andExpect(status().isOk());

        assertThat(searchLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("자동완성은 글을 주되 기록은 남기지 않는다")
    void suggestsWithoutRecording() throws Exception {
        mockMvc.perform(get(SUGGEST).param("q", "블로그"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Spring Boot 로 블로그 만들기"))
                .andExpect(jsonPath("$.data[0].categoryName").value("검색분류"));

        assertThat(searchLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("자동완성도 발행 안 한 글은 주지 않는다")
    void suggestHidesUnpublished() throws Exception {
        mockMvc.perform(get(SUGGEST).param("q", "임시저장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("자동완성에 한 글자를 보내면 빈 목록이다 — 전부 끌어오지 않는다")
    void suggestIgnoresSingleLetter() throws Exception {
        mockMvc.perform(get(SUGGEST).param("q", "글"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
