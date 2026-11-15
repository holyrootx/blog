package me.jsjlog.blog.post.service;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReactionRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 실제 글 잠금을 기다리는 두 요청의 HTTP 응답과 커밋된 반응을 비교한다. */
@SpringBootTest
@AutoConfigureMockMvc
class CommentReactionConcurrencyTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		MySqlTestDatabase.configure(registry, "comment_reaction_concurrency");
	}

	@Autowired MockMvc mvc;
	@Autowired CommentService service;
	@Autowired CommentRepository comments;
	@Autowired CommentReactionRepository reactions;
	@Autowired PostRepository posts;
	@Autowired CategoryRepository categories;
	@Autowired MemberRepository members;
	@Autowired PlatformTransactionManager transactions;
	@Autowired DataSource dataSource;
	@MockitoSpyBean PostAccess access;

	private final JsonMapper json = JsonMapper.builder().build();
	private TransactionTemplate tx;
	private Long commentId;
	private Member reader;
	private Member secondReader;
	private CountDownLatch waiterReadPostId;

	@BeforeEach
	void fixture() throws Exception {
		tx = new TransactionTemplate(transactions);
		tx.executeWithoutResult(status -> {
			String key = UUID.randomUUID().toString();
			Category category = categories.save(new Category("동시 반응 " + key, 981L));
			Post post = new Post("반응 검사", "요약", "본문", category, null, null);
			post.publish(LocalDateTime.now().minusDays(1));
			posts.save(post);
			Member writer = members.save(Member.ofSocial(AuthProvider.NAVER, "writer-" + key, "글쓴이", null, null));
			reader = members.save(Member.ofSocial(AuthProvider.NAVER, "reader-" + key, "독자", null, null));
			secondReader = members.save(Member.ofSocial(AuthProvider.NAVER, "reader2-" + key, "다른 독자", null, null));
			commentId = comments.save(new Comment(post, null, writer, "댓글")).getId();
		});
		try (var connection = dataSource.getConnection()) {
			if (MySqlTestDatabase.enabled()) {
				assertThat(connection.getTransactionIsolation())
						.as("기본 MySQL 격리 수준은 변경하지 않는다")
						.isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
			}
		}
		waiterReadPostId = new CountDownLatch(1);
		doAnswer(invocation -> {
			// findPostId의 실제 SELECT가 끝난 뒤에만 앞선 트랜잭션의 커밋을 허용한다.
			// 잠금·조회·저장 쿼리는 대체하지 않는다.
			if (Thread.currentThread().getName().equals("comment-reaction-waiter")) {
				waiterReadPostId.countDown();
			}
			return invocation.callRealMethod();
		}).when((PostAccess) AopTestUtils.getUltimateTargetObject(access)).lockPublished(anyLong());
	}

	private MvcResult request(boolean setting, Member who, CommentReactionType type) throws Exception {
		String path = "/api/v1/blog/comments/" + commentId + "/reaction";
		var builder = setting
				? put(path).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\"}")
				: delete(path + "?type=" + type);
		return mvc.perform(builder.with(user(MemberPrincipal.ofSocial(who))).with(csrf())).andReturn();
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).as("요청의 지정된 경계에 도착한다").isTrue();
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new AssertionError(error);
		}
	}

	private MvcResult overlap(boolean firstSets, boolean waiterSets, Member waiter,
			CommentReactionType firstType, CommentReactionType waiterType) throws Exception {
		CountDownLatch firstChanged = new CountDownLatch(1);
		try (ExecutorService first = Executors.newSingleThreadExecutor();
			 ExecutorService second = Executors.newSingleThreadExecutor(r -> new Thread(r, "comment-reaction-waiter"))) {
			Future<?> firstTask = first.submit(() -> tx.executeWithoutResult(status -> {
				if (firstSets) service.setReaction(commentId, firstType, reader.getId());
				else service.removeReaction(commentId, firstType, reader.getId());
				firstChanged.countDown();
				await(waiterReadPostId);
			}));
			await(firstChanged);
			Future<MvcResult> secondTask = second.submit(() -> request(waiterSets, waiter, waiterType));
			firstTask.get(15, TimeUnit.SECONDS);
			return secondTask.get(15, TimeUnit.SECONDS);
		}
	}

	private void verify(MvcResult response, Member who, CommentReactionType type,
			long expectedCount, boolean expectedMine, long otherCount, boolean otherMine) throws Exception {
		assertThat(response.getResponse().getStatus()).isEqualTo(200);
		var body = json.readTree(response.getResponse().getContentAsString()).path("data");
		for (CommentReactionType checked : CommentReactionType.values()) {
			long count = checked == type ? expectedCount : otherCount;
			boolean mine = checked == type ? expectedMine : otherMine;
			String countField = checked == CommentReactionType.LIKE ? "likeCount" : "dislikeCount";
			String mineField = checked == CommentReactionType.LIKE ? "likedByMe" : "dislikedByMe";
			assertThat(body.path(countField).asLong()).as(countField).isEqualTo(count);
			assertThat(body.path(mineField).asBoolean()).as(mineField).isEqualTo(mine);
			assertThat(reactions.countByCommentIdAndType(commentId, checked)).isEqualTo(count);
			assertThat(reactions.findByCommentIdAndMemberIdAndType(commentId, who.getId(), checked).isPresent())
					.isEqualTo(mine);
		}
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void sequentialSetRepeatAndRemove(CommentReactionType type) throws Exception {
		verify(request(true, reader, type), reader, type, 1, true, 0, false);
		verify(request(true, reader, type), reader, type, 1, true, 0, false);
		verify(request(false, reader, type), reader, type, 0, false, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void removeWaitingForSetMatchesStoredState(CommentReactionType type) throws Exception {
		verify(overlap(true, false, reader, type, type), reader, type, 0, false, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void setWaitingForRemoveMatchesStoredState(CommentReactionType type) throws Exception {
		service.setReaction(commentId, type, reader.getId());
		verify(overlap(false, true, reader, type, type), reader, type, 1, true, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void repeatedSetWaitingForSetRemainsIdempotent(CommentReactionType type) throws Exception {
		verify(overlap(true, true, reader, type, type), reader, type, 1, true, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void repeatedRemoveWaitingForRemoveRemainsIdempotent(CommentReactionType type) throws Exception {
		service.setReaction(commentId, type, reader.getId());
		verify(overlap(false, false, reader, type, type), reader, type, 0, false, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void differentMembersReceiveCurrentCountAfterLock(CommentReactionType type) throws Exception {
		verify(overlap(true, true, secondReader, type, type), secondReader, type, 2, true, 0, false);
	}

	@ParameterizedTest
	@EnumSource(CommentReactionType.class)
	void likeAndDislikeRemainIndependentWhenRequestsOverlap(CommentReactionType type) throws Exception {
		CommentReactionType other = type == CommentReactionType.LIKE ? CommentReactionType.DISLIKE : CommentReactionType.LIKE;
		verify(overlap(true, true, reader, other, type), reader, type, 1, true, 1, true);
	}
}
