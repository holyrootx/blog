package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;

public record CommentReplyResponse(
        Long id,
        String nickname,
        String content,
        LocalDateTime createdAt,
        boolean authorComment,
        boolean deleted
) {
}