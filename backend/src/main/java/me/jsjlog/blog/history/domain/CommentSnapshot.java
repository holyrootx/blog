package me.jsjlog.blog.history.domain;

import java.time.LocalDateTime;
import me.jsjlog.blog.post.domain.Comment;

public record CommentSnapshot(String content, Long memberId, Long parentId, boolean deleted,
                              boolean hiddenByAdmin, boolean edited, LocalDateTime createdAt) {
    public static CommentSnapshot from(Comment comment) {
        return new CommentSnapshot(comment.getContent(), comment.getMember().getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.isDeleted(), comment.isHiddenByAdmin(), comment.isEdited(), comment.getCreatedAt());
    }
}
