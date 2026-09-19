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
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
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
        Set<Long> answeredIds = getAnsweredRootIds(rows.stream()
                .map(row -> row.get(comment.id))
                .toList());

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
                        answeredIds.contains(row.get(comment.id))
                ))
                .toList();
    }

    public long countComments(AdminCommentSearchCondition condition) {
        return count(condition, condition.statusOrDefault());
    }

    public AdminCommentCounts countByStatus(AdminCommentSearchCondition condition) {
        return new AdminCommentCounts(
                count(condition, AdminCommentFilter.ALL),
                count(condition, AdminCommentFilter.UNANSWERED),
                count(condition, AdminCommentFilter.HIDDEN)
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
        }

        return builder;
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
