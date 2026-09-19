package me.jsjlog.blog.admin.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCategoryShareResponse;
import me.jsjlog.blog.admin.dto.AdminUnansweredCommentResponse;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.domain.QCategory;
import me.jsjlog.blog.post.domain.QComment;
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
                .where(post.status.eq(status))
                .fetchOne());
    }

    /** 글별 조회수 합계. 발행 여부를 가리지 않는다 */
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
                .where(post.createdAt.goe(from))
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
                .leftJoin(post).on(post.category.id.eq(category.id), post.status.eq(PostStatus.PUBLISHED))
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
                .join(post).on(post.category.id.eq(category.id))
                .groupBy(category.id, category.name)
                .orderBy(post.views.sum().desc(), category.id.asc())
                .limit(1)
                .fetchOne();
    }

    /**
     * 답변을 기다리는 댓글 수.
     *
     * "미답변"은 작성자 답글이 달리지 않은, 삭제되지 않은 최상위 댓글이다.
     * 답글에는 답글을 달 수 없으므로 답글 자체는 대상이 아니고,
     * 방문자끼리 주고받은 답글은 답변으로 치지 않는다 — 블로그 주인의 답글만 센다.
     */
    public long countUnansweredComments() {
        QComment comment = QComment.comment;

        return orZero(jpaQueryFactory
                .select(comment.count())
                .from(comment)
                .where(unanswered(comment))
                .fetchOne());
    }

    public LocalDateTime getOldestUnansweredAt() {
        QComment comment = QComment.comment;

        return jpaQueryFactory
                .select(comment.createdAt)
                .from(comment)
                .where(unanswered(comment))
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .limit(1)
                .fetchOne();
    }

    /** 오래 기다린 순서로 몇 건 */
    public List<AdminUnansweredCommentResponse> getUnansweredComments(long size) {
        QComment comment = QComment.comment;

        return jpaQueryFactory
                .select(Projections.constructor(
                        AdminUnansweredCommentResponse.class,
                        comment.id,
                        comment.post.id,
                        comment.post.title,
                        comment.member.nickname,
                        comment.content,
                        comment.createdAt
                ))
                .from(comment)
                .join(comment.post)
                .where(unanswered(comment))
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .limit(size)
                .fetch();
    }

    private com.querydsl.core.types.dsl.BooleanExpression unanswered(QComment comment) {
        QComment reply = new QComment("reply");

        return comment.parent.isNull()
                .and(comment.deleted.isFalse())
                .and(comment.member.role.ne(MemberRole.ADMIN))
                .and(JPAExpressions
                        .selectOne()
                        .from(reply)
                        .where(
                                reply.parent.id.eq(comment.id),
                                reply.deleted.isFalse(),
                                reply.member.role.eq(MemberRole.ADMIN)
                        )
                        .notExists());
    }

    private long orZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
