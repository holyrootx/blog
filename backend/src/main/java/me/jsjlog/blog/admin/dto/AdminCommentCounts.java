package me.jsjlog.blog.admin.dto;

public record AdminCommentCounts(
        long all,
        long unanswered,
        long hidden
) {
}
