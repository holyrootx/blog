package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminImageCleanupRequest(List<Long> imageIds) {
}
