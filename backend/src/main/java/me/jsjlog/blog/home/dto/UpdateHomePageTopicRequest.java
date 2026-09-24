package me.jsjlog.blog.home.dto;

import java.util.List;

public record UpdateHomePageTopicRequest(
        Long id,
        String label,
        String title,
        String description,
        List<String> keywords
) {
}
