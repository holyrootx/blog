package me.jsjlog.blog.post.dto;

import java.util.List;

public record CommentReplyListResponse(List<CommentReplyResponse> items, Long nextCursor, boolean hasNext) {
}
