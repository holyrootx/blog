package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.dto.AdjacentPostResponse;
import me.jsjlog.blog.post.dto.PostDetailResponse;
import me.jsjlog.blog.post.dto.PostSummaryResponse;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    public List<PostSummaryResponse> getLatestPostsForHomePage(){
        return postRepository.getLatestPostsForHomePage();
    }

    public List<PostSummaryResponse> getPopularPostsForHomePage(){
        return postRepository.getPopularPostsForHomePage();
    }

    public List<PostSummaryResponse> getPostsForHomePage(String sort, Long size){
        String POPULAR_PARAM = "popular";
        String LATEST_PARAM = "latest";

        if (!POPULAR_PARAM.equals(sort) && !LATEST_PARAM.equals(sort)) {
            throw new BlogException(ErrorCode.POST_SORT_INVALID);
        }

        if (size ==  null || size <= 0L) {
            size = 4L;
            log.info("[getPostsForHomePage] Size is Null or Zero => size = {}, " +
                    "so we change to default Value : 4L " +
                    ", you have to check it ", size);
        }

        if (size > 50L) {
            log.warn("[getPostsForHomePage] Size exceeds max size. size = {}, maxSize = 50", size);
            throw new BlogException(ErrorCode.POST_SIZE_LIMIT_EXCEEDED);
        }

        return postRepository.getPostsForHomePage(sort,size);
    }

    public PostDetailResponse getPostDetail(Long postId) {

        Optional<Post> byId = postRepository.findById(postId);
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        Post post = byId.get();
        if (post.getStatus() == PostStatus.PRIVATE || post.getStatus() == PostStatus.DRAFT){
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        return postRepository.getPostDetail(postId);
    }

    public AdjacentPostResponse getAdjacentPost(Long postId) {
        Optional<Post> byId = postRepository.findById(postId);
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        Post post = byId.get();
        if (post.getStatus() == PostStatus.PRIVATE || post.getStatus() == PostStatus.DRAFT){
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        return postRepository.getAdjacentPost(postId);
    }

    public List<PostSummaryResponse> getRelatedPosts(Long postId) {

        Optional<Post> byId = postRepository.findById(postId);
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        Post post = byId.get();
        if (post.getStatus() == PostStatus.PRIVATE || post.getStatus() == PostStatus.DRAFT){
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }
        Long categoryId = post.getCategory().getId();
        return postRepository.getRelatedPosts(postId, categoryId);
    }

}
