package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.CommentCreateResponse;
import me.jsjlog.blog.post.dto.CommentReactionRequest;
import me.jsjlog.blog.post.dto.CommentReactionResponse;
import me.jsjlog.blog.post.dto.CommentReportRequest;
import me.jsjlog.blog.post.dto.CommentReportResponse;
import me.jsjlog.blog.post.dto.CommentUpdateRequest;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.service.CommentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blog")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentCreateResponse> createComment(
            @PathVariable Long postId,
            @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(commentService.createComment(postId, request, principalId(principal)));
    }

    /** 내 댓글 고치기. 남의 댓글은 관리자라도 여기로 고칠 수 없다 */
    @PutMapping("/comments/{commentId}")
    public ApiResponse<Void> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        commentService.updateComment(
                commentId,
                request == null ? null : request.content(),
                principalId(principal)
        );

        return ApiResponse.ok(null);
    }

    /**
     * 내 댓글 지우기.
     *
     * 레코드는 남고 표시만 바뀐다. 답글이 달린 댓글을 실제로 지우면 답글이 부모를 잃는다.
     */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        commentService.deleteComment(commentId, principalId(principal));

        return ApiResponse.ok(null);
    }

    /**
     * 댓글 신고. 로그인한 회원만 할 수 있다.
     *
     * 익명으로 열어 두면 한 사람이 창을 새로 열어 가며 몇 번이고 신고할 수 있어서,
     * 신고 수가 아무 뜻도 없는 숫자가 된다.
     */
    @PostMapping("/comments/{commentId}/reports")
    public ApiResponse<CommentReportResponse> reportComment(
            @PathVariable Long commentId,
            @RequestBody CommentReportRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(commentService.reportComment(
                commentId,
                request == null ? null : request.reason(),
                request == null ? null : request.detail(),
                principalId(principal)
        ));
    }

    @PutMapping("/comments/{commentId}/reaction")
    public ApiResponse<CommentReactionResponse> setReaction(
            @PathVariable Long commentId,
            @RequestBody CommentReactionRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(commentService.setReaction(
                commentId,
                request == null ? null : request.type(),
                principalId(principal)
        ));
    }

    @DeleteMapping("/comments/{commentId}/reaction")
    public ApiResponse<CommentReactionResponse> removeReaction(
            @PathVariable Long commentId,
            @RequestParam CommentReactionType type,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(commentService.removeReaction(
                commentId,
                type,
                principalId(principal)
        ));
    }

    private Long principalId(MemberPrincipal principal) {
        return principal == null ? null : principal.getId();
    }
}
