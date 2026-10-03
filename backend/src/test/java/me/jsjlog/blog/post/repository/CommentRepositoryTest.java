package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReaction;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentItemResponse;
import me.jsjlog.blog.post.dto.CommentListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CommentRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentReactionRepository commentReactionRepository;

    @Test
    @DisplayName("회원 닉네임과 답글, 반응 집계, 내 반응을 한 페이지로 조회한다")
    void getsMemberCommentsWithReactions() {
        Category category = categoryRepository.save(new Category("댓글 테스트", 1L));
        Post post = new Post("댓글 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now());
        post = postRepository.save(post);

        Member writer = memberRepository.save(Member.ofSocial(
                AuthProvider.GOOGLE,
                "comment-writer",
                "댓글작성자",
                "writer@example.com",
                null
        ));
        Member reactor = memberRepository.save(Member.ofSocial(
                AuthProvider.KAKAO,
                "comment-reactor",
                "반응회원",
                null,
                null
        ));

        Comment root = commentRepository.save(new Comment(post, null, writer, "최상위 댓글"));
        Comment reply = commentRepository.save(new Comment(post, root, reactor, "회원 답글"));
        commentReactionRepository.save(new CommentReaction(root, reactor, CommentReactionType.LIKE));
        commentReactionRepository.save(new CommentReaction(root, reactor, CommentReactionType.DISLIKE));
        commentReactionRepository.save(new CommentReaction(reply, writer, CommentReactionType.DISLIKE));
        commentRepository.flush();
        commentReactionRepository.flush();

        CommentListResponse page = commentRepository.getCommentPageByPostId(
                post.getId(),
                null,
                20L,
                reactor.getId()
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.items()).hasSize(1);

        CommentItemResponse item = page.items().getFirst();
        assertThat(item.nickname()).isEqualTo("댓글작성자");
        assertThat(item.likeCount()).isEqualTo(1L);
        assertThat(item.dislikeCount()).isEqualTo(1L);
        assertThat(item.likedByMe()).isTrue();
        assertThat(item.dislikedByMe()).isTrue();
        assertThat(item.replies()).singleElement().satisfies(response -> {
            assertThat(response.nickname()).isEqualTo("반응회원");
            assertThat(response.dislikeCount()).isEqualTo(1L);
            assertThat(response.likedByMe()).isFalse();
            assertThat(response.dislikedByMe()).isFalse();
        });

        post.delete(LocalDateTime.now());
        postRepository.flush();

        assertThat(commentReactionRepository.count()).isEqualTo(3);
        assertThat(commentRepository.getCommentPageByPostId(post.getId(), null, 20L, reactor.getId()).items())
                .isEmpty();
    }
}
