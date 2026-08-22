package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.dto.*;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom{

    private final JPAQueryFactory jpaQueryFactory;

    /**
     * 대문용 게시글 최신순으로 4건
     * @return
     */
    @Override
    public List<PostSummaryResponse> getLatestPostsForHomePage(){
        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(post.status.eq(PostStatus.PUBLISHED));

        builder.and(post.publishedAt.loe(LocalDateTime.now()));

        List<PostSummaryResponse> postSummaryResponseList = jpaQueryFactory.select(Projections.constructor(PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.thumbnailImageUrl,
                        post.publishedAt,
                        post.views
                )).from(post)
                .where(builder)
                .orderBy(post.publishedAt.desc())
                .limit(4)
                .fetch();

        return postSummaryResponseList;
    }

    @Override
    public List<PostSummaryResponse> getPopularPostsForHomePage() {
        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(post.status.eq(PostStatus.PUBLISHED));

        builder.and(post.publishedAt.loe(LocalDateTime.now()));

        List<PostSummaryResponse> postSummaryResponseList = jpaQueryFactory.select(
                        Projections.constructor(PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.thumbnailImageUrl,
                        post.publishedAt,
                        post.views
                )).from(post)
                .where(builder)
                .orderBy(post.views.desc())
                .limit(4)
                .fetch();
        return postSummaryResponseList;
    }

    @Override
    public List<PostSummaryResponse> getPostsForHomePage(String sort, Long size){

        String POPULAR_PARAM = "popular";
        String LATEST_PARAM = "latest";

        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(post.status.eq(PostStatus.PUBLISHED));
        builder.and(post.publishedAt.loe(LocalDateTime.now()));

        OrderSpecifier<?>[] orderSpecifier = POPULAR_PARAM.equals(sort) ?
                                                new OrderSpecifier<?>[]{ post.views.desc(), post.id.desc() } :
                                                new OrderSpecifier<?>[]{ post.publishedAt.desc(), post.id.desc() };

        List<PostSummaryResponse> postSummaryResponseList = jpaQueryFactory.select(
                Projections.constructor(
                        PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.thumbnailImageUrl,
                        post.publishedAt,
                        post.views
                )).from(post)
                .where(builder)
                .orderBy(orderSpecifier)
                .limit(size)
                .fetch();
        return postSummaryResponseList;
    }

    @Override
    public PostDetailResponse getPostDetail(Long postId) {

        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(post.id.eq(postId));


        PostDetailResponse postDetailResponse = jpaQueryFactory.select(Projections.constructor(
                PostDetailResponse.class,
                post.id,
                post.title,
                post.excerpt,
                post.content,
                post.category.id,
                post.category.name,
                post.thumbnailImageUrl,
                post.status,
                post.publishedAt,
                post.views,
                post.likeCount
        )).from(post)
                .where(builder)
                .limit(1L)
                .fetchOne();

        return postDetailResponse;
    }

    @Override
    public AdjacentPostResponse getAdjacentPost(Long postId) {

        QPost post = QPost.post;

        Post currentPost = jpaQueryFactory
                .selectFrom(post)
                .where(post.id.eq(postId))
                .fetchOne();

        LocalDateTime publishedAt = currentPost.getPublishedAt();

        AdjacentPostSummary previousPost = jpaQueryFactory
                .select(
                        Projections.constructor(AdjacentPostSummary.class,
                                post.id,
                                post.title
                        ))
                .where(
                        post.publishedAt.lt(publishedAt)
                                .or(
                                        post.publishedAt.eq(publishedAt)
                                                .and(post.id.lt(postId))
                                ),
                        post.status.eq(PostStatus.PUBLISHED),
                        post.publishedAt.loe(LocalDateTime.now())
                )
                .orderBy(
                        post.publishedAt.desc(),
                        post.id.desc()
                )
                .fetchFirst();

        AdjacentPostSummary nextPost = jpaQueryFactory
                .select(
                        Projections.constructor(AdjacentPostSummary.class,
                                post.id,
                                post.title
                        ))
                .where(
                        post.publishedAt.gt(publishedAt)
                                .or(
                                        post.publishedAt.eq(publishedAt)
                                                .and(post.id.gt(postId))
                                ),
                        post.status.eq(PostStatus.PUBLISHED),
                        post.publishedAt.loe(LocalDateTime.now())
                )
                .orderBy(
                        post.publishedAt.asc(),
                        post.id.asc()
                )
                .fetchFirst();

        return new AdjacentPostResponse(previousPost,nextPost);
    }

    @Override
    public List<PostSummaryResponse> getRelatedPosts(Long postId, Long categoryId) {

        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(post.status.eq(PostStatus.PUBLISHED));
        builder.and(post.publishedAt.loe(LocalDateTime.now()));
        builder.and(post.category.id.eq(categoryId));
        builder.and(post.id.ne(postId)); // 자기자신은 포함되지 않도록

        List<PostSummaryResponse> postSummaryResponseList = jpaQueryFactory.select(Projections.constructor(PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.thumbnailImageUrl,
                        post.publishedAt,
                        post.views
                )).from(post)
                .where(builder)
                .orderBy(post.publishedAt.desc(),post.id.desc())
                .limit(3)
                .fetch();

        return postSummaryResponseList;
    }

    @Override
    public List<CommentResponse> getCommentInPost(Long postId) {
        QPost post = QPost.post;
        QComment comment = QComment.comment;
        QComment parent = new QComment("parent");

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(post.status.eq(PostStatus.PUBLISHED));
        builder.and(post.publishedAt.loe(LocalDateTime.now()));

        return List.of();
    }
}
