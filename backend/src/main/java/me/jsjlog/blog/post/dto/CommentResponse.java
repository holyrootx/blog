package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        String nickname,
        String content,
        LocalDateTime createdAt,
        List<CommentResponse> replies
) {
}
