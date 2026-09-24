package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.CommentReportReason;

/**
 * 댓글 신고 요청.
 *
 * <p>{@code detail} 은 사유가 기타일 때 쓰는 한 줄 설명이다. 없어도 된다 —
 * 광고나 욕설은 댓글을 열어 보면 바로 알 수 있어서 덧붙일 말이 딱히 없다.</p>
 */
public record CommentReportRequest(
        CommentReportReason reason,
        String detail
) {
}
