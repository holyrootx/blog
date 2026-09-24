package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.post.domain.PostStatus;

import java.time.LocalDateTime;

public record PostDetailResponse(
        Long id,
        String title,
        String excerpt,
        String content,
        Long categoryId,
        String categoryName,
        String thumbnailImageUrl,
        PostStatus status,
        LocalDateTime publishedAt,
        Long views,
        Long likeCount,
        Long dislikeCount,
        boolean likedByMe,
        boolean dislikedByMe
) {

    /** QueryDSL 상세 조회가 사용하는 기본 생성자. 반응 정보는 서비스에서 회원 기준으로 채운다. */
    public PostDetailResponse(
            Long id,
            String title,
            String excerpt,
            String content,
            Long categoryId,
            String categoryName,
            String thumbnailImageUrl,
            PostStatus status,
            LocalDateTime publishedAt,
            Long views,
            Long likeCount
    ) {
        this(
                id,
                title,
                excerpt,
                content,
                categoryId,
                categoryName,
                thumbnailImageUrl,
                status,
                publishedAt,
                views,
                likeCount,
                0L,
                false,
                false
        );
    }

    public PostDetailResponse withReactions(PostReactionResponse reactions) {
        return new PostDetailResponse(
                id,
                title,
                excerpt,
                content,
                categoryId,
                categoryName,
                thumbnailImageUrl,
                status,
                publishedAt,
                views,
                reactions.likeCount(),
                reactions.dislikeCount(),
                reactions.likedByMe(),
                reactions.dislikedByMe()
        );
    }
}
