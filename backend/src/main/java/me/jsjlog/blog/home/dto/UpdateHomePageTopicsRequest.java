package me.jsjlog.blog.home.dto;

import java.util.List;

public record UpdateHomePageTopicsRequest(
        Long sectionId,
        String title,
        String intro,
        String noteBadge,
        String note,
        List<UpdateHomePageTopicRequest> topics
) {
}
