package me.jsjlog.blog.history.domain;

import java.time.LocalDateTime;
import me.jsjlog.blog.history.domain.ContentHistory.Action;

/** 댓글 기록 한 줄. 저장된 JSON 을 풀어 둔 모양이다 */
public record CommentChange(Long id, Action action, LocalDateTime occurredAt,
                            CommentSnapshot before, CommentSnapshot after) {
}
