package me.jsjlog.blog.admin.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommentCounts;
import me.jsjlog.blog.admin.dto.AdminCommentFilter;
import me.jsjlog.blog.admin.dto.AdminCommentSearchCondition;
import me.jsjlog.blog.admin.dto.AdminCommentSummaryResponse;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.domain.QComment;
import me.jsjlog.blog.post.domain.QCommentReport;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class AdminCommentQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<AdminCommentSummaryResponse> getComments(AdminCommentSearchCondition condition) {
        QComment comment = QComment.comment;

        JPAQuery<Tuple> query = jpaQueryFactory
                .select(
                        comment.id,
                        comment.post.id,
                        comment.post.title,
                        comment.parent.id,
                        comment.member.nickname,
                        comment.member.role,
                        comment.content,
                        comment.createdAt,
                        comment.deleted
                )
                .from(comment)
                .join(comment.post)
                .join(comment.member)
                .where(toPredicate(condition, condition.statusOrDefault()))
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault());

        if (condition.statusOrDefault() == AdminCommentFilter.UNANSWERED) {
            query.orderBy(comment.createdAt.asc(), comment.id.asc());
        } else {
            query.orderBy(comment.createdAt.desc(), comment.id.desc());
        }

        List<Tuple> rows = query.fetch();
        List<Long> ids = rows.stream()
                .map(row -> row.get(comment.id))
                .toList();

        Set<Long> answeredIds = getAnsweredRootIds(ids);
        Map<Long, Long> reportCounts = getReportCounts(ids);
        Map<Long, Long> unhandledReportCounts = getUnhandledReportCounts(ids);

        return rows.stream()
                .map(row -> new AdminCommentSummaryResponse(
                        row.get(comment.id),
                        row.get(comment.post.id),
                        row.get(comment.post.title),
                        row.get(comment.parent.id),
                        row.get(comment.member.nickname),
                        row.get(comment.member.role),
                        row.get(comment.content),
                        row.get(comment.createdAt),
                        Boolean.TRUE.equals(row.get(comment.deleted)),
                        answeredIds.contains(row.get(comment.id)),
                        reportCounts.getOrDefault(row.get(comment.id), 0L),
                        unhandledReportCounts.getOrDefault(row.get(comment.id), 0L)
                ))
                .toList();
    }

    /**
     * 화면에 뜬 댓글들의 신고 수를 한 번에 센다.
     *
     * <p>댓글마다 따로 세면 목록 한 번 그리는 데 쿼리가 스무 번 나간다. 위의
     * {@code getAnsweredRootIds} 와 같은 이유, 같은 방식이다.</p>
     *
     * <p>신고가 없는 댓글은 결과에 없다. 받는 쪽에서 0으로 채운다.</p>
     */
    private Map<Long, Long> getReportCounts(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        QCommentReport report = QCommentReport.commentReport;

        List<Tuple> rows = jpaQueryFactory
                .select(report.comment.id, report.count())
                .from(report)
                .where(report.comment.id.in(commentIds))
                .groupBy(report.comment.id)
                .fetch();

        Map<Long, Long> counts = new HashMap<>();

        for (Tuple row : rows) {
            counts.put(row.get(report.comment.id), row.get(report.count()));
        }

        return counts;
    }

    /** 미처리 신고 수. 위와 한 번에 세지 않는 이유는 조건부 집계가 방언마다 달라서다 */
    private Map<Long, Long> getUnhandledReportCounts(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        QCommentReport report = QCommentReport.commentReport;

        List<Tuple> rows = jpaQueryFactory
                .select(report.comment.id, report.count())
                .from(report)
                .where(report.comment.id.in(commentIds), report.handledAt.isNull())
                .groupBy(report.comment.id)
                .fetch();

        Map<Long, Long> counts = new HashMap<>();

        for (Tuple row : rows) {
            counts.put(row.get(report.comment.id), row.get(report.count()));
        }

        return counts;
    }

    public long countComments(AdminCommentSearchCondition condition) {
        return count(condition, condition.statusOrDefault());
    }

    public AdminCommentCounts countByStatus(AdminCommentSearchCondition condition) {
        return new AdminCommentCounts(
                count(condition, AdminCommentFilter.ALL),
                count(condition, AdminCommentFilter.UNANSWERED),
                count(condition, AdminCommentFilter.HIDDEN),
                count(condition, AdminCommentFilter.REPORTED)
        );
    }

    private long count(AdminCommentSearchCondition condition, AdminCommentFilter filter) {
        QComment comment = QComment.comment;

        Long count = jpaQueryFactory
                .select(comment.count())
                .from(comment)
                .where(toPredicate(condition, filter))
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    private BooleanBuilder toPredicate(
            AdminCommentSearchCondition condition,
            AdminCommentFilter filter
    ) {
        QComment comment = QComment.comment;
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.hasText(condition.keyword())) {
            String keyword = condition.keyword().trim();
            builder.and(
                    comment.content.containsIgnoreCase(keyword)
                            .or(comment.member.nickname.containsIgnoreCase(keyword))
                            .or(comment.post.title.containsIgnoreCase(keyword))
            );
        }

        if (filter == AdminCommentFilter.UNANSWERED) {
            builder.and(unanswered(comment));
        } else if (filter == AdminCommentFilter.HIDDEN) {
            builder.and(comment.deleted.isTrue());
        } else if (filter == AdminCommentFilter.REPORTED) {
            builder.and(hasUnhandledReport(comment));
        }

        return builder;
    }

    /**
     * 아직 판단하지 않은 신고가 달렸는가.
     *
     * <p>처리한 신고까지 세면 한 번 본 댓글이 목록에서 내려가지 않는다. 그러면 새로 온
     * 신고가 옛것 사이에 묻혀서, 목록을 봐도 무엇이 새것인지 알 수 없다.</p>
     */
    private com.querydsl.core.types.dsl.BooleanExpression hasUnhandledReport(QComment comment) {
        QCommentReport report = QCommentReport.commentReport;

        return JPAExpressions
                .selectOne()
                .from(report)
                .where(
                        report.comment.id.eq(comment.id),
                        report.handledAt.isNull()
                )
                .exists();
    }

    private com.querydsl.core.types.dsl.BooleanExpression unanswered(QComment comment) {
        QComment reply = new QComment("adminReply");

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

    private Set<Long> getAnsweredRootIds(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Set.of();
        }

        QComment reply = new QComment("answerReply");
        List<Long> answeredIds = jpaQueryFactory
                .select(reply.parent.id)
                .from(reply)
                .where(
                        reply.parent.id.in(commentIds),
                        reply.deleted.isFalse(),
                        reply.member.role.eq(MemberRole.ADMIN)
                )
                .distinct()
                .fetch();

        return new HashSet<>(answeredIds);
    }
}
