package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.util.StringUtils;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.dto.AdminPostStatusCounts;
import me.jsjlog.blog.admin.dto.AdminPostSummaryResponse;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.domain.QPost;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class AdminPostRepositoryCustomImpl implements AdminPostRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<AdminPostSummaryResponse> getAdminPosts(AdminPostSearchCondition condition) {

        QPost post = QPost.post;

        return jpaQueryFactory
                .select(Projections.constructor(
                        AdminPostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.category.id,
                        post.category.name,
                        post.status,
                        post.publishedAt,
                        post.createdAt,
                        post.views
                ))
                .from(post)
                .join(post.category)
                .where(toPredicate(condition, true))
                // 발행일이 있으면 그것으로, 없으면(임시저장) 작성일로 줄을 세운다.
                // coalesce 를 쓰면 임시저장 글이 목록 맨 뒤로 밀려서 찾기 어렵다
                .orderBy(post.createdAt.desc(), post.id.desc())
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault())
                .fetch();
    }

    @Override
    public long countAdminPosts(AdminPostSearchCondition condition) {

        QPost post = QPost.post;

        Long total = jpaQueryFactory
                .select(post.count())
                .from(post)
                .where(toPredicate(condition, true))
                .fetchOne();

        return Objects.requireNonNullElse(total, 0L);
    }

    @Override
    public AdminPostStatusCounts countByStatus(AdminPostSearchCondition condition) {

        QPost post = QPost.post;

        // status 는 빼고 센다. 발행 탭을 보고 있어도 임시저장 건수가 보여야 한다
        BooleanBuilder builder = toPredicate(condition, false);

        long published = countWithStatus(builder, PostStatus.PUBLISHED);
        long scheduled = countWithStatus(builder, PostStatus.SCHEDULED);
        long privateCount = countWithStatus(builder, PostStatus.PRIVATE);
        long draft = countWithStatus(builder, PostStatus.DRAFT);

        return new AdminPostStatusCounts(
                published + scheduled + privateCount + draft,
                published,
                scheduled,
                privateCount,
                draft
        );
    }

    private long countWithStatus(BooleanBuilder baseBuilder, PostStatus status) {

        QPost post = QPost.post;

        // 원본을 건드리지 않도록 복사해서 상태 조건만 덧붙인다
        BooleanBuilder builder = new BooleanBuilder(baseBuilder.getValue());
        builder.and(post.status.eq(status));

        Long count = jpaQueryFactory
                .select(post.count())
                .from(post)
                .where(builder)
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    /**
     * @param withStatus 상태 조건을 포함할지. 상태별 건수를 셀 때는 빼야 한다
     */
    private BooleanBuilder toPredicate(AdminPostSearchCondition condition, boolean withStatus) {

        QPost post = QPost.post;

        BooleanBuilder builder = new BooleanBuilder();

        if (withStatus && condition.status() != null) {
            builder.and(post.status.eq(condition.status()));
        }

        if (condition.categoryId() != null) {
            builder.and(post.category.id.eq(condition.categoryId()));
        }

        // 제목만 검색한다. 본문까지 넣으면 마크다운 문법에 엉뚱한 글이 걸린다
        if (!StringUtils.isNullOrEmpty(condition.keyword())) {
            builder.and(post.title.contains(condition.keyword()));
        }

        return builder;
    }
}
