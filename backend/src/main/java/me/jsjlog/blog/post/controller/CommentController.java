package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.dto.CommentCreateResponse;
import me.jsjlog.blog.post.dto.CommentReactionRequest;
import me.jsjlog.blog.post.dto.CommentReactionResponse;
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
