package me.jsjlog.blog.admin.dto;

public record AdminMemberStatusCounts(
        long all,
        long active,
        long suspended,
        long withdrawn
) {
}
