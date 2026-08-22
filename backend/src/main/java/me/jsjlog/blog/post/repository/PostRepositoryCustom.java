package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.dto.AdjacentPostResponse;
import me.jsjlog.blog.post.dto.CommentResponse;
import me.jsjlog.blog.post.dto.PostDetailResponse;
import me.jsjlog.blog.post.dto.PostSummaryResponse;

import java.util.List;

public interface PostRepositoryCustom {

    List<PostSummaryResponse> getLatestPostsForHomePage();
    List<PostSummaryResponse> getPopularPostsForHomePage();
    List<PostSummaryResponse> getPostsForHomePage(String sort, Long size);
    PostDetailResponse getPostDetail(Long postId);
    AdjacentPostResponse getAdjacentPost(Long postId);
    List<PostSummaryResponse> getRelatedPosts(Long postId, Long categoryId);
    List<CommentResponse> getCommentInPost(Long postId);
}
