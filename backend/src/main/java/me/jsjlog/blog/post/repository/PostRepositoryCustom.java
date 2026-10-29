package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.dto.*;

import java.util.List;

public interface PostRepositoryCustom {

	List<PostSummaryResponse> getPostsForHomePage(String sort, Long size);
	PostDetailResponse getPostDetail(Long postId);
	AdjacentPostResponse getAdjacentPost(Long postId);
	List<PostSummaryResponse> getRelatedPosts(Long postId, Long categoryId);

	List<PostSummaryResponse> getPublicPosts(PostListCondition condition);

	long countPublicPosts(PostListCondition condition);

	List<PostSuggestResponse> getPublicPostSuggestions(String keyword, int size);
}
