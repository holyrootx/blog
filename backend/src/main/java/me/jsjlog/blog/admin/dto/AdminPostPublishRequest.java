package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 발행 요청.
 *
 * publishedAt 이 비어 있으면 지금 발행한다. 미래 시각을 주면 그때까지 공개 목록에 나오지 않는다 —
 * 공개 조회가 이미 {@code publishedAt <= now} 로 거르기 때문에 따로 스케줄러가 필요하지 않다.
 */
public record AdminPostPublishRequest(LocalDateTime publishedAt) {
}
