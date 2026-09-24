package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.service.NotificationService;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReaction;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.CommentCreateResponse;
import me.jsjlog.blog.post.dto.CommentReactionResponse;
import me.jsjlog.blog.post.dto.CommentReportResponse;
import me.jsjlog.blog.post.repository.CommentReactionRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final CommentReportRepository commentReportRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    public CommentCreateResponse createComment(Long postId, CommentCreateRequest request, Long memberId) {
        String content = requireContent(request == null ? null : request.content());
        Post post = findPublishedPost(postId);
        Member member = findActiveMember(memberId);
        Comment parent = findParent(request == null ? null : request.parentId(), postId);

        Comment comment = commentRepository.save(new Comment(post, parent, member, content));

        // 답글이면 부모 댓글을 쓴 사람에게, 최상위 댓글이면 글쓴이에게 간다.
        // 자기 자신에게는 가지 않는다 — 그 판단은 알림 쪽이 한다
        notificationService.notifyCommentCreated(comment);

        return new CommentCreateResponse(comment.getId());
    }

    /**
     * 내 댓글 고치기.
     *
     * 남의 댓글은 관리자라도 여기로 고칠 수 없다. 남이 한 말을 다른 말로 바꿔 놓는 것은
     * 숨기는 것과 다른 일이라, 관리자에게도 숨기기만 있다.
     */
    @Transactional
    public void updateComment(Long commentId, String content, Long memberId) {
        String text = requireContent(content);
        Comment comment = findMyUsableComment(commentId, memberId);

        comment.updateContent(text);
    }

    /**
     * 내 댓글 지우기.
     *
     * 레코드를 지우지 않고 표시만 남긴다. 답글이 달린 댓글을 실제로 지우면 답글이
     * 부모를 잃기 때문이다 — 화면에는 "삭제된 댓글입니다" 로 나오고 답글은 그대로 보인다.
     *
     * 이 댓글로 갔던 알림도 같이 사라진다. 알림 조회가 삭제된 댓글을 빼고 보기 때문에
     * 여기서 따로 지울 것은 없다.
     */
    @Transactional
    public void deleteComment(Long commentId, Long memberId) {
        findMyUsableComment(commentId, memberId).delete();
    }

    /**
     * 고치거나 지울 수 있는 내 댓글인가.
     *
     * 없는 댓글과 남의 댓글에 다른 답을 주는 것은 괜찮다. 댓글은 어차피 공개되어 있어서
     * 있다는 사실이 새어 나갈 것이 없고, 화면은 왜 안 되는지 말해 줄 수 있어야 한다.
     */
    private Comment findMyUsableComment(Long commentId, Long memberId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));

        // 가려진 댓글과 지운 댓글에 다른 말을 준다. 둘 다 "삭제된 댓글" 이라고 하면
        // 운영자가 가린 사람은 자기가 지운 줄 알고 영문을 모른다
        if (comment.isDeleted()) {
            throw new BlogException(comment.isHiddenByAdmin()
                    ? ErrorCode.COMMENT_HIDDEN_BY_ADMIN
                    : ErrorCode.COMMENT_DELETED);
        }
        Member member = findActiveMember(memberId);

        if (!comment.getMember().getId().equals(member.getId())) {
            throw new BlogException(ErrorCode.COMMENT_NOT_MINE);
        }

        return comment;
    }

    /**
     * 댓글 신고.
     *
     * 접수만 한다. 신고가 쌓여도 댓글을 저절로 숨기지 않는다 — 여럿이 몰려 신고하면
     * 멀쩡한 글이 사라지고, 그러면 신고가 도리어 공격 수단이 된다. 숨길지는 사람이 본다.
     */
    @Transactional
    public CommentReportResponse reportComment(
            Long commentId,
            CommentReportReason reason,
            String detail,
            Long memberId
    ) {
        if (reason == null) {
            throw new BlogException(ErrorCode.COMMENT_REPORT_REASON_REQUIRED);
        }

        Comment comment = findUsableComment(commentId);
        Member member = findActiveMember(memberId);

        // 자기 댓글을 신고하는 것은 뜻이 없다. 지우고 싶으면 삭제하면 된다
        if (comment.getMember().getId().equals(member.getId())) {
            throw new BlogException(ErrorCode.COMMENT_REPORT_SELF);
        }

        // 한 사람이 여러 번 신고하면 혼자서 숫자를 부풀릴 수 있고,
        // 그러면 받는 쪽에서 신고 수를 믿을 수 없게 된다
        if (commentReportRepository.existsByCommentIdAndMemberId(commentId, member.getId())) {
            throw new BlogException(ErrorCode.COMMENT_REPORT_DUPLICATED);
        }

        commentReportRepository.save(new CommentReport(comment, member, reason, detail));

        // 접수만 하고 끝내면 운영자가 우연히 댓글 관리 화면에 들어갈 때까지 아무 일도
        // 일어나지 않는다. 그건 자동 숨김을 뺀 것과 다르다 — "사람이 본다" 가 이 판단의
        // 전제인데, 볼 계기를 안 만들면 전제가 거짓이 된다
        notificationService.notifyReportReceived(comment);

        return CommentReportResponse.accepted(commentId);
    }

    @Transactional
    public CommentReactionResponse setReaction(
            Long commentId,
            CommentReactionType type,
            Long memberId
    ) {
        if (type == null) {
            throw new BlogException(ErrorCode.COMMENT_REACTION_REQUIRED);
        }

        Comment comment = findUsableComment(commentId);
        Member member = findActiveMember(memberId);

        commentReactionRepository
                .findByCommentIdAndMemberIdAndType(commentId, memberId, type)
                .orElseGet(() -> commentReactionRepository.save(
                        new CommentReaction(comment, member, type)));

        return reactionResponse(commentId, memberId);
    }

    @Transactional
    public CommentReactionResponse removeReaction(
            Long commentId,
            CommentReactionType type,
            Long memberId
    ) {
        if (type == null) {
            throw new BlogException(ErrorCode.COMMENT_REACTION_REQUIRED);
        }

        findUsableComment(commentId);
        findActiveMember(memberId);

        commentReactionRepository.findByCommentIdAndMemberIdAndType(commentId, memberId, type)
                .ifPresent(commentReactionRepository::delete);

        return reactionResponse(commentId, memberId);
    }

    private Post findPublishedPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND));

        if (!post.isPublished()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    private Member findActiveMember(Long memberId) {
        if (memberId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));

        if (!member.getStatus().isActive()) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        return member;
    }

    private Comment findParent(Long parentId, Long postId) {
        if (parentId == null) {
            return null;
        }

        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));

        if (!parent.getPost().getId().equals(postId)) {
            throw new BlogException(ErrorCode.COMMENT_PARENT_INVALID);
        }
        if (parent.isReply()) {
            throw new BlogException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
        }
        if (parent.isDeleted()) {
            throw new BlogException(ErrorCode.COMMENT_DELETED);
        }

        return parent;
    }

    private Comment findUsableComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BlogException(ErrorCode.COMMENT_DELETED);
        }

        return comment;
    }

    private String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BlogException(ErrorCode.COMMENT_CONTENT_REQUIRED);
        }

        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BlogException(ErrorCode.COMMENT_CONTENT_TOO_LONG);
        }

        return trimmed;
    }

    private CommentReactionResponse reactionResponse(Long commentId, Long memberId) {
        var myReactions = commentReactionRepository
                .findAllByCommentIdAndMemberId(commentId, memberId)
                .stream()
                .map(CommentReaction::getType)
                .collect(java.util.stream.Collectors.toSet());

        return new CommentReactionResponse(
                commentReactionRepository.countByCommentIdAndType(commentId, CommentReactionType.LIKE),
                commentReactionRepository.countByCommentIdAndType(commentId, CommentReactionType.DISLIKE),
                myReactions.contains(CommentReactionType.LIKE),
                myReactions.contains(CommentReactionType.DISLIKE)
        );
    }
}
