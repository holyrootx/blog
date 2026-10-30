package me.jsjlog.blog.admin.dto;

public record AdminGuestbookCounts(
		long all,
		long reported,
		long hidden,
		long deleted
) {
}
