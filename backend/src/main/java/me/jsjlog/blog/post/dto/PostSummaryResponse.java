package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;

public record PostSummaryResponse(Long id,
                                  String title,
                                  Long categoryId,
                                  String categoryName,
                                  String thumbnailImageUrl,
                                  LocalDateTime publishedAt,
                                  long views
) {
}
