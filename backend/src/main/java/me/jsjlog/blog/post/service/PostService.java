package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostReaction;
import me.jsjlog.blog.post.domain.PostReactionType;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.dto.*;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostReactionRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.search.service.SearchLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private static final long DEFAULT_COMMENT_PAGE_SIZE = 20L;
    private static final long MAX_COMMENT_PAGE_SIZE = 50L;

    private static final int DEFAULT_SUGGEST_SIZE = 5;
    private static final int MAX_SUGGEST_SIZE = 10;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostReactionRepository postReactionRepository;
    private final MemberRepository memberRepository;
    private final SearchLogService searchLogService;

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

    /**
     * 지금 독자에게 보여도 되는 글인지.
     *
     * 상태 하나만 본다. 예약한 글은 시각이 될 때까지 SCHEDULED 로 남아 있고,
     * DRAFT·PRIVATE 와 마찬가지로 여기서 걸린다.
     *
     * 없는 글과 공개되지 않은 글에 같은 응답을 주는 이유는, 응답이 갈리면
     * 아직 공개하지 않은 글의 존재를 알려주는 꼴이 되기 때문이다.
     */
    private Post findReadablePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND));

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    /**
     * 공개 글 목록. 검색어가 있으면 검색 결과이기도 하다.
     *
     * 목록과 개수를 따로 조회한다. 한 방에 세려면 전체를 읽어야 하는데, 화면은 한 페이지만 쓴다.
     */
    @Transactional(readOnly = true)
    public PostListResponse getPosts(PostListCondition condition) {
        long total = postRepository.countPublicPosts(condition);

        recordSearch(condition, total);

        return PostListResponse.of(
                postRepository.getPublicPosts(condition),
                condition.pageOrDefault(),
                condition.sizeOrDefault(),
                total
        );
    }

    /**
     * 검색창 아래에 바로 뜨는 글 몇 건.
     *
     * 여기서는 기록을 남기지 않는다. 타자 한 자마다 불리는 자리라 "스", "스프", "스프링" 이
     * 전부 쌓이는데, 사람이 실제로 찾은 말은 마지막 하나뿐이다.
     */
    @Transactional(readOnly = true)
    public List<PostSuggestResponse> getPostSuggestions(String keyword, Integer size) {
        PostListCondition condition = new PostListCondition(null, null, null, null, keyword);

        if (!condition.hasKeyword()) {
            return List.of();
        }

        int limit = size == null || size < 1 ? DEFAULT_SUGGEST_SIZE : Math.min(size, MAX_SUGGEST_SIZE);

        return postRepository.getPublicPostSuggestions(condition.keywordOrNull(), limit);
    }

    /**
     * 첫 페이지에서만 남긴다.
     *
     * 뒷장으로 넘길 때마다 쌓으면 같은 검색이 여러 번 센 것이 되어, 나중에 인기 검색어를
     * 뽑을 때 페이지를 많이 넘긴 검색어가 인기 있는 것처럼 보인다.
     */
    private void recordSearch(PostListCondition condition, long total) {
        if (condition.hasKeyword() && condition.pageOrDefault() == 0) {
            searchLogService.record(condition.keywordOrNull(), total);
        }
    }

    /** 조회수를 올리므로 쓰기 트랜잭션이 필요하다 */
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, boolean increaseViewCount, Long memberId) {

        findReadablePost(postId);

        if (increaseViewCount) {
            int updatedRows = postRepository.increaseViewCount(postId, PostStatus.PUBLISHED);
            if (updatedRows != 1) {
                throw new BlogException(ErrorCode.POST_NOT_FOUND);
            }
        }

        return postRepository
                .getPostDetail(postId)
                .withReactions(reactionResponse(postId, memberId));
    }

    @Transactional
    public PostReactionResponse setReaction(
            Long postId,
            PostReactionType type,
            Long memberId
    ) {
        if (type == null) {
            throw new BlogException(ErrorCode.POST_REACTION_REQUIRED);
        }

        Post post = findReadablePost(postId);
        Member member = findActiveMember(memberId);

        postReactionRepository
                .findByPostIdAndMemberIdAndType(postId, memberId, type)
                .orElseGet(() -> postReactionRepository.save(new PostReaction(post, member, type)));

        return reactionResponse(postId, memberId);
    }

    @Transactional
    public PostReactionResponse removeReaction(
            Long postId,
            PostReactionType type,
            Long memberId
    ) {
        if (type == null) {
            throw new BlogException(ErrorCode.POST_REACTION_REQUIRED);
        }

        findReadablePost(postId);
        findActiveMember(memberId);

        postReactionRepository.findByPostIdAndMemberIdAndType(postId, memberId, type)
                .ifPresent(postReactionRepository::delete);

        return reactionResponse(postId, memberId);
    }

    private PostReactionResponse reactionResponse(Long postId, Long memberId) {
        var myReactions = memberId == null
                ? java.util.Set.<PostReactionType>of()
                : postReactionRepository
                        .findAllByPostIdAndMemberId(postId, memberId)
                        .stream()
                        .map(PostReaction::getType)
                        .collect(java.util.stream.Collectors.toSet());

        return new PostReactionResponse(
                postReactionRepository.countByPostIdAndType(postId, PostReactionType.LIKE),
                postReactionRepository.countByPostIdAndType(postId, PostReactionType.DISLIKE),
                myReactions.contains(PostReactionType.LIKE),
                myReactions.contains(PostReactionType.DISLIKE)
        );
    }

    private Member findActiveMember(Long memberId) {
        if (memberId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));

        if (!member.getStatus().isActive()) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        return member;
    }

    public AdjacentPostResponse getAdjacentPost(Long postId) {
        findReadablePost(postId);

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

    public CommentListResponse getCommentInPostDetail(
            Long postId,
            Long cursor,
            Long size,
            Long memberId
    ) {

        findReadablePost(postId);

        if (size == null || size <= 0L) {
            size = DEFAULT_COMMENT_PAGE_SIZE;
            log.info("[getCommentInPostDetail] Size is Null or Zero => size = {}, " +
                    "so we change to default Value : {} " +
                    ", you have to check it ", size, DEFAULT_COMMENT_PAGE_SIZE);
        }

        if (size > MAX_COMMENT_PAGE_SIZE) {
            log.warn("[getCommentInPostDetail] Size exceeds max size. size = {}, maxSize = {}", size, MAX_COMMENT_PAGE_SIZE);
            throw new BlogException(ErrorCode.COMMENT_SIZE_LIMIT_EXCEEDED);
        }

        return commentRepository.getCommentPageByPostId(postId, cursor, size, memberId);
    }
}
