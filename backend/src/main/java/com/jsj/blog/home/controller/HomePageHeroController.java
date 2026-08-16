package com.jsj.blog.home.controller;

import com.jsj.blog.common.response.ApiResponse;
import com.jsj.blog.home.dto.HomePageHeroResponse;
import com.jsj.blog.home.service.HomePageHeroService;
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

    @PutMapping("/admin/home/hero")
    public ApiResponse<?> updateHomePageHero(){

        return ApiResponse.ok();
    }

}
