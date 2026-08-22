package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.PostStatus;

import java.time.LocalDateTime;

public record PostResponse(Long id,
                           String title,
                           String excerpt,
                           String content,
                           Long categoryId,
                           String categoryName,
                           String thumbnailImageUrl,
                           PostStatus status,
                           LocalDateTime publishedAt,
                           long views,
                           long likeCount
) {
}
