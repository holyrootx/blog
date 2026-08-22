package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.post.dto.CategoryResponse;
import me.jsjlog.blog.post.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/blog/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(){
        List<CategoryResponse> categories = categoryService.getCategories();
        return ApiResponse.ok(categories);
    }



}
