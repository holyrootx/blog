package me.jsjlog.blog.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import tools.jackson.databind.ObjectMapper;

import me.jsjlog.blog.admin.dto.AdminCommentSearchCondition;
import me.jsjlog.blog.admin.dto.AdminGuestbookSearchCondition;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.admin.service.AdminGuestbookService;
import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminListQueryBindingApiTest {

	private static final String INPUT_MARKER = "fake-admin-list-query-value";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 0);

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper json;
	@Autowired private ApplicationContext context;
	@Autowired private MemberRepository members;
	@Autowired private CategoryRepository categories;
	@Autowired private PostRepository posts;
	@Autowired private CommentRepository comments;
	@Autowired private GuestbookEntryRepository entries;
	@MockitoSpyBean private AdminCommentService adminComments;
	@MockitoSpyBean private AdminGuestbookService adminGuestbook;
	@MockitoSpyBean private AdminMemberService adminMembers;

	private MemberPrincipal admin;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admin_list_query_binding_094;"
				+ "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
	}

	@BeforeEach
	void securityFixture() {
		admin = MemberPrincipal.ofLocal(members.save(Member.ofLocalAdmin(
				"fake-list-query-admin", "fake-test-hash", "가짜 관리자")));
		clearInvocations(adminComments, adminGuestbook, adminMembers);
	}

	static Stream<Arguments> nestedQueries() {
		return Stream.of(Endpoint.values()).flatMap(endpoint -> endpoint.fields().stream()
				.flatMap(field -> Stream.of(field + "[0]", field + ".value")
						.map(name -> Arguments.of(endpoint, name))));
	}

	static Stream<Arguments> mixedQueries() {
		return Stream.of(Endpoint.values()).flatMap(endpoint -> Stream.of("page[0]", "keyword.text")
				.map(name -> Arguments.of(endpoint, name)));
	}

	@ParameterizedTest
	@MethodSource("nestedQueries")
	void nestedScalarQueriesAreFixedBadRequests(Endpoint endpoint, String name) throws Exception {
		assertFixedBadRequest(adminQuery(endpoint).param(name, INPUT_MARKER), name);
		assertNoListServiceCall();
	}

	@ParameterizedTest
	@MethodSource("mixedQueries")
	void mixedFlatAndNestedQueriesAreRejectedBeforeTheService(Endpoint endpoint, String name) throws Exception {
		assertFixedBadRequest(adminQuery(endpoint).param("page", "0").param("size", "1")
				.param("status", endpoint.status()).param("keyword", "표식").param(name, INPUT_MARKER), name);
		assertNoListServiceCall();
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void emptyDefaultsAndFlatFiltersKeepTheirExistingMeaning(Endpoint endpoint) throws Exception {
		mvc.perform(adminQuery(endpoint)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20));
		Seed seed = seedEntries();
		MockHttpServletRequestBuilder filtered = adminQuery(endpoint).param("page", "0").param("size", "1")
				.param("status", endpoint.status()).param("keyword", " 표식 ");
		if (endpoint == Endpoint.COMMENTS) {
			filtered.param("memberId", seed.memberId().toString());
		}
		mvc.perform(filtered).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(seed.id(endpoint)));
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void terminalArraySuffixAndUnrelatedQueriesRemainIgnored(Endpoint endpoint) throws Exception {
		seedEntries();
		String defaults = mvc.perform(adminQuery(endpoint)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		for (String field : endpoint.fields()) {
			String alias = mvc.perform(adminQuery(endpoint).param(field + "[]", INPUT_MARKER))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(json.readTree(alias).path("data")).isEqualTo(json.readTree(defaults).path("data"));
		}
		for (String name : List.of("pageNumber[0]", "keyword_extra.text", "tracking.value")) {
			String unknown = mvc.perform(adminQuery(endpoint).param(name, INPUT_MARKER))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(json.readTree(unknown).path("data")).isEqualTo(json.readTree(defaults).path("data"));
		}
		if (endpoint != Endpoint.COMMENTS) {
			mvc.perform(adminQuery(endpoint).param("memberId.text", INPUT_MARKER).param("memberId[0]", INPUT_MARKER))
					.andExpect(status().isOk());
		}
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void numericDefaultsClampsAndOrdinaryTypeErrorsStayUnchanged(Endpoint endpoint) throws Exception {
		mvc.perform(adminQuery(endpoint).param("page", "-1").param("size", "100000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(100));
		mvc.perform(adminQuery(endpoint).param("page", "").param("size", "0").param("keyword", ""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20));
		for (String field : endpoint.fields()) {
			if (!field.equals("keyword")) {
				mvc.perform(adminQuery(endpoint).param(field, INPUT_MARKER)).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
			}
		}
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void securityRunsBeforeNestedBinding(Endpoint endpoint) throws Exception {
		MemberPrincipal reader = MemberPrincipal.ofSocial(socialMember("fake-query-reader", "가짜 독자"));
		for (String name : List.of("page[0]", "keyword.text")) {
			mvc.perform(get(endpoint.path()).param(name, INPUT_MARKER)).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
			mvc.perform(get(endpoint.path()).param(name, INPUT_MARKER).with(user(reader)))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		assertNoListServiceCall();
	}

	@ParameterizedTest
	@EnumSource(value = Endpoint.class, names = { "COMMENTS", "GUESTBOOK" })
	void detailAndJsonRequestDtosDoNotUseListBindingRules(Endpoint endpoint) throws Exception {
		Seed seed = seedEntries();
		mvc.perform(get(endpoint.path() + "/{id}", seed.id(endpoint)).with(user(admin)).param("page[0]", INPUT_MARKER))
				.andExpect(status().isOk());
		mvc.perform(put(endpoint.path() + "/{id}/visibility", seed.id(endpoint)).with(user(admin)).with(csrf())
						.param("keyword.text", INPUT_MARKER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"hidden\":false}"))
				.andExpect(status().isOk());
	}

	@ParameterizedTest
	@EnumSource(Endpoint.class)
	void realInternalFailuresRemainGenericServerErrors(Endpoint endpoint) throws Exception {
		for (RuntimeException failure : List.of(new IllegalStateException(INPUT_MARKER),
				new InvalidPropertyException(AdminMemberSearchCondition.class, "fakeInternalField", INPUT_MARKER))) {
			Object controller = brokenController(endpoint, failure);
			MockMvc internal = MockMvcBuilders.standaloneSetup(controller)
					.setControllerAdvice(context.getBeansWithAnnotation(ControllerAdvice.class).values().toArray()).build();
			String body = internal.perform(get(endpoint.path())).andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(INPUT_MARKER, "fakeInternalField", "Exception", "java.lang");
		}
	}

	private void assertFixedBadRequest(MockHttpServletRequestBuilder request, String name) throws Exception {
		String body = mvc.perform(request).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()))
				.andExpect(jsonPath("$.errors").isEmpty()).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(INPUT_MARKER, name, "SearchCondition", "Exception", "java.lang");
	}

	private void assertNoListServiceCall() {
		verify(adminComments, never()).getComments(any(AdminCommentSearchCondition.class));
		verify(adminGuestbook, never()).getEntries(any(AdminGuestbookSearchCondition.class));
		verify(adminMembers, never()).getMembers(any(AdminMemberSearchCondition.class));
	}

	private Object brokenController(Endpoint endpoint, RuntimeException failure) {
		return switch (endpoint) {
			case COMMENTS -> {
				AdminCommentService broken = mock(AdminCommentService.class);
				when(broken.getComments(any(AdminCommentSearchCondition.class))).thenThrow(failure);
				yield new AdminCommentController(broken);
			}
			case GUESTBOOK -> {
				AdminGuestbookService broken = mock(AdminGuestbookService.class);
				when(broken.getEntries(any(AdminGuestbookSearchCondition.class))).thenThrow(failure);
				yield new AdminGuestbookController(broken);
			}
			case MEMBERS -> {
				AdminMemberService broken = mock(AdminMemberService.class);
				when(broken.getMembers(any(AdminMemberSearchCondition.class))).thenThrow(failure);
				yield new AdminMemberController(broken);
			}
		};
	}

	private MockHttpServletRequestBuilder adminQuery(Endpoint endpoint) {
		return get(endpoint.path()).with(user(admin));
	}

	private Member socialMember(String providerId, String nickname) {
		return members.save(Member.ofSocial(AuthProvider.GOOGLE, providerId, nickname, null, null));
	}

	private Seed seedEntries() {
		Member writer = socialMember("fake-query-writer", "표식 독자");
		Member other = socialMember("fake-query-other", "다른 독자");
		writer.suspend(NOW);
		Category category = categories.save(new Category("입력 경계 분류", 1L));
		Post post = posts.save(new Post("입력 경계 글", "요약", "본문", category, null, null));
		Comment firstComment = comments.save(new Comment(post, null, writer, "표식 댓글"));
		firstComment.hideByAdmin();
		comments.save(new Comment(post, null, other, "다른 댓글"));
		GuestbookEntry firstEntry = entries.save(new GuestbookEntry(writer, "표식 방명록", true));
		firstEntry.hideByAdmin();
		entries.save(new GuestbookEntry(other, "다른 방명록", false));
		return new Seed(firstComment.getId(), firstEntry.getId(), writer.getId());
	}

	record Seed(Long commentId, Long entryId, Long memberId) {
		Long id(Endpoint endpoint) {
			return switch (endpoint) {
				case COMMENTS -> commentId;
				case GUESTBOOK -> entryId;
				case MEMBERS -> memberId;
			};
		}
	}

	enum Endpoint {
		COMMENTS("comments", "HIDDEN"), GUESTBOOK("guestbook", "HIDDEN"), MEMBERS("members", "SUSPENDED");

		private final String path;
		private final String status;

		Endpoint(String suffix, String status) {
			this.path = "/api/v1/admin/blog/" + suffix;
			this.status = status;
		}

		String path() { return path; }
		String status() { return status; }

		List<String> fields() {
			return this == COMMENTS ? List.of("page", "size", "status", "keyword", "memberId")
					: List.of("page", "size", "status", "keyword");
		}
	}
}
