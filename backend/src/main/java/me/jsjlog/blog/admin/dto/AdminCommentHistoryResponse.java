package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;
import me.jsjlog.blog.history.domain.CommentChange;
import me.jsjlog.blog.history.domain.CommentSnapshot;

/**
 * 댓글 변경 기록 한 줄.
 *
 * 본문은 바뀐 경우에만 담는다. 작성은 쓴 내용, 수정은 전후 내용이다.
 * 삭제·가림·되돌림은 본문이 그대로라 담지 않는다.
 */
public record AdminCommentHistoryResponse(
        Long id,
        String action,
        String actionLabel,
        LocalDateTime occurredAt,
        String beforeContent,
        String afterContent
) {

    public static AdminCommentHistoryResponse from(CommentChange change) {
        String action = change.action().name();

        return switch (action) {
            case "CREATE" -> of(change, "작성", null, content(change.after()));
            case "UPDATE" -> of(change, "수정", content(change.before()), content(change.after()));
            case "DELETE" -> of(change, "삭제", null, null);
            // 조치 이력과 같은 말을 쓴다
            case "HIDE" -> of(change, "가림", null, null);
            case "RESTORE" -> of(change, "되돌림", null, null);
            default -> of(change, action, null, null);
        };
    }

    private static AdminCommentHistoryResponse of(
            CommentChange change,
            String label,
            String before,
            String after
    ) {
        return new AdminCommentHistoryResponse(
                change.id(), change.action().name(), label, change.occurredAt(), before, after);
    }

    private static String content(CommentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.content();
    }
}
