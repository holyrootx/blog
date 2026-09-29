package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import me.jsjlog.blog.admin.dto.AdminImageCleanupRequest;
import me.jsjlog.blog.admin.dto.AdminImageListResponse;
import me.jsjlog.blog.admin.dto.UploadImageResponse;
import me.jsjlog.blog.admin.service.ImageCleanupService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import me.jsjlog.blog.admin.service.AdminImageService;
import me.jsjlog.blog.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/blog")
@RequiredArgsConstructor
public class AdminImageController {

    private final AdminImageService adminImageService;
    private final ImageCleanupService imageCleanupService;

    @PostMapping("/images")
    public ApiResponse<UploadImageResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(adminImageService.upload(file));
    }

    /** 이미지 목록. 올린 지 일주일 안 된 것은 빼고 준다 */
    @GetMapping("/images")
    public ApiResponse<AdminImageListResponse> images(
            @RequestParam(defaultValue = "false") boolean onlyUnused,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(imageCleanupService.findAll(
                onlyUnused, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    /** 고른 것을 지운다. 지우기 직전에 참조를 한 번 더 본다 */
    @PostMapping("/images/cleanup")
    public ApiResponse<Void> cleanup(@RequestBody AdminImageCleanupRequest request) {
        imageCleanupService.cleanup(request.imageIds());

        return ApiResponse.ok();
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long imageId) {
        adminImageService.delete(imageId);

        return ApiResponse.ok();
    }
}
