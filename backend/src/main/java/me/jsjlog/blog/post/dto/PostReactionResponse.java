package me.jsjlog.blog.post.dto;

public record PostReactionResponse(
        long likeCount,
        long dislikeCount,
        boolean likedByMe,
        boolean dislikedByMe
) {
}
