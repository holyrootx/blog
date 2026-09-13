package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 답변을 기다리는 댓글.
 * 관리자 화면이라 비밀 댓글 본문도 그대로 내려준다.
 */
public record AdminUnansweredCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String guestNickname,
        String content,
        LocalDateTime createdAt
) {
}
