package me.jsjlog.blog.post.dto;

import java.time.OffsetDateTime;

/** 글 카드에 "최근 본 글" 을 붙이는 데 쓴다. 본 적 없는 글은 목록에 없다 */
public record ViewedPostResponse(
        Long postId,
        OffsetDateTime lastViewedAt
) {
}
