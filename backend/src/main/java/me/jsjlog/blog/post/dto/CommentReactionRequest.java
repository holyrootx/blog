package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.CommentReactionType;

public record CommentReactionRequest(CommentReactionType type) {
}
