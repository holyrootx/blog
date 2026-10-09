package me.jsjlog.blog.admin.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupSearchCondition;
import me.jsjlog.blog.common.code.domain.CommonCodeGroup;
import me.jsjlog.blog.common.code.domain.QCommonCode;
import me.jsjlog.blog.common.code.domain.QCommonCodeGroup;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class AdminCommonCodeQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<CommonCodeGroup> getGroups(AdminCommonCodeGroupSearchCondition condition) {
        QCommonCodeGroup group = QCommonCodeGroup.commonCodeGroup;

        return jpaQueryFactory
                .selectFrom(group)
                .where(toPredicate(condition))
                .orderBy(group.groupCode.asc())
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault())
                .fetch();
    }

    public long countGroups(AdminCommonCodeGroupSearchCondition condition) {
        QCommonCodeGroup group = QCommonCodeGroup.commonCodeGroup;

        Long count = jpaQueryFactory
                .select(group.count())
                .from(group)
                .where(toPredicate(condition))
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    /** 그룹별 코드 수. 사용하지 않는 코드도 센다 */
    public Map<String, Long> countCodes(Collection<String> groupCodes) {
        if (groupCodes.isEmpty()) {
            return Map.of();
        }
        QCommonCode code = QCommonCode.commonCode;

        List<Tuple> rows = jpaQueryFactory
                .select(code.groupCode, code.count())
                .from(code)
                .where(code.groupCode.in(groupCodes))
                .groupBy(code.groupCode)
                .fetch();

        return rows.stream().collect(Collectors.toMap(
                row -> row.get(code.groupCode),
                row -> Objects.requireNonNullElse(row.get(code.count()), 0L)));
    }

    /** 검색어는 그룹 코드·이름과, 그 그룹 안 코드의 값·이름에서 찾는다 */
    private BooleanBuilder toPredicate(AdminCommonCodeGroupSearchCondition condition) {
        QCommonCodeGroup group = QCommonCodeGroup.commonCodeGroup;
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.enabled() != null) {
            builder.and(group.enabled.eq(condition.enabled()));
        }

        if (StringUtils.hasText(condition.keyword())) {
            String keyword = condition.keyword().trim();
            QCommonCode code = new QCommonCode("matchedCode");
            builder.and(group.groupCode.containsIgnoreCase(keyword)
                    .or(group.groupName.containsIgnoreCase(keyword))
                    .or(JPAExpressions.selectOne()
                            .from(code)
                            .where(code.groupCode.eq(group.groupCode),
                                    code.code.containsIgnoreCase(keyword)
                                            .or(code.codeName.containsIgnoreCase(keyword)))
                            .exists()));
        }

        return builder;
    }
}
