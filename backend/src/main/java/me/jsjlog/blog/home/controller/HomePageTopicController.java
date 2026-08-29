package me.jsjlog.blog.home.controller;

import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.home.dto.HomePageTopicResponse;
import me.jsjlog.blog.home.service.HomePageTopicService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1")
public class HomePageTopicController {

    private final HomePageTopicService homePageTopicService;

    @GetMapping("/blog/home/topics")
    public ApiResponse<List<HomePageTopicResponse>> getHomePageTopics() {
        List<HomePageTopicResponse> homePageTopicResponseList = homePageTopicService.getHomePageTopics();
        return ApiResponse.ok(homePageTopicResponseList);
    }

}
