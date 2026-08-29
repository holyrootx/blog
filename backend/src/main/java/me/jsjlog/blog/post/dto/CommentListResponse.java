package me.jsjlog.blog.post.dto;

import java.util.List;

public record CommentListResponse(
        long total,
        List<CommentItemResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}
