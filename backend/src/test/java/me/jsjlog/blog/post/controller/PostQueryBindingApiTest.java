package me.jsjlog.blog.post.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.exception.GlobalExceptionHandler;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.PostListCondition;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.PostService;
import me.jsjlog.blog.post.service.PostViewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostQueryBindingApiTest {
	private static final String POSTS = "/api/v1/blog/posts";
	private static final String INPUT_MARKER = "fake-query-input-marker";

	@Autowired private MockMvc mvc;
	@Autowired private PostRepository posts;
	@Autowired private CategoryRepository categories;
	@Autowired private ObjectMapper json;

	private Long categoryId;

	@DynamicPropertySource
	static void isolatedDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:post_query_binding;"
				+ "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
	}

	@BeforeEach
	void seed() {
		posts.deleteAll();
		Category first = category("바인딩 첫 분류", 1L);
		Category second = category("바인딩 둘째 분류", 2L);
		categoryId = first.getId();
		Post match = new Post("키워드 검색 글", "요약", "본문", first, null, null);
		match.publish(LocalDateTime.now().minusDays(2));
		ReflectionTestUtils.setField(match, "views", 10L);
		posts.save(match);
		Post latest = new Post("다른 글", "요약", "본문", second, null, null);
		latest.publish(LocalDateTime.now().minusDays(1));
		posts.save(latest);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"page[0]", "size[0]", "categoryId[0]", "q[0]", "q.text", "page.value", "sort.value", "sort[0]"
	})
	void nestedScalarQueryIsFixedBadRequest(String name) throws Exception {
		String body = mvc.perform(get(POSTS).param(name, INPUT_MARKER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(INPUT_MARKER, name, "PostListCondition", "Exception", "java.lang");
	}

	@ParameterizedTest
	@ValueSource(strings = { "page", "size", "categoryId", "sort", "q" })
	void terminalArraySuffixRemainsIgnoredAsBefore(String field) throws Exception {
		String value = switch (field) {
			case "categoryId" -> String.valueOf(categoryId);
			case "sort" -> "popular";
			case "q" -> "키워드";
			default -> "1";
		};
		MockHttpServletRequestBuilder defaultQuery = get(POSTS);
		if (!"size".equals(field)) {
			defaultQuery.param("size", "1");
		}
		String defaults = mvc.perform(defaultQuery).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String alias = mvc.perform(query(field, field + "[]", value)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(json.readTree(alias).path("data")).isEqualTo(json.readTree(defaults).path("data"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "tracking", "unrelated.value", "unrelated[0]", "pageNumber", "q_extra.text" })
	void unrelatedQueryNamesRemainIgnored(String name) throws Exception {
		mvc.perform(get(POSTS).param(name, INPUT_MARKER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void numericDefaultsClampsAndSearchPoliciesRemainUnchanged() throws Exception {
		mvc.perform(get(POSTS).param("page", "-1").param("size", "100000").param("sort", "unknown").param("q", " x "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(50))
				.andExpect(jsonPath("$.data.totalElements").value(2));
		mvc.perform(get(POSTS).param("q", "x".repeat(4000)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void ordinaryTypeMismatchAndInvalidNumericIdKeepTheirExistingContracts() throws Exception {
		mvc.perform(get(POSTS).param("size", "abc"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(get(POSTS + "/suggest").param("size", "abc"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mvc.perform(get(POSTS + "/9223372036854775808"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mvc.perform(get(POSTS + "/0"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}

	@Test
	void actualInternalFailuresRemainServerErrorsWithoutExposingDetails() throws Exception {
		for (RuntimeException failure : List.of(new IllegalStateException(INPUT_MARKER),
				new InvalidPropertyException(PostListCondition.class, "internalProperty", INPUT_MARKER))) {
			PostService broken = mock(PostService.class);
			when(broken.getPosts(any(PostListCondition.class))).thenThrow(failure);
			MockMvc internal = MockMvcBuilders.standaloneSetup(new PostController(broken, mock(PostViewService.class)))
					.setControllerAdvice(new PostListQueryBinder(), new GlobalExceptionHandler()).build();
			String body = internal.perform(get(POSTS))
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(INPUT_MARKER, "internalProperty", "Exception", "java.lang");
		}
	}

	private MockHttpServletRequestBuilder query(String field, String name, String value) {
		MockHttpServletRequestBuilder request = get(POSTS).param(name, value);
		if (!"size".equals(field)) {
			request.param("size", "1");
		}
		return request;
	}

	private Category category(String name, long order) {
		return categories.findAll().stream().filter(found -> name.equals(found.getName()))
				.findFirst().orElseGet(() -> categories.save(new Category(name, order)));
	}
}
