package me.jsjlog.blog.post.dto;

import java.time.OffsetDateTime;

public record RecentPostResponse(
        Long postId,
        String title,
        String categoryName,
        String thumbnailImageUrl,
        OffsetDateTime lastViewedAt
) {
}
