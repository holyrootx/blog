package me.jsjlog.blog.home.dto;

public record UpdateHomePageHeroRequest(
        Long id,
        String subTitle,
        String title,
        String intro,
        String heroImageUrl
) {
}
