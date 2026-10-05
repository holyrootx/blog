package me.jsjlog.blog.post.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.dto.AdjacentPostResponse;
import me.jsjlog.blog.post.dto.AdjacentPostSummary;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.PostListCondition;
import me.jsjlog.blog.post.dto.PostSuggestResponse;
import me.jsjlog.blog.post.dto.PostSummaryResponse;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 화면의 모든 입구가 같은 기준으로 글을 가린다.
 *
 * <p>발행 상태인데 발행 시각이 아직 오지 않은 글을 만든다. 예약은 SCHEDULED 로 따로 두므로
 * 정상 경로로는 생기지 않지만, 기준이 입구마다 달랐을 때 상세 화면만 이런 글을 보여 줬다.
 * 한쪽만 고쳐 다시 갈라지면 여기서 걸린다.</p>
 *
 * <p>테스트 DB 는 다른 테스트와 같이 쓴다. 다른 테스트가 남긴 공개 글이 있을 수 있으므로
 * "결과가 비었다" 가 아니라 "이 글이 없다" 를 본다.</p>
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "blog.frontend.index-path=../frontend/index.html")
class PublicPostVisibilityTest {

    @Autowired PostService postService;
    @Autowired CommentService commentService;
    @Autowired PostPageService postPageService;
    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager entityManager;

    private Category category;
    private Post visible;
    private Post notYet;
    private Member member;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(new Category("공개 기준", 870L));
        visible = publish("이미 공개된 글", LocalDateTime.now().minusDays(1));
        notYet = publish("아직 시각이 안 된 글", LocalDateTime.now().plusDays(1));
        member = memberRepository.save(Member.ofSocial(AuthProvider.NAVER, "visibility-" + System.nanoTime(),
                "공개회원", null, null));
        commentRepository.save(new Comment(notYet, null, member, "시각 전 글에 남은 댓글"));
        entityManager.flush();
    }

    @Test
    @DisplayName("상세·조회수·댓글 목록·댓글 쓰기가 모두 같은 글을 막는다")
    void everyEntranceHidesPostBeforeItsTime() {
        expectNotFound(() -> postService.getPostDetail(notYet.getId(), true, null));
        expectNotFound(() -> postService.getCommentInPostDetail(notYet.getId(), null, 20L, null));
        expectNotFound(() -> commentService.createComment(notYet.getId(),
                new CommentCreateRequest("시각 전 글에 쓰기", null), member.getId()));

        assertThat(postRepository.increaseViewCount(notYet.getId(), PostStatus.PUBLISHED, LocalDateTime.now())).isZero();
        assertThat(commentRepository.getCommentPageByPostId(notYet.getId(), null, 20, null).items()).isEmpty();
        assertThat(commentRepository.findMyComments(member.getId(), LocalDateTime.now(), PageRequest.of(0, 20))).isEmpty();
        assertThat(postPageService.render(String.valueOf(notYet.getId())).found()).isFalse();
    }

    @Test
    @DisplayName("목록·대문·검색·이전 다음 글에도 나오지 않는다")
    void listsSkipPostBeforeItsTime() {
        PostListCondition condition = new PostListCondition(0, 20, category.getId(), null, null);

        assertThat(postRepository.getPublicPosts(condition)).extracting(PostSummaryResponse::id)
                .containsExactly(visible.getId());
        assertThat(postRepository.countPublicPosts(condition)).isEqualTo(1);
        assertThat(postRepository.getPostsForHomePage("latest", 50L)).extracting(PostSummaryResponse::id)
                .doesNotContain(notYet.getId());
        assertThat(postRepository.getPublicPostSuggestions("아직 시각", 10)).extracting(PostSuggestResponse::id)
                .doesNotContain(notYet.getId());

        // 공개 글에서 다음 글을 끝까지 따라가도 시각이 안 된 글로는 넘어가지 않는다
        assertThat(nextPostIdsFrom(visible.getId())).doesNotContain(notYet.getId());

        AdjacentPostResponse aroundHidden = postRepository.getAdjacentPost(notYet.getId());
        assertThat(aroundHidden.previousPost()).isNull();
        assertThat(aroundHidden.nextPost()).isNull();
    }

    @Test
    @DisplayName("이미 공개된 글은 그대로 보인다")
    void publishedPostStaysVisible() {
        assertThat(postService.getPostDetail(visible.getId(), true, null).id()).isEqualTo(visible.getId());
        assertThat(postPageService.render(String.valueOf(visible.getId())).found()).isTrue();
    }

    private List<Long> nextPostIdsFrom(Long postId) {
        List<Long> ids = new ArrayList<>();
        AdjacentPostSummary next = postRepository.getAdjacentPost(postId).nextPost();

        // 다른 테스트가 남긴 글이 많아도 끝이 있게 한도를 둔다
        while (next != null && ids.size() < 1000) {
            ids.add(next.id());
            next = postRepository.getAdjacentPost(next.id()).nextPost();
        }
        return ids;
    }

    private Post publish(String title, LocalDateTime publishedAt) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(publishedAt);
        return postRepository.save(post);
    }

    private static void expectNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }
}
