package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.PostStatus;

import java.time.LocalDateTime;

/**
 * 편집 화면이 읽는 글 한 건.
 *
 * views·likeCount 는 내려주지 않는다. 글을 쓰는 동안 아무 판단에도 쓰이지 않는다.
 *
 * updatedAt 은 로컬 임시 보관 스냅샷이 어느 서버 값을 기준으로 만들어졌는지
 * 화면이 비교하는 데 쓴다.
 */
public record AdminPostDetailResponse(
        Long id,
        String title,
        Long categoryId,
        String categoryName,
        String excerpt,
        String content,
        String thumbnailImageUrl,
        PostStatus status,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt
) {
}
