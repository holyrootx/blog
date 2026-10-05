package me.jsjlog.blog.common.exception;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import me.jsjlog.blog.common.response.ErrorResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보낸 쪽 잘못이나 잠깐 겹친 요청이 500 과 ERROR 로그로 나가지 않는가.
 *
 * <p>잠금 대기를 실제로 일으켜야 해서 다른 테스트와 DB 를 나눈다. 잠금 대기 시간은 짧게 둔다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientErrorResponseTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:client_error_response;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Post post;
    private MemberPrincipal principal;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.findAll().stream().findFirst()
                .orElseGet(() -> categoryRepository.save(new Category("오류 응답", 1L)));
        post = new Post("오류 응답 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        Member member = memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER, "client-error-" + System.nanoTime(), "오류회원", null, null));
        principal = MemberPrincipal.ofSocial(member);
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 빠지면 400 INVALID_INPUT 이다")
    void missingRequestParameterIsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/blog/posts/%d/reaction".formatted(post.getId()))
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("다른 요청이 글을 잠그고 있어 기다리다 시간이 다 되면 409 RESOURCE_BUSY 다")
    void lockTimeoutIsConflict() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();

        try {
            Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                postRepository.findLockedById(post.getId()).orElseThrow();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post("/api/v1/blog/posts/%d/comments".formatted(post.getId()))
                            .with(user(principal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"잠금 중에 쓴 댓글\",\"parentId\":null}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESOURCE_BUSY"));

            release.countDown();
            holding.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    @Test
    @DisplayName("multipart 한도를 넘은 업로드는 서비스 검사와 같은 400 IMAGE_TOO_LARGE 다")
    void uploadOverMultipartLimitIsImageTooLarge() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/blog/images");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleMaxUploadSize(new MaxUploadSizeExceededException(10L * 1024 * 1024), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("IMAGE_TOO_LARGE");
    }

    @Test
    @DisplayName("잠금 획득 실패 예외는 종류와 관계없이 409 RESOURCE_BUSY 로 바뀐다")
    void cannotAcquireLockIsConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/blog/posts/1/reaction");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleLockFailure(new CannotAcquireLockException("lock wait timeout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_BUSY");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
