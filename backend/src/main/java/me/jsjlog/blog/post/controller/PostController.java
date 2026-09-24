package me.jsjlog.blog.post.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.post.dto.*;
import me.jsjlog.blog.post.service.PostService;
import me.jsjlog.blog.post.service.PostViewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;
    private final PostViewService postViewService;

    @GetMapping("/blog/home/posts")
    public ApiResponse<List<PostSummaryResponse>> getPostsForHomePage(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "4") Long size
    ) {
        List<PostSummaryResponse> postServicePostsForHomePage = postService.getPostsForHomePage(sort, size);
        return ApiResponse.ok(postServicePostsForHomePage);
    }


    /**
     * 공개 글 목록. 대문의 "전체 보기" 와 상단 카테고리가 여기로 온다.
     *
     * 조회조건을 객체로 받는다 — 나중에 태그나 검색이 붙어도 메서드 시그니처가 흔들리지 않는다.
     */
    @GetMapping("/blog/posts")
    public ApiResponse<PostListResponse> getPosts(PostListCondition condition) {
        return ApiResponse.ok(postService.getPosts(condition));
    }

    /**
     * 검색창 아래에 바로 띄울 글 몇 건.
     *
     * 목록({@code /blog/posts?q=})과 따로 둔 이유는 두 가지다. 타자마다 불리므로 개수 세기와
     * 페이지 계산을 안 하고, 검색 기록도 여기서는 남기지 않는다 — 덜 친 말까지 다 쌓이면
     * 나중에 인기 검색어를 뽑을 수 없다.
     *
     * 이 경로는 {@code /blog/posts/{postId}} 보다 먼저 잡힌다. 스프링이 변수 자리보다
     * 글자 그대로인 경로를 먼저 보기 때문이다.
     */
    @GetMapping("/blog/posts/suggest")
    public ApiResponse<List<PostSuggestResponse>> getPostSuggestions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(postService.getPostSuggestions(q, size));
    }

    @GetMapping("/blog/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(
            @PathVariable Long postId,
            HttpSession session
    ) {
        PostDetailResponse postDetail = postViewService.getPostDetail(postId, session);
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
            @RequestParam(defaultValue = "20") Long size,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        Long memberId = principal == null ? null : principal.getId();
        CommentListResponse comments = postService.getCommentInPostDetail(postId, cursor, size, memberId);
        return ApiResponse.ok(comments);
    }


}
