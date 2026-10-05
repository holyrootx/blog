package me.jsjlog.blog.search.service;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 기록 저장이 DB 에서 실패해도 검색 결과는 나와야 한다.
 *
 * <p>목 객체로 예외를 던지면 저장소의 트랜잭션 처리를 건너뛰어, 실제로 문제가 됐던
 * "롤백만 가능해진 트랜잭션을 안에서 삼켰다가 커밋에서 다시 터지는" 경로를 재현하지 못한다.
 * 그래서 실제 테이블에 제약을 걸어 INSERT 를 실패시킨다. 제약이 다른 테스트에 번지지 않게 DB 를 나눈다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchLogFailureTest {

    private static final String FAILING_KEYWORD = "기록실패";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:search_log_failure;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SearchLogRepository searchLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        if (postRepository.count() == 0) {
            Category category = categoryRepository.save(new Category("검색 기록", 1L));
            Post post = new Post(FAILING_KEYWORD + " 이 들어간 글", "요약", "본문", category, null, null);
            post.publish(LocalDateTime.now().minusDays(1));
            postRepository.save(post);
            jdbcTemplate.execute("alter table search_log add constraint search_log_test_reject"
                    + " check (keyword <> '" + FAILING_KEYWORD + "')");
        }
        searchLogRepository.deleteAll();
    }

    @Test
    @DisplayName("검색 기록 INSERT 가 실패해도 검색은 200 으로 결과를 준다")
    void searchSucceedsWhenLoggingFails() throws Exception {
        mockMvc.perform(get("/api/v1/blog/posts").param("q", FAILING_KEYWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value(FAILING_KEYWORD + " 이 들어간 글"));

        assertThat(searchLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("기록이 되는 검색어는 그대로 남는다")
    void normalSearchIsStillLogged() throws Exception {
        mockMvc.perform(get("/api/v1/blog/posts").param("q", "들어간"))
                .andExpect(status().isOk());

        assertThat(searchLogRepository.count()).isEqualTo(1);
    }
}
