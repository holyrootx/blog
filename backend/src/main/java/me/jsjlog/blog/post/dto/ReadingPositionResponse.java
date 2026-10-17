package me.jsjlog.blog.post.dto;

import me.jsjlog.blog.common.time.ServerTime;
import me.jsjlog.blog.post.domain.PostReadPosition;

import java.time.OffsetDateTime;

public record ReadingPositionResponse(
        Long postId,
        int blockIndex,
        int blockOffset,
        String blockHash,
        OffsetDateTime savedAt
) {

    public static ReadingPositionResponse from(PostReadPosition position) {
        return new ReadingPositionResponse(
                position.getPostId(),
                position.getBlockIndex(),
                position.getBlockOffset(),
                position.getBlockHash(),
                ServerTime.toOffset(position.getSavedAt())
        );
    }
}
