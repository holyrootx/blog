package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommentListResponse;
import me.jsjlog.blog.admin.dto.AdminCommentReplyRequest;
import me.jsjlog.blog.admin.dto.AdminCommentSearchCondition;
import me.jsjlog.blog.admin.dto.AdminCommentVisibilityRequest;
import me.jsjlog.blog.admin.repository.AdminCommentQueryRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminCommentService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final AdminCommentQueryRepository adminCommentQueryRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public AdminCommentListResponse getComments(AdminCommentSearchCondition condition) {
        var items = adminCommentQueryRepository.getComments(condition);
        long totalElements = adminCommentQueryRepository.countComments(condition);
        int size = condition.sizeOrDefault();

        return new AdminCommentListResponse(
                items,
                adminCommentQueryRepository.countByStatus(condition),
                condition.pageOrDefault(),
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size)
        );
    }

    @Transactional
    public Long reply(Long commentId, AdminCommentReplyRequest request, Long memberId) {
        Comment parent = findComment(commentId);
        if (parent.isReply()) {
            throw new BlogException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
        }
        if (parent.isDeleted()) {
            throw new BlogException(ErrorCode.COMMENT_DELETED);
        }

        Member admin = findActiveAdmin(memberId);
        String content = requireContent(request == null ? null : request.content());

        Comment reply = commentRepository.save(new Comment(
                parent.getPost(),
                parent,
                admin,
                content
        ));

        return reply.getId();
    }

    @Transactional
    public void updateVisibility(
            Long commentId,
            AdminCommentVisibilityRequest request
    ) {
        if (request == null || request.hidden() == null) {
            throw new BlogException(ErrorCode.COMMENT_VISIBILITY_REQUIRED);
        }

        Comment comment = findComment(commentId);
        if (request.hidden()) {
            comment.delete();
        } else {
            comment.restore();
        }
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Member findActiveAdmin(Long memberId) {
        if (memberId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));

        if (!member.getStatus().isActive() || member.getRole() != MemberRole.ADMIN) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        return member;
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
}
