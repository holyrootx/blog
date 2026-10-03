package me.jsjlog.blog.history.domain;

import java.time.LocalDateTime;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;

public record PostSnapshot(String title, String excerpt, String content, Long categoryId,
                           String thumbnailImageUrl, Long authorId, PostStatus status,
                           LocalDateTime publishedAt, LocalDateTime deletedAt, LocalDateTime createdAt) {
    public static PostSnapshot from(Post post) {
        return new PostSnapshot(post.getTitle(), post.getExcerpt(), post.getContent(),
                post.getCategory().getId(), post.getThumbnailImageUrl(),
                post.getAuthor() == null ? null : post.getAuthor().getId(),
                post.getStatus(), post.getPublishedAt(), post.getDeletedAt(), post.getCreatedAt());
    }
}
