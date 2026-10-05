package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommentListResponse;
import me.jsjlog.blog.admin.dto.AdminCommentReplyRequest;
import me.jsjlog.blog.admin.dto.AdminCommentSearchCondition;
import me.jsjlog.blog.admin.dto.AdminCommentDetail;
import me.jsjlog.blog.admin.dto.AdminCommentModerationRequest;
import me.jsjlog.blog.admin.dto.AdminCommentVisibilityRequest;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/blog/comments")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @GetMapping
    public ApiResponse<AdminCommentListResponse> getComments(
            AdminCommentSearchCondition condition
    ) {
        return ApiResponse.ok(adminCommentService.getComments(condition));
    }

    @PostMapping("/{commentId}/replies")
    public ApiResponse<Long> reply(
            @PathVariable Long commentId,
            @RequestBody AdminCommentReplyRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(adminCommentService.reply(
                commentId,
                request,
                principal == null ? null : principal.getId()
        ));
    }

    @PutMapping("/{commentId}/visibility")
    public ApiResponse<Void> updateVisibility(
            @PathVariable Long commentId,
            @RequestBody AdminCommentVisibilityRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        adminCommentService.updateVisibility(commentId, request, principalId(principal));
        return ApiResponse.ok();
    }

    /** 댓글 상세. 지금 본문, 신고와 조치, 변경 기록 */
    @GetMapping("/{commentId}")
    public ApiResponse<AdminCommentDetail> getDetail(@PathVariable Long commentId) {
        return ApiResponse.ok(adminCommentService.getDetail(commentId));
    }

    /** 신고를 봤지만 댓글은 그대로 둔다 */
    @PostMapping("/{commentId}/reports/dismiss")
    public ApiResponse<Void> dismissReports(
            @PathVariable Long commentId,
            @RequestBody(required = false) AdminCommentModerationRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        adminCommentService.dismissReports(
                commentId,
                request == null ? null : request.reason(),
                principalId(principal)
        );

        return ApiResponse.ok();
    }

    private Long principalId(MemberPrincipal principal) {
        return principal == null ? null : principal.getId();
    }
}
