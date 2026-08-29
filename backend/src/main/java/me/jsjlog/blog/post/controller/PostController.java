package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.post.dto.*;
import me.jsjlog.blog.post.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;

    @GetMapping("/blog/home/posts")
    public ApiResponse<List<PostSummaryResponse>> getPostsForHomePage(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "4") Long size
    ) {
        List<PostSummaryResponse> postServicePostsForHomePage = postService.getPostsForHomePage(sort, size);
        return ApiResponse.ok(postServicePostsForHomePage);
    }

    @GetMapping("/blog/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(@PathVariable Long postId) {
        PostDetailResponse postDetail = postService.getPostDetail(postId);
        return ApiResponse.ok(postDetail);
    }

    @GetMapping("/blog/posts/{postId}/related")
    public ApiResponse<List<PostSummaryResponse>> getPostDetailRelated(@PathVariable Long postId) {
        List<PostSummaryResponse> relatedPosts = postService.getRelatedPosts(postId);
        return ApiResponse.ok(relatedPosts);
    }

    @GetMapping("/blog/posts/{postId}/adjacent")
    public ApiResponse<AdjacentPostResponse> getAdjacentPost(@PathVariable Long postId) {
        AdjacentPostResponse adjacentPost = postService.getAdjacentPost(postId);

        return ApiResponse.ok(adjacentPost);
    }

    @GetMapping("/blog/posts/{postId}/comments")
    public ApiResponse<CommentListResponse> getCommentsOfPostDetail(
            @PathVariable Long postId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") Long size
    ) {
        CommentListResponse comments = postService.getCommentInPostDetail(postId, cursor, size);
        return ApiResponse.ok(comments);
    }


}
