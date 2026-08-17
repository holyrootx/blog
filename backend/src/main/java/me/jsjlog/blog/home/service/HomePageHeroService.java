package me.jsjlog.blog.home.service;


import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.home.domain.HomePageHero;
import me.jsjlog.blog.home.dto.HomePageHeroResponse;
import me.jsjlog.blog.home.repository.HomePageHeroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomePageHeroService {

    private final HomePageHeroRepository homePageHeroRepository;

    public HomePageHeroResponse getHomePageHero(Long homePageHeroId) {

        Optional<HomePageHero> byId = homePageHeroRepository.findById(homePageHeroId);
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.MAIN_HERO_NOT_FOUND);
        }

        HomePageHero homePageHero = byId.get();
        return HomePageHeroResponse.from(homePageHero);
    }

}
