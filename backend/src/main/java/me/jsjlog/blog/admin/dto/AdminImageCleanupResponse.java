package me.jsjlog.blog.admin.dto;

public record AdminImageCleanupResponse(
		int requestedCount,
		int deletedCount,
		int skippedUsedCount,
		int notFoundCount,
		int failedCount
) {

	public static AdminImageCleanupResponse empty() {
		return new AdminImageCleanupResponse(0, 0, 0, 0, 0);
	}
}
