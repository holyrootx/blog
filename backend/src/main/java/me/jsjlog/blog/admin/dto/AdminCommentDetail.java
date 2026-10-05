package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import me.jsjlog.blog.member.domain.MemberRole;

/**
 * 댓글 관리에서 한 줄을 눌렀을 때 여는 창.
 *
 * 지금 모습, 신고와 조치, 변경 기록을 한 번에 준다. 신고된 댓글은 셋을 같이 봐야
 * 판단할 수 있다. 이미 가린 줄 모르고 또 가리거나, 신고 뒤에 고친 줄 모르고 넘어가는 일이 생긴다.
 */
public record AdminCommentDetail(
        Long id,
        Long postId,
        String postTitle,
        Long parentId,
        String nickname,
        MemberRole memberRole,
        String content,
        LocalDateTime createdAt,
        boolean deleted,
        boolean hiddenByAdmin,
        boolean edited,
        // 글쓴이가 지운 시각. 운영자가 가린 것만으로는 비어 있다
        LocalDateTime deletedAt,
        // 댓글 삭제 또는 원글 영구 삭제에 따른 원문 보관 기한. 파기 후에는 비어 있다.
        LocalDateTime contentRetainedUntil,
        boolean contentPurged,
        boolean postPurged,
        List<AdminCommentReportResponse> reports,
        List<AdminCommentModerationResponse> moderations,
        List<AdminCommentHistoryResponse> histories
) {
}
