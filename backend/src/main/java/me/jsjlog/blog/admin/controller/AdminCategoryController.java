package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminCategoryRequest;
import me.jsjlog.blog.admin.dto.AdminCategoryResponse;
import me.jsjlog.blog.admin.dto.AdminCategorySearchCondition;
import me.jsjlog.blog.admin.service.AdminCategoryService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/blog")
@Slf4j
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping("/categories")
    public ApiResponse<List<AdminCategoryResponse>> getCategories(AdminCategorySearchCondition condition) {
        List<AdminCategoryResponse> categories = adminCategoryService.getCategoryList(condition);
        return ApiResponse.ok(categories);
    }

    @PostMapping("/categories")
    public ApiResponse<Long> createCategory(@RequestBody AdminCategoryRequest request) {
        Long categoryId = adminCategoryService.createCategory(request);
        return ApiResponse.ok(categoryId);
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<Void> updateCategory(@PathVariable Long categoryId,
                                            @RequestBody AdminCategoryRequest request) {
        adminCategoryService.updateCategory(categoryId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        adminCategoryService.deleteCategory(categoryId);
        return ApiResponse.ok();
    }

}
