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
import me.jsjlog.blog.admin.dto.AdminCommentDetail;
import me.jsjlog.blog.admin.dto.AdminCommentHistoryResponse;
import me.jsjlog.blog.admin.dto.AdminCommentModerationResponse;
import me.jsjlog.blog.admin.dto.AdminCommentReportResponse;
import me.jsjlog.blog.notification.service.NotificationService;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentModeration;
import me.jsjlog.blog.post.domain.CommentModerationAction;
import me.jsjlog.blog.post.domain.CommentReport;
import me.jsjlog.blog.post.repository.CommentModerationRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.service.PostAccess;
import me.jsjlog.blog.history.domain.CommentChange;
import me.jsjlog.blog.history.domain.CommentSnapshot;
import me.jsjlog.blog.history.domain.ContentHistory.Action;
import me.jsjlog.blog.history.service.ContentHistoryService;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final NotificationService notificationService;
    private final PostAccess postAccess;
    private final ContentHistoryService historyService;

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
        Comment parent = lockComment(commentId);
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

        historyService.recordComment(reply, Action.CREATE, null);
        notificationService.notifyCommentCreated(reply);
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

        Comment comment = lockComment(commentId);
        CommentSnapshot before = CommentSnapshot.from(comment);

        // delete() 가 아니다. 그건 글쓴이가 지울 때 쓰는 자리라, 여기서 부르면
        // 가려진 사람이 자기가 지운 줄 알게 된다
        if (request.hidden()) {
            comment.hideByAdmin();

            // 알리지 않으면 쓴 사람은 안 써진 줄 알고 다시 쓴다. 답글이 없는 댓글은
            // 공개 화면에서 흔적 없이 사라지기 때문이다. 되돌릴 때는 알리지 않는다 —
            // 받는 사람이 할 일이 없다
            notificationService.notifyCommentHidden(comment);
        } else {
            comment.restore();
        }

        record(comment, adminId, request.hidden()
                ? CommentModerationAction.HIDE
                : CommentModerationAction.RESTORE, request.reason());
        historyService.recordComment(comment, request.hidden() ? Action.HIDE : Action.RESTORE, before);
    }

    /**
     * 신고를 봤지만 댓글은 그대로 둔다.
     *
     * <p>이 자리가 없으면 "문제 없음" 을 남길 방법이 없어서, 같은 신고를 볼 때마다
     * 처음부터 다시 읽게 된다.</p>
     */
    @Transactional
    public void dismissReports(Long commentId, String reason, Long adminId) {
        record(lockComment(commentId), adminId, CommentModerationAction.DISMISS, reason);
    }

    /** 댓글 상세. 지금 본문, 신고와 조치, 변경 기록을 함께 준다 */
    @Transactional(readOnly = true)
    public AdminCommentDetail getDetail(Long commentId) {
        Comment comment = findComment(commentId);
        String current = comment.getContent();
        List<CommentChange> changes = historyService.commentChanges(commentId);

        List<AdminCommentReportResponse> reports = commentReportRepository
                .findByCommentIdOrderByIdAsc(commentId)
                .stream()
                .map(report -> AdminCommentReportResponse.from(
                        report,
                        contentAt(report.getCreatedAt(), current, changes),
                        current
                ))
                .toList();

        List<AdminCommentModerationResponse> moderations = commentModerationRepository
                .findByCommentIdOrderByActedAtDescIdDesc(commentId)
                .stream()
                .map(AdminCommentModerationResponse::from)
                .toList();

        return new AdminCommentDetail(
                comment.getId(),
                comment.getPost().getId(),
                comment.getPost().getTitle(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getMember().getNickname(),
                comment.getMember().getRole(),
                current,
                comment.getCreatedAt(),
                comment.isDeleted(),
                comment.isHiddenByAdmin(),
                comment.isEdited(),
                reports,
                moderations,
                changes.stream().map(AdminCommentHistoryResponse::from).toList()
        );
    }

    /**
     * 그 시각의 본문.
     *
     * 본문을 바꾸는 건 수정뿐이다. 그 시각 이후 첫 수정의 "수정 전" 이 그때 본문이고,
     * 이후 수정이 없으면 지금 본문이 그때 본문이다.
     */
    private String contentAt(LocalDateTime at, String current, List<CommentChange> changes) {
        if (at == null) {
            return current;
        }

        return changes.stream()
                .filter(change -> change.action() == Action.UPDATE && change.before() != null)
                .filter(change -> change.occurredAt() != null && !change.occurredAt().isBefore(at))
                .min(Comparator.comparing(CommentChange::occurredAt))
                .map(change -> change.before().content())
                .orElse(current);
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
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));
        postAccess.findActive(comment.getPost().getId());
        return comment;
    }

    private Comment lockComment(Long commentId) {
        Long postId = commentRepository.findPostId(commentId)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMENT_NOT_FOUND));
        postAccess.lockActive(postId);
        return commentRepository.findLockedById(commentId)
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
