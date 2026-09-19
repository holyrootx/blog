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
        long likeCount,
        long dislikeCount,
        boolean likedByMe,
        boolean dislikedByMe,
        List<CommentReplyResponse> replies
) {
}
