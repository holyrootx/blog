package me.jsjlog.blog.admin.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCategoryShareResponse;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.domain.QCategory;
import me.jsjlog.blog.post.domain.QCommentReport;
import me.jsjlog.blog.post.domain.QPost;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 대시보드 집계.
 *
 * 글·댓글·카테고리를 함께 훑기 때문에 특정 엔티티 리포지토리에 붙이지 않고 따로 둔다.
 * 여기 있는 쿼리는 전부 읽기 전용 집계다.
 */
@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public long countPostsByStatus(PostStatus status) {
        QPost post = QPost.post;

        return orZero(jpaQueryFactory
                .select(post.count())
                .from(post)
                .where(post.status.eq(status), post.deletedAt.isNull())
                .fetchOne());
    }

    /** 누적 조회수는 과거 방문 기록이므로 휴지통 이동 여부와 관계없이 유지한다. */
    public long sumViews() {
        QPost post = QPost.post;

        return orZero(jpaQueryFactory
                .select(post.views.sum())
                .from(post)
                .fetchOne());
    }

    /** 이번 달에 쓴 글. 발행일이 아니라 작성일 기준이다 */
    public long countPostsCreatedSince(LocalDateTime from) {
        QPost post = QPost.post;

        return orZero(jpaQueryFactory
                .select(post.count())
                .from(post)
                .where(post.createdAt.goe(from), post.deletedAt.isNull())
                .fetchOne());
    }

    /** 카테고리 비중. 화면이 "발행 글 기준"이라고 적고 있다 */
    public List<AdminCategoryShareResponse> getCategoryShares() {
        QCategory category = QCategory.category;
        QPost post = QPost.post;

        return jpaQueryFactory
                .select(Projections.constructor(
                        AdminCategoryShareResponse.class,
                        category.id,
                        category.name,
                        post.id.count()
                ))
                .from(category)
                // 글이 0개인 카테고리도 나와야 하므로 LEFT JOIN,
                // 발행 조건은 where 가 아니라 on 에 둔다
                .leftJoin(post).on(post.category.id.eq(category.id), post.status.eq(PostStatus.PUBLISHED), post.deletedAt.isNull())
                .groupBy(category.id, category.name)
                .orderBy(post.id.count().desc(), category.id.asc())
                .fetch();
    }

    /** 조회수 합이 가장 큰 카테고리. 동점이면 먼저 만든 카테고리 */
    public String getMostViewedCategoryName() {
        QCategory category = QCategory.category;
        QPost post = QPost.post;

        return jpaQueryFactory
                .select(category.name)
                .from(category)
                .join(post).on(post.category.id.eq(category.id), post.deletedAt.isNull())
                .groupBy(category.id, category.name)
                .orderBy(post.views.sum().desc(), category.id.asc())
                .limit(1)
                .fetchOne();
    }

    /** 미처리 신고가 하나 이상 있는 댓글 수. 댓글 관리의 신고 필터와 같은 기준이다. */
    public long countReportedComments() {
        QCommentReport report = QCommentReport.commentReport;

        return orZero(jpaQueryFactory
                .select(report.comment.id.countDistinct())
                .from(report)
                .where(report.handledAt.isNull(), report.comment.post.deletedAt.isNull()
                        .or(report.comment.post.contentPurgedAt.isNotNull()))
                .fetchOne());
    }

    private long orZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
