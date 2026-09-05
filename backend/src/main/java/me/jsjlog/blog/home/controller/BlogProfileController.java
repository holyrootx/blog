package me.jsjlog.blog.home.controller;

import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.home.dto.BlogProfileResponse;
import me.jsjlog.blog.home.dto.UpdateBlogProfileRequest;
import me.jsjlog.blog.home.service.BlogProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1")
public class BlogProfileController {

    private final BlogProfileService blogProfileService;

    @GetMapping("/blog/profile")
    public ApiResponse<BlogProfileResponse> getBlogProfile(@RequestParam(defaultValue = "1") Long profileId){

        BlogProfileResponse blogProfile = blogProfileService.getBlogProfile(profileId);

        return ApiResponse.ok(blogProfile);
    }


    @PutMapping("/admin/blog/profile")
    public ApiResponse<?> updateBlogProfile(@RequestBody UpdateBlogProfileRequest updateBlogProfileRequest){

        blogProfileService.updateBlogProfile(updateBlogProfileRequest);

        return ApiResponse.ok();
    }
}
