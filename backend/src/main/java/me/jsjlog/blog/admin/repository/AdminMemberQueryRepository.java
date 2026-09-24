package me.jsjlog.blog.admin.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.dto.AdminMemberStatusCounts;
import me.jsjlog.blog.admin.dto.AdminMemberSummaryResponse;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.domain.QMember;
import me.jsjlog.blog.post.domain.QComment;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class AdminMemberQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    /** 운영자 계정은 제재 대상 목록에서 제외한다. */
    public List<AdminMemberSummaryResponse> getMembers(AdminMemberSearchCondition condition) {
        QMember member = QMember.member;
        QComment comment = QComment.comment;

        List<Tuple> rows = jpaQueryFactory
                .select(
                        member.id,
                        member.nickname,
                        member.email,
                        member.provider,
                        member.status,
                        comment.count(),
                        member.createdAt
                )
                .from(member)
                .leftJoin(comment).on(comment.member.id.eq(member.id))
                .where(toPredicate(condition, condition.status()))
                .groupBy(
                        member.id,
                        member.nickname,
                        member.email,
                        member.provider,
                        member.status,
                        member.createdAt
                )
                .orderBy(member.createdAt.desc(), member.id.desc())
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault())
                .fetch();

        return rows.stream()
                .map(row -> new AdminMemberSummaryResponse(
                        row.get(member.id),
                        row.get(member.nickname),
                        row.get(member.email),
                        row.get(member.provider),
                        row.get(member.status),
                        Objects.requireNonNullElse(row.get(comment.count()), 0L),
                        row.get(member.createdAt)
                ))
                .toList();
    }

    public long countMembers(AdminMemberSearchCondition condition) {
        return count(condition, condition.status());
    }

    /** 상태 탭의 숫자는 현재 선택한 상태를 제외하고 검색어만 반영한다. */
    public AdminMemberStatusCounts countByStatus(AdminMemberSearchCondition condition) {
        return new AdminMemberStatusCounts(
                count(condition, null),
                count(condition, MemberStatus.ACTIVE),
                count(condition, MemberStatus.SUSPENDED),
                count(condition, MemberStatus.WITHDRAWN)
        );
    }

    private long count(AdminMemberSearchCondition condition, MemberStatus status) {
        QMember member = QMember.member;

        Long count = jpaQueryFactory
                .select(member.count())
                .from(member)
                .where(toPredicate(condition, status))
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    private BooleanBuilder toPredicate(AdminMemberSearchCondition condition, MemberStatus status) {
        QMember member = QMember.member;
        BooleanBuilder builder = new BooleanBuilder(member.role.eq(MemberRole.USER));

        if (status != null) {
            builder.and(member.status.eq(status));
        }

        if (StringUtils.hasText(condition.keyword())) {
            String keyword = condition.keyword().trim();
            builder.and(
                    member.nickname.containsIgnoreCase(keyword)
                            .or(member.email.containsIgnoreCase(keyword))
            );
        }

        return builder;
    }
}
