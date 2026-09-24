package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.dto.*;

import java.util.List;

public interface PostRepositoryCustom {

    List<PostSummaryResponse> getLatestPostsForHomePage();
    List<PostSummaryResponse> getPopularPostsForHomePage();
    List<PostSummaryResponse> getPostsForHomePage(String sort, Long size);
    PostDetailResponse getPostDetail(Long postId);
    AdjacentPostResponse getAdjacentPost(Long postId);
    List<PostSummaryResponse> getRelatedPosts(Long postId, Long categoryId);

    /** 공개 글 목록 한 페이지 */
    List<PostSummaryResponse> getPublicPosts(PostListCondition condition);

    /** 같은 조건의 전체 개수. 화면이 마지막 페이지를 알아야 한다 */
    long countPublicPosts(PostListCondition condition);

    /** 검색창 아래에 바로 띄울 글 몇 건 */
    List<PostSuggestResponse> getPublicPostSuggestions(String keyword, int size);
}
