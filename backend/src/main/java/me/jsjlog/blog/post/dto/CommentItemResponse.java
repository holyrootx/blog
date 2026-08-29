package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommentItemResponse(
        Long id,
        String nickname,
        String content,
        LocalDateTime createdAt,
        boolean authorComment,
        boolean deleted,
        List<CommentReplyResponse> replies
) {
}
