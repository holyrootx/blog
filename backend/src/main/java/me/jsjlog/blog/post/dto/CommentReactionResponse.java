package me.jsjlog.blog.post.dto;

public record CommentReactionResponse(
        long likeCount,
        long dislikeCount,
        boolean likedByMe,
        boolean dislikedByMe
) {
}
