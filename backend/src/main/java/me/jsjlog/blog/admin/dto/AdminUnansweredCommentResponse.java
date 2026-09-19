package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 답변을 기다리는 댓글.
 */
public record AdminUnansweredCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String nickname,
        String content,
        LocalDateTime createdAt
) {
}
