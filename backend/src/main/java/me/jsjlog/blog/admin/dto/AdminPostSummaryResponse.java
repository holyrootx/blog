package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.PostStatus;

import java.time.LocalDateTime;

/**
 * 글 목록 한 줄.
 *
 * publishedAt 과 createdAt 을 함께 내려준다.
 * 임시저장 글은 publishedAt 이 null 이라 목록에 보여줄 날짜 기준이 따로 필요하다.
 *
 * 댓글 수·좋아요·썸네일은 넣지 않는다. 화면에서 쓰지 않는 값이고,
 * 항상 0이거나 비어 있는 칸은 정보가 아니라 잡음이다.
 */
public record AdminPostSummaryResponse(
        Long id,
        String title,
        Long categoryId,
        String categoryName,
        PostStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        long views
) {
}
