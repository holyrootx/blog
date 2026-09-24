package me.jsjlog.blog.home.dto;

import me.jsjlog.blog.home.domain.HomePageTopicSection;

public record HomePageTopicSectionResponse(
        Long id,
        String title,
        String intro,
        String noteBadge,
        String note
) {
    public static HomePageTopicSectionResponse from(HomePageTopicSection section) {
        return new HomePageTopicSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getIntro(),
                section.getNoteBadge(),
                section.getNote()
        );
    }
}
