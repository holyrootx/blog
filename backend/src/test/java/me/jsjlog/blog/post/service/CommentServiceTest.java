package me.jsjlog.blog.post.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.service.NotificationService;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReaction;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.CommentReactionResponse;
import me.jsjlog.blog.post.repository.CommentReactionRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.history.service.ContentHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long POST_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentReactionRepository commentReactionRepository;

    @Mock
    private CommentReportRepository commentReportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Post post;

    @Mock
    private Member member;

    @Mock
    private CommentWriteGuard commentWriteGuard;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                commentRepository,
                commentReactionRepository,
                commentReportRepository,
                new PostAccess(postRepository),
                mock(ContentHistoryService.class),
                memberRepository,
                notificationService,
                commentWriteGuard
        );
    }

    @Test
    @DisplayName("로그인한 회원이 공개 글에 댓글을 등록한다")
    void createsMemberComment() {
        givenPublishedPostAndActiveMember();
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        commentService.createComment(POST_ID, new CommentCreateRequest("  첫 댓글  ", null), MEMBER_ID);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getPost()).isSameAs(post);
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getParent()).isNull();
        assertThat(captor.getValue().getContent()).isEqualTo("첫 댓글");
    }

    @Test
    @DisplayName("답글의 답글은 등록하지 못한다")
    void rejectsNestedReply() {
        givenPublishedPostAndActiveMember();
        when(post.getId()).thenReturn(POST_ID);
        Comment root = new Comment(post, null, member, "부모");
        Comment reply = new Comment(post, root, member, "답글");
        when(commentRepository.findLockedById(10L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.createComment(
                POST_ID,
                new CommentCreateRequest("답글의 답글", 10L),
                MEMBER_ID
        )).isInstanceOfSatisfying(BlogException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED));

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("다른 글의 댓글을 부모로 지정하지 못한다")
    void rejectsParentFromAnotherPost() {
        givenPublishedPostAndActiveMember();
        Post otherPost = mock(Post.class);
        when(otherPost.getId()).thenReturn(99L);
        when(commentRepository.findLockedById(10L))
                .thenReturn(Optional.of(new Comment(otherPost, null, member, "다른 글 댓글")));

        assertThatThrownBy(() -> commentService.createComment(
                POST_ID,
                new CommentCreateRequest("잘못된 답글", 10L),
                MEMBER_ID
        )).isInstanceOfSatisfying(BlogException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_PARENT_INVALID));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("부모의 최신 상태가 삭제 또는 숨김이면 답글과 알림을 만들지 않는다")
    void rejectsDeletedOrHiddenParent(boolean hiddenByAdmin) {
        givenPublishedPostAndActiveMember();
        when(post.getId()).thenReturn(POST_ID);
        Comment parent = new Comment(post, null, member, "현재는 보이지 않는 부모");
        if (hiddenByAdmin) {
            parent.hideByAdmin();
        } else {
            parent.delete();
        }
        when(commentRepository.findLockedById(10L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(
                POST_ID, new CommentCreateRequest("거절할 답글", 10L), MEMBER_ID))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_DELETED));

        verify(commentRepository, never()).save(any(Comment.class));
        verify(notificationService, never()).notifyCommentCreated(any(Comment.class));
    }

    @Test
    @DisplayName("삭제되지 않은 부모에는 답글과 알림을 만든다")
    void createsReplyForActiveParent() {
        givenPublishedPostAndActiveMember();
        when(post.getId()).thenReturn(POST_ID);
        Comment parent = new Comment(post, null, member, "부모");
        when(commentRepository.findLockedById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        commentService.createComment(POST_ID, new CommentCreateRequest("정상 답글", 10L), MEMBER_ID);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(parent);
        assertThat(captor.getValue().getContent()).isEqualTo("정상 답글");
        verify(notificationService).notifyCommentCreated(captor.getValue());
    }

    @Test
    @DisplayName("좋아요를 누른 회원도 같은 댓글에 싫어요를 추가할 수 있다")
    void addsDislikeWithoutRemovingLike() {
        Comment comment = mock(Comment.class);
        CommentReaction like = new CommentReaction(comment, member, CommentReactionType.LIKE);
        CommentReaction dislike = new CommentReaction(comment, member, CommentReactionType.DISLIKE);
        when(commentRepository.findPostId(20L)).thenReturn(Optional.of(POST_ID));
        when(postRepository.findLockedById(POST_ID)).thenReturn(Optional.of(post));
        when(post.isPublished()).thenReturn(true);
        when(commentRepository.findLockedById(20L)).thenReturn(Optional.of(comment));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(commentReactionRepository.findByCommentIdAndMemberIdAndType(
                20L,
                MEMBER_ID,
                CommentReactionType.DISLIKE
        )).thenReturn(Optional.empty());
        when(commentReactionRepository.save(any(CommentReaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(commentReactionRepository.findAllByCommentIdAndMemberId(20L, MEMBER_ID))
                .thenReturn(java.util.List.of(like, dislike));
        when(commentReactionRepository.countByCommentIdAndType(20L, CommentReactionType.LIKE))
                .thenReturn(1L);
        when(commentReactionRepository.countByCommentIdAndType(20L, CommentReactionType.DISLIKE))
                .thenReturn(1L);

        CommentReactionResponse response = commentService.setReaction(
                20L,
                CommentReactionType.DISLIKE,
                MEMBER_ID
        );

        ArgumentCaptor<CommentReaction> captor = ArgumentCaptor.forClass(CommentReaction.class);
        verify(commentReactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CommentReactionType.DISLIKE);
        assertThat(response.likeCount()).isEqualTo(1L);
        assertThat(response.dislikeCount()).isEqualTo(1L);
        assertThat(response.likedByMe()).isTrue();
        assertThat(response.dislikedByMe()).isTrue();
    }

    @Test
    void rejectsRateLimitedRequestsBeforeAcquiringPostLock() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        org.mockito.Mockito.doThrow(new BlogException(ErrorCode.COMMENT_RATE_LIMITED))
                .when(commentWriteGuard).check(MEMBER_ID);
        assertThatThrownBy(() -> commentService.createComment(POST_ID,
                new CommentCreateRequest("제한된 요청", null), MEMBER_ID))
                .isInstanceOf(BlogException.class);
        verify(postRepository, never()).findLockedById(any());
        verify(commentRepository, never()).save(any());
    }

    private void givenPublishedPostAndActiveMember() {
        when(postRepository.findLockedById(POST_ID)).thenReturn(Optional.of(post));
        when(post.isPublished()).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    }
}
