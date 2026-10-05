package me.jsjlog.blog.post.repository;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentItemResponse;
import me.jsjlog.blog.post.dto.CommentListResponse;
import me.jsjlog.blog.post.dto.CommentReplyResponse;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 한 페이지를 읽는 쿼리 수가 부모 댓글 수에 따라 늘지 않는다.
 *
 * <p>부모마다 답글을 따로 물으면 50개짜리 페이지에서 답글 쿼리만 50번 나간다.
 * 쿼리 수를 세려고 통계를 켜야 해서 다른 테스트와 DB 를 나눈다.</p>
 */
@SpringBootTest
@Transactional
class CommentListQueryCountTest {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:comment_list_query_count;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired CommentRepository commentRepository;
    @Autowired PostRepository postRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Category category;
    private Member member;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(new Category("쿼리 수 " + System.nanoTime(), 1L));
        member = memberRepository.save(Member.ofSocial(AuthProvider.NAVER, "query-count-" + System.nanoTime(),
                "쿼리회원", null, null));
    }

    @Test
    @DisplayName("부모가 1개든 8개든 같은 수의 쿼리로 읽는다")
    void queryCountDoesNotGrowWithParents() {
        Post onePost = postWithParents(1, 3);
        Post manyPost = postWithParents(8, 3);

        long forOne = statementsFor(() -> commentRepository.getCommentPageByPostId(onePost.getId(), null, 20, member.getId()));
        long forMany = statementsFor(() -> commentRepository.getCommentPageByPostId(manyPost.getId(), null, 20, member.getId()));

        assertThat(forMany).isEqualTo(forOne);
    }

    @Test
    @DisplayName("부모마다 앞쪽 답글 10개를 오래된 순으로 주고, 더 있으면 다음 커서를 준다")
    void initialRepliesAreCappedPerParent() {
        Post post = savePost();
        Comment busy = saveComment(post, null, "답글 많은 댓글");
        List<Comment> busyReplies = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> saveComment(post, busy, "답글 " + index))
                .toList();
        Comment quiet = saveComment(post, null, "답글 적은 댓글");
        Comment quietReply = saveComment(post, quiet, "하나뿐인 답글");
        Comment deletedReply = saveComment(post, quiet, "지운 답글");
        deletedReply.delete();
        commentRepository.flush();

        CommentListResponse page = commentRepository.getCommentPageByPostId(post.getId(), null, 20, member.getId());

        CommentItemResponse busyItem = item(page, busy.getId());
        assertThat(busyItem.replies()).extracting(CommentReplyResponse::id)
                .containsExactlyElementsOf(busyReplies.subList(0, 10).stream().map(Comment::getId).toList());
        assertThat(busyItem.replyHasNext()).isTrue();
        assertThat(busyItem.replyNextCursor()).isEqualTo(busyReplies.get(9).getId());

        CommentItemResponse quietItem = item(page, quiet.getId());
        assertThat(quietItem.replies()).extracting(CommentReplyResponse::id).containsExactly(quietReply.getId());
        assertThat(quietItem.replyHasNext()).isFalse();
    }

    private long statementsFor(Runnable query) {
        commentRepository.flush();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        query.run();
        return statistics.getPrepareStatementCount();
    }

    private Post postWithParents(int parents, int repliesEach) {
        Post post = savePost();
        for (int parentIndex = 0; parentIndex < parents; parentIndex++) {
            Comment parent = saveComment(post, null, "부모 " + parentIndex);
            for (int replyIndex = 0; replyIndex < repliesEach; replyIndex++) {
                saveComment(post, parent, "답글 " + parentIndex + "-" + replyIndex);
            }
        }
        return post;
    }

    private Post savePost() {
        Post post = new Post("쿼리 수 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        return postRepository.save(post);
    }

    private Comment saveComment(Post post, Comment parent, String content) {
        return commentRepository.save(new Comment(post, parent, member, content));
    }

    private static CommentItemResponse item(CommentListResponse page, Long id) {
        return page.items().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
    }
}
