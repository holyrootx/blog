package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.PostStatus;

import java.time.LocalDateTime;

public record PostDetailResponse(
        Long id,
        String title,
        String excerpt,
        String content,
        Long categoryId,
        String categoryName,
        String thumbnailImageUrl,
        PostStatus status,
        LocalDateTime publishedAt,
        Long views,
        Long likeCount
) {
}