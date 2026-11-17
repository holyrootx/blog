package me.jsjlog.blog.admin.controller;

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
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import tools.jackson.databind.ObjectMapper;

import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.service.AdminPostService;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPostQueryBindingApiTest {
	private static final String POSTS = "/api/v1/admin/blog/posts";
	private static final String INPUT_MARKER = "fake-admin-query-marker";
	private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 17, 12, 0);

	@Autowired private MockMvc mvc;
	@Autowired private PostRepository posts;
	@Autowired private CategoryRepository categories;
	@Autowired private MemberRepository members;
	@Autowired private ObjectMapper json;
	@Autowired private ApplicationContext context;
	@MockitoSpyBean private AdminPostService adminPosts;

	private MemberPrincipal admin;
	private MemberPrincipal reader;
	private Long categoryId;
	private Long publishedId;
	private Long deletedId;

	@DynamicPropertySource
	static void isolatedDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admin_post_query_binding_090;"
				+ "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
	}

	@BeforeEach
	void seed() {
		clearInvocations(adminPosts);
		posts.deleteAll();
		admin = MemberPrincipal.ofLocal(members.save(Member.ofLocalAdmin(
				"fake-query-admin-" + System.nanoTime(), "fake-test-hash", "가짜 관리자")));
		reader = MemberPrincipal.ofSocial(members.save(Member.ofSocial(
				AuthProvider.GOOGLE, "fake-query-reader-" + System.nanoTime(), "가짜 독자", null, null)));
		Category first = category("관리자 바인딩 첫 분류", 1L);
		Category second = category("관리자 바인딩 둘째 분류", 2L);
		categoryId = first.getId();

		Post published = new Post("표식 발행 글", "요약", "본문", first, null, null);
		published.publish(BASE.minusDays(2));
		publishedId = posts.save(published).getId();
		posts.save(new Post("표식 임시 글", "요약", "본문", first, null, null));
		Post scheduled = new Post("표식 예약 글", "요약", "본문", first, null, null);
		scheduled.schedule(BASE.plusDays(2));
		posts.save(scheduled);
		Post other = new Post("다른 분류 글", "요약", "본문", second, null, null);
		other.publish(BASE.minusDays(1));
		posts.save(other);
		Post deleted = new Post("표식 휴지통 글", "요약", "본문", first, null, null);
		deleted.publish(BASE.minusDays(1));
		deleted.delete(BASE);
		deletedId = posts.save(deleted).getId();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"page[0]", "page.value", "size[0]", "size.value", "status[0]", "status.value",
			"categoryId[0]", "categoryId.value", "keyword[0]", "keyword.text", "trash[0]", "trash.value"
	})
	void nestedScalarQueriesAreFixedBadRequests(String name) throws Exception {
		String body = mvc.perform(adminQuery().param(name, INPUT_MARKER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(INPUT_MARKER, name, "AdminPostSearchCondition", "Exception", "java.lang");
	}

	@ParameterizedTest
	@ValueSource(strings = { "page[0]", "keyword.text" })
	void mixedFlatAndNestedQueriesAreRejectedBeforeTheService(String name) throws Exception {
		mvc.perform(adminQuery().param("page", "0").param("size", "1").param("status", "PUBLISHED")
						.param("categoryId", String.valueOf(categoryId)).param("keyword", "표식").param("trash", "false")
						.param(name, INPUT_MARKER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()))
				.andExpect(jsonPath("$.errors").isEmpty());
		verify(adminPosts, never()).getPostList(any(AdminPostSearchCondition.class));
	}

	@ParameterizedTest
	@ValueSource(strings = { "page", "size", "status", "categoryId", "keyword", "trash" })
	void terminalArraySuffixRemainsIgnored(String field) throws Exception {
		String value = switch (field) {
			case "status" -> "PUBLISHED";
			case "categoryId" -> String.valueOf(categoryId);
			case "keyword" -> "표식";
			case "trash" -> "true";
			default -> "1";
		};
		String defaults = mvc.perform(adminQuery()).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String alias = mvc.perform(adminQuery().param(field + "[]", value)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(json.readTree(alias).path("data")).isEqualTo(json.readTree(defaults).path("data"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "tracking", "unrelated.value", "unrelated[0]", "pageNumber", "keyword_extra.text", "trashBin[0]" })
	void unrelatedQueryNamesRemainIgnored(String name) throws Exception {
		mvc.perform(adminQuery().param(name, INPUT_MARKER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4));
	}

	@ParameterizedTest
	@ValueSource(strings = { "page", "size", "status", "categoryId", "trash" })
	void ordinaryTypeMismatchKeepsItsValidationContract(String field) throws Exception {
		mvc.perform(adminQuery().param(field, INPUT_MARKER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void authenticationAndAuthorizationRunBeforeMalformedBinding() throws Exception {
		for (String name : List.of("tracking", "page[0]")) {
			mvc.perform(get(POSTS).param(name, INPUT_MARKER))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
			mvc.perform(get(POSTS).param(name, INPUT_MARKER).with(user(reader)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
	}

	@Test
	void flatQueriesPreserveCategoryKeywordStatusCountsAndTrashPolicies() throws Exception {
		mvc.perform(adminQuery().param("page", "0").param("size", "1")
						.param("categoryId", String.valueOf(categoryId)).param("keyword", "표식").param("status", "PUBLISHED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(publishedId))
				.andExpect(jsonPath("$.data.statusCounts.all").value(3))
				.andExpect(jsonPath("$.data.statusCounts.scheduled").value(1))
				.andExpect(jsonPath("$.data.statusCounts.draft").value(1));
		mvc.perform(adminQuery().param("trash", "true").param("status", "DRAFT")
						.param("categoryId", String.valueOf(categoryId)).param("keyword", "표식"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(deletedId))
				.andExpect(jsonPath("$.data.statusCounts.trash").value(1));
	}

	@Test
	void numericDefaultsClampsAndStringSearchRemainUnchanged() throws Exception {
		mvc.perform(adminQuery().param("page", "-1").param("size", "100000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(100))
				.andExpect(jsonPath("$.data.totalElements").value(4));
		mvc.perform(adminQuery().param("page", "").param("size", "0").param("keyword", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(4));
		mvc.perform(adminQuery().param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.items.length()").value(1));
		mvc.perform(adminQuery().param("keyword", "x".repeat(4000)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void listOnlyBindingRulesDoNotApplyToDetailOrChangeNumericIdErrors() throws Exception {
		mvc.perform(get(POSTS + "/{postId}", publishedId).param("page[0]", INPUT_MARKER).with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(publishedId));
		mvc.perform(get(POSTS + "/not-a-number").with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void actualInternalFailuresRemainServerErrorsWithAllAdvicePresent() throws Exception {
		for (RuntimeException failure : List.of(new IllegalStateException(INPUT_MARKER),
				new InvalidPropertyException(AdminPostSearchCondition.class, "fakeInternalField", INPUT_MARKER))) {
			AdminPostService broken = mock(AdminPostService.class);
			when(broken.getPostList(any(AdminPostSearchCondition.class))).thenThrow(failure);
			MockMvc internal = MockMvcBuilders.standaloneSetup(new AdminPostController(broken))
					.setControllerAdvice(context.getBeansWithAnnotation(ControllerAdvice.class).values().toArray()).build();
			String body = internal.perform(get(POSTS))
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(INPUT_MARKER, "fakeInternalField", "AdminPostSearchCondition", "Exception", "java.lang");
		}
	}

	private MockHttpServletRequestBuilder adminQuery() {
		return get(POSTS).with(user(admin));
	}

	private Category category(String name, long order) {
		return categories.findAll().stream().filter(found -> name.equals(found.getName()))
				.findFirst().orElseGet(() -> categories.save(new Category(name, order)));
	}
}
