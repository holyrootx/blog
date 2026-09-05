package me.jsjlog.blog.home.controller;

import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.home.dto.HomePageHeroResponse;
import me.jsjlog.blog.home.service.HomePageHeroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1")
public class HomePageHeroController {

    private final HomePageHeroService homePageHeroService;

    @GetMapping("/blog/home/hero")
    public ApiResponse<HomePageHeroResponse> getHomePageHero(@RequestParam(defaultValue = "1") Long homePageHeroId){
        HomePageHeroResponse homePageHeroResponse = homePageHeroService.getHomePageHero(homePageHeroId);
        return ApiResponse.ok(homePageHeroResponse);
    }

    @PutMapping("/admin/blog/home/hero")
    public ApiResponse<?> updateHomePageHero(){

        return ApiResponse.ok();
    }

}
