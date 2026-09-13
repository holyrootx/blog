package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminPostDetailResponse;
import me.jsjlog.blog.admin.dto.AdminPostListResponse;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.service.AdminPostService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/blog")
@Slf4j
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    // GET /api/v1/admin/blog/posts?page=0&size=20&status=DRAFT&categoryId=1&keyword=배포
    @GetMapping("/posts")
    public ApiResponse<AdminPostListResponse> getPosts(AdminPostSearchCondition condition) {
        AdminPostListResponse posts = adminPostService.getPostList(condition);
        return ApiResponse.ok(posts);
    }

    // GET /api/v1/admin/blog/posts/{postId}
    @GetMapping("/posts/{postId}")
    public ApiResponse<AdminPostDetailResponse> getPost(@PathVariable Long postId) {
        return ApiResponse.ok(adminPostService.getPost(postId));
    }

    // POST /api/v1/admin/blog/posts
    @PostMapping("/posts")
    public ApiResponse<Long> createPost(@RequestBody AdminPostRequest request) {
        return ApiResponse.ok(adminPostService.createPost(request));
    }

    // PUT /api/v1/admin/blog/posts/{postId}
    @PutMapping("/posts/{postId}")
    public ApiResponse<Void> updatePost(@PathVariable Long postId,
                                        @RequestBody AdminPostRequest request) {
        adminPostService.updatePost(postId, request);
        return ApiResponse.ok();
    }

    // POST /api/v1/admin/blog/posts/{postId}/publish
    // 수정(PUT)에 status 를 섞지 않고 나눈 이유는 발행이 publishedAt 을 건드리는
    // 부수효과를 갖기 때문이다. 저장할 때마다 값이 바뀌면 사고가 난다
    @PostMapping("/posts/{postId}/publish")
    public ApiResponse<Void> publishPost(@PathVariable Long postId) {
        adminPostService.publishPost(postId);
        return ApiResponse.ok();
    }

    // POST /api/v1/admin/blog/posts/{postId}/unpublish
    @PostMapping("/posts/{postId}/unpublish")
    public ApiResponse<Void> unpublishPost(@PathVariable Long postId) {
        adminPostService.unpublishPost(postId);
        return ApiResponse.ok();
    }

    // DELETE /api/v1/admin/blog/posts/{postId}
    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        adminPostService.deletePost(postId);
        return ApiResponse.ok();
    }
}
