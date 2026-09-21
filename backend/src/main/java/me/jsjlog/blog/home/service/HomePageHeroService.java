package me.jsjlog.blog.home.service;


import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.home.domain.HomePageHero;
import me.jsjlog.blog.home.dto.HomePageHeroResponse;
import me.jsjlog.blog.home.dto.UpdateHomePageHeroRequest;
import me.jsjlog.blog.home.repository.HomePageHeroRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomePageHeroService {

    private static final int TITLE_MAX_LENGTH = 255;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    private final HomePageHeroRepository homePageHeroRepository;

    public HomePageHeroResponse getHomePageHero(Long homePageHeroId) {
        return HomePageHeroResponse.from(findHero(homePageHeroId));
    }

    @Transactional
    public HomePageHeroResponse updateHomePageHero(UpdateHomePageHeroRequest request) {
        validate(request);

        HomePageHero homePageHero = findHero(request.id());
        homePageHero.update(
                request.subTitle().trim(),
                request.title().trim(),
                request.intro().trim(),
                blankToNull(request.heroImageUrl())
        );

        return HomePageHeroResponse.from(homePageHero);
    }

    private HomePageHero findHero(Long homePageHeroId) {
        if (homePageHeroId == null) {
            throw new BlogException(ErrorCode.MAIN_HERO_NOT_FOUND);
        }

        return homePageHeroRepository.findById(homePageHeroId)
                .orElseThrow(() -> new BlogException(ErrorCode.MAIN_HERO_NOT_FOUND));
    }

    private void validate(UpdateHomePageHeroRequest request) {
        if (request == null
                || !StringUtils.hasText(request.subTitle())
                || !StringUtils.hasText(request.title())
                || !StringUtils.hasText(request.intro())) {
            throw new BlogException(ErrorCode.MAIN_HERO_REQUIRED_VALUE_MISSING);
        }

        if (request.subTitle().trim().length() > TITLE_MAX_LENGTH
                || request.title().trim().length() > TITLE_MAX_LENGTH
                || length(request.heroImageUrl()) > IMAGE_URL_MAX_LENGTH) {
            throw new BlogException(ErrorCode.MAIN_HERO_VALUE_TOO_LONG);
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
