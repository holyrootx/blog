package com.jsj.blog.home.dto;

import com.jsj.blog.home.domain.HomePageHero;

public record HomePageHeroResponse(
        Long id,
        String subTitle,
        String title,
        String intro,
        String heroImageUrl
) {
    public static HomePageHeroResponse from(HomePageHero homePageHero) {
        return new HomePageHeroResponse(
                homePageHero.getId(),
                homePageHero.getSubTitle(),
                homePageHero.getTitle(),
                homePageHero.getIntro(),
                homePageHero.getHeroImageUrl()
        );
    }

}
