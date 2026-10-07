package me.jsjlog.blog.post.dto;

/** 알림이 가리키는 댓글과 부모 스레드. 답글 커서는 원래 첫 페이지의 연속 구간을 유지한다. */
public record CommentContextResponse(Long targetCommentId, CommentItemResponse item) {
}
