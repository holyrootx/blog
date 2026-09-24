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
import me.jsjlog.blog.admin.dto.AdminCommentModerationDetail;
import me.jsjlog.blog.admin.dto.AdminCommentModerationResponse;
import me.jsjlog.blog.admin.dto.AdminCommentReportResponse;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentModeration;
import me.jsjlog.blog.post.domain.CommentModerationAction;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.repository.CommentModerationRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminCommentService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final AdminCommentQueryRepository adminCommentQueryRepository;
    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final CommentModerationRepository commentModerationRepository;
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
            AdminCommentVisibilityRequest request,
            Long adminId
    ) {
        if (request == null || request.hidden() == null) {
            throw new BlogException(ErrorCode.COMMENT_VISIBILITY_REQUIRED);
        }

        Comment comment = findComment(commentId);

        // delete() 가 아니다. 그건 글쓴이가 지울 때 쓰는 자리라, 여기서 부르면
        // 가려진 사람이 자기가 지운 줄 알게 된다
        if (request.hidden()) {
            comment.hideByAdmin();
        } else {
            comment.restore();
        }

        record(comment, adminId, request.hidden()
                ? CommentModerationAction.HIDE
                : CommentModerationAction.RESTORE, request.reason());
    }

    /**
     * 신고를 봤지만 댓글은 그대로 둔다.
     *
     * <p>이 자리가 없으면 "문제 없음" 을 남길 방법이 없어서, 같은 신고를 볼 때마다
     * 처음부터 다시 읽게 된다.</p>
     */
    @Transactional
    public void dismissReports(Long commentId, String reason, Long adminId) {
        record(findComment(commentId), adminId, CommentModerationAction.DISMISS, reason);
    }

    /**
     * 댓글 하나를 판단하는 데 필요한 것.
     *
     * <p>신고 내역과 조치 이력을 함께 준다. 이미 가린 댓글인 줄 모르고 또 가리는 일을
     * 막으려면 둘을 같이 봐야 한다.</p>
     */
    @Transactional(readOnly = true)
    public AdminCommentModerationDetail getModerationDetail(Long commentId) {
        findComment(commentId);

        List<AdminCommentReportResponse> reports = commentReportRepository
                .findByCommentIdOrderByIdAsc(commentId)
                .stream()
                .map(AdminCommentReportResponse::from)
                .toList();

        List<AdminCommentModerationResponse> moderations = commentModerationRepository
                .findByCommentIdOrderByActedAtDescIdDesc(commentId)
                .stream()
                .map(AdminCommentModerationResponse::from)
                .toList();

        return new AdminCommentModerationDetail(commentId, reports, moderations);
    }

    /**
     * 조치를 남기고, 이 댓글에 걸린 미처리 신고를 모두 판단한 것으로 바꾼다.
     *
     * <p>무엇을 했든 운영자가 이 댓글을 본 것이므로, 조치와 신고 처리를 따로 누르게 하지
     * 않는다. 두 번 눌러야 하면 한쪽을 빠뜨리고 목록에 계속 남는다.</p>
     */
    private void record(Comment comment, Long adminId, CommentModerationAction action, String reason) {
        Member admin = findAdmin(adminId);

        commentModerationRepository.save(CommentModeration.of(comment, admin, action, reason));

        for (CommentReport report : commentReportRepository
                .findByCommentIdAndHandledAtIsNull(comment.getId())) {
            report.markHandled();
        }
    }

    private Member findAdmin(Long adminId) {
        if (adminId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        return memberRepository.findById(adminId)
                .orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));
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
