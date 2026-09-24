package me.jsjlog.blog.home.dto;

import java.util.List;

public record HomePageTopicSettingsResponse(
        HomePageTopicSectionResponse section,
        List<HomePageTopicResponse> topics
) {
}
