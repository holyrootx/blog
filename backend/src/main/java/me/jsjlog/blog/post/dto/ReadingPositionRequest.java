package me.jsjlog.blog.post.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import me.jsjlog.blog.post.domain.PostReadPosition;

/**
 * 읽던 자리 저장.
 *
 * @param blockIndex  본문 맨 위 덩어리부터 센 순번
 * @param blockOffset 그 덩어리 안에서의 위치. 천분율
 * @param blockHash   그 덩어리 글자로 만든 지문. 화면이 만든 16진수 8자리
 */
public record ReadingPositionRequest(
        @NotNull @Min(0) @Max(100_000) Integer blockIndex,
        @NotNull @Min(0) @Max(PostReadPosition.MAX_BLOCK_OFFSET) Integer blockOffset,
        @NotNull @Pattern(regexp = "[0-9a-f]{8}") String blockHash
) {
}
