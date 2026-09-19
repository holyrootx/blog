package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReaction;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.CommentCreateResponse;
import me.jsjlog.blog.post.dto.CommentReactionResponse;
import me.jsjlog.blog.post.repository.CommentReactionRepository;
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
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public CommentCreateResponse createComment(Long postId, CommentCreateRequest request, Long memberId) {
        String content = requireContent(request == null ? null : request.content());
        Post post = findPublishedPost(postId);
        Member member = findActiveMember(memberId);
        Comment parent = findParent(request == null ? null : request.parentId(), postId);

        Comment comment = commentRepository.save(new Comment(post, parent, member, content));
        return new CommentCreateResponse(comment.getId());
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
