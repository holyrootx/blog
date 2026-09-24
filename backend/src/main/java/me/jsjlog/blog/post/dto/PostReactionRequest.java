package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.PostReactionType;

public record PostReactionRequest(
        PostReactionType type
) {
}
