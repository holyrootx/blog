package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.dto.*;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom{

    /** LIKE 패턴에서 뜻을 가진 글자들. 사용자가 친 그대로 찾게 하려면 막아야 한다 */
    private static final char LIKE_ESCAPE = '!';

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
                .orderBy(post.views.desc(), post.id.desc())
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
                .from(post)
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
                .from(post)
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

    /**
     * 공개 글 목록.
     *
     * <p>상태 조건을 요청에서 받지 않는다. 발행됐고 발행 시각이 지난 글만 보여주는 것은
     * 공개 화면의 규칙이라, 조회조건으로 열어 두면 주소를 고쳐 비공개 글을 꺼내 볼 수 있다.</p>
     */
    @Override
    public List<PostSummaryResponse> getPublicPosts(PostListCondition condition) {
        QPost post = QPost.post;

        return jpaQueryFactory
                .select(Projections.constructor(
                        PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.thumbnailImageUrl,
                        post.publishedAt,
                        post.views
                ))
                .from(post)
                .join(post.category)
                .where(publicPostPredicate(post, condition))
                .orderBy(listOrder(post, condition))
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault())
                .fetch();
    }

    @Override
    public long countPublicPosts(PostListCondition condition) {
        QPost post = QPost.post;

        Long total = jpaQueryFactory
                .select(post.count())
                .from(post)
                .where(publicPostPredicate(post, condition))
                .fetchOne();

        return total == null ? 0L : total;
    }

    /**
     * 검색창 아래에 바로 뜨는 글 몇 건.
     *
     * <p>목록과 달리 개수를 세지 않고 페이지도 없다. 타자를 칠 때마다 불리는 자리라
     * 한 번에 하나라도 덜 하는 편이 낫다.</p>
     */
    @Override
    public List<PostSuggestResponse> getPublicPostSuggestions(String keyword, int size) {
        QPost post = QPost.post;

        String pattern = likePattern(keyword);

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(post.status.eq(PostStatus.PUBLISHED));
        builder.and(post.publishedAt.loe(LocalDateTime.now()));
        builder.and(keywordPredicate(post, pattern));

        return jpaQueryFactory
                .select(Projections.constructor(
                        PostSuggestResponse.class,
                        post.id,
                        post.title,
                        post.category.name
                ))
                .from(post)
                .join(post.category)
                .where(builder)
                .orderBy(titleFirst(post, pattern).asc(), post.publishedAt.desc(), post.id.desc())
                .limit(size)
                .fetch();
    }

    /** 목록과 개수가 같은 조건을 봐야 페이지 수가 맞는다. 그래서 한 곳에서 만든다 */
    private BooleanBuilder publicPostPredicate(QPost post, PostListCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(post.status.eq(PostStatus.PUBLISHED));
        builder.and(post.publishedAt.loe(LocalDateTime.now()));

        if (condition.categoryId() != null) {
            builder.and(post.category.id.eq(condition.categoryId()));
        }

        if (condition.hasKeyword()) {
            builder.and(keywordPredicate(post, likePattern(condition.keywordOrNull())));
        }

        return builder;
    }

    /**
     * 정렬.
     *
     * <p>검색 중이면 제목에 걸린 글을 먼저 올린다. 제목에 있는 말이 그 글의 주제일 가능성이
     * 높아서, 본문 어딘가에 한 번 스친 글보다 찾는 사람에게 가깝다. 그 다음에야 고른
     * 순서(최신순·인기순)를 따른다.</p>
     */
    private OrderSpecifier<?>[] listOrder(QPost post, PostListCondition condition) {
        OrderSpecifier<?>[] chosen = condition.isPopular()
                ? new OrderSpecifier<?>[]{ post.views.desc(), post.id.desc() }
                : new OrderSpecifier<?>[]{ post.publishedAt.desc(), post.id.desc() };

        if (!condition.hasKeyword()) {
            return chosen;
        }

        OrderSpecifier<?>[] order = new OrderSpecifier<?>[chosen.length + 1];
        order[0] = titleFirst(post, likePattern(condition.keywordOrNull())).asc();
        System.arraycopy(chosen, 0, order, 1, chosen.length);

        return order;
    }

    /** 제목에 걸리면 0, 아니면 1. 오름차순으로 정렬하면 제목 일치가 위로 온다 */
    private NumberExpression<Integer> titleFirst(QPost post, String pattern) {
        return new CaseBuilder()
                .when(likeMatches(post.title, pattern))
                .then(0)
                .otherwise(1);
    }

    /**
     * 검색 범위는 제목과 발췌다.
     *
     * <p>본문을 넣지 않는 이유는 {@code content} 가 마크다운 원문이기 때문이다. 제목 기호(##),
     * 코드 블록, 이미지 주소까지 그대로 들어 있어서 http 로 검색하면 사진 있는 글이 전부 걸린다.
     * 발췌는 사람이 쓴 요약이라 깨끗하고, 제목이 놓치는 것을 상당히 메운다.</p>
     */
    private BooleanExpression keywordPredicate(QPost post, String pattern) {
        return likeMatches(post.title, pattern).or(likeMatches(post.excerpt, pattern));
    }

    /**
     * 부분 일치. 인덱스를 타지 못하지만 글이 수천 편이 될 때까지는 체감되지 않는다.
     *
     * <p>여기를 고치면 FULLTEXT 로 갈아탈 수 있다. 바꿀 곳이 이 메서드 하나가 되도록
     * 검색 조건을 전부 이 아래로 모아 두었다.</p>
     *
     * <p><b>대소문자를 가르는 일은 여기서 하지 않는다.</b> 그것은 컬럼의 대조 규칙이 정한다 —
     * {@code post.title} 과 {@code post.excerpt} 는 {@code utf8mb4_bin} 이어야 한다.
     * 쿼리에 {@code collate} 를 붙이는 길도 시도했으나 Hibernate 의 HQL 파서가 그 낱말을
     * 모른다(SyntaxException). 애초에 "이 칸은 대소문자를 가린다" 는 것은 질의마다 고를 일이
     * 아니라 칸이 지니는 성질이라, 스키마에 두는 편이 제자리이기도 하다.</p>
     *
     * <p>H2 는 LIKE 가 본래 대소문자를 가르므로 테스트와 운영이 같은 결과를 낸다.</p>
     */
    private BooleanExpression likeMatches(StringPath path, String pattern) {
        return path.like(pattern, LIKE_ESCAPE);
    }

    /**
     * 사용자가 친 말을 LIKE 패턴으로 바꾼다.
     *
     * <p>% 와 _ 는 LIKE 에서 뜻이 있어서 그대로 두면 "100%" 검색이 "100" 으로 시작하는 글을
     * 전부 끌어온다. 찾는 사람은 글자 그대로를 기대하므로 막아 준다.</p>
     */
    private String likePattern(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);

        for (char letter : keyword.toCharArray()) {
            if (letter == '%' || letter == '_' || letter == LIKE_ESCAPE) {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(letter);
        }

        return "%" + escaped + "%";
    }
}
