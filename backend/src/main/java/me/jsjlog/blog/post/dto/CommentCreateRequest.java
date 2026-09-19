package me.jsjlog.blog.post.dto;

public record CommentCreateRequest(
        String content,
        Long parentId
) {
}
