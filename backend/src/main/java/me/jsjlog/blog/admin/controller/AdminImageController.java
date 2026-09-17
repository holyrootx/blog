package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import me.jsjlog.blog.admin.dto.UploadImageResponse;
import me.jsjlog.blog.admin.service.AdminImageService;
import me.jsjlog.blog.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/blog")
@RequiredArgsConstructor
public class AdminImageController {

    private final AdminImageService adminImageService;

    @PostMapping("/images")
    public ApiResponse<UploadImageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(adminImageService.upload(file));
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long imageId) {
        adminImageService.delete(imageId);

        return ApiResponse.ok();
    }
}
