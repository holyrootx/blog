package me.jsjlog.blog.admin.repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminGuestbookCounts;
import me.jsjlog.blog.admin.dto.AdminGuestbookFilter;
import me.jsjlog.blog.admin.dto.AdminGuestbookSearchCondition;
import me.jsjlog.blog.admin.dto.AdminGuestbookSummaryResponse;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.domain.QGuestbookEntry;
import me.jsjlog.blog.guestbook.domain.QGuestbookReport;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.QMember;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class AdminGuestbookQueryRepository {

	private final JPAQueryFactory jpaQueryFactory;

	public List<AdminGuestbookSummaryResponse> getEntries(AdminGuestbookSearchCondition condition) {
		QGuestbookEntry entry = QGuestbookEntry.guestbookEntry;
		QMember writer = new QMember("writer");

		List<Tuple> rows = jpaQueryFactory
				.select(entry, writer.id, writer.nickname, writer.role)
				.from(entry)
				.join(entry.member, writer)
				.where(toPredicate(condition, condition.statusOrDefault(), writer))
				.orderBy(entry.id.desc())
				.offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
				.limit(condition.sizeOrDefault())
				.fetch();

		List<Long> ids = rows.stream().map(row -> row.get(entry).getId()).toList();
		Map<Long, Long> reportCounts = countReports(ids, false);
		Map<Long, Long> unhandledCounts = countReports(ids, true);

		return rows.stream()
				.map(row -> toSummary(
						row.get(entry),
						row.get(writer.id),
						row.get(writer.nickname),
						row.get(writer.role),
						reportCounts.getOrDefault(row.get(entry).getId(), 0L),
						unhandledCounts.getOrDefault(row.get(entry).getId(), 0L)
				))
				.toList();
	}

	public long countEntries(AdminGuestbookSearchCondition condition) {
		return count(condition, condition.statusOrDefault());
	}

	public AdminGuestbookCounts countByStatus(AdminGuestbookSearchCondition condition) {
		return new AdminGuestbookCounts(
				count(condition, AdminGuestbookFilter.ALL),
				count(condition, AdminGuestbookFilter.REPORTED),
				count(condition, AdminGuestbookFilter.HIDDEN),
				count(condition, AdminGuestbookFilter.DELETED)
		);
	}

	/** 상세 화면도 목록과 같은 모양으로 그린다. 한 줄만 다시 센다 */
	public AdminGuestbookSummaryResponse toSummary(GuestbookEntry entry) {
		Map<Long, Long> reportCounts = countReports(List.of(entry.getId()), false);
		Map<Long, Long> unhandledCounts = countReports(List.of(entry.getId()), true);

		return toSummary(
				entry,
				entry.getMember().getId(),
				entry.getMember().getNickname(),
				entry.getMember().getRole(),
				reportCounts.getOrDefault(entry.getId(), 0L),
				unhandledCounts.getOrDefault(entry.getId(), 0L)
		);
	}

	private AdminGuestbookSummaryResponse toSummary(
			GuestbookEntry entry,
			Long memberId,
			String nickname,
			MemberRole role,
			long reportCount,
			long unhandledReportCount
	) {
		return new AdminGuestbookSummaryResponse(
				entry.getId(),
				memberId,
				nickname,
				role == MemberRole.ADMIN,
				entry.getContent(),
				entry.isSecret(),
				entry.isEdited(),
				entry.getCreatedAt(),
				entry.isDeleted(),
				entry.isHiddenByAdmin(),
				entry.isContentPurged(),
				entry.getReplyContent(),
				entry.getRepliedAt(),
				reportCount,
				unhandledReportCount,
				entry.isDeletedByWriter()
		);
	}

	private long count(AdminGuestbookSearchCondition condition, AdminGuestbookFilter filter) {
		QGuestbookEntry entry = QGuestbookEntry.guestbookEntry;
		QMember writer = new QMember("writer");

		Long count = jpaQueryFactory
				.select(entry.count())
				.from(entry)
				.join(entry.member, writer)
				.where(toPredicate(condition, filter, writer))
				.fetchOne();

		return Objects.requireNonNullElse(count, 0L);
	}

	private BooleanBuilder toPredicate(
			AdminGuestbookSearchCondition condition,
			AdminGuestbookFilter filter,
			QMember writer
	) {
		QGuestbookEntry entry = QGuestbookEntry.guestbookEntry;
		BooleanBuilder builder = new BooleanBuilder();

		if (StringUtils.hasText(condition.keyword())) {
			String keyword = condition.keyword().trim();
			builder.and(entry.content.containsIgnoreCase(keyword)
					.or(writer.nickname.containsIgnoreCase(keyword)));
		}

		if (filter == AdminGuestbookFilter.REPORTED) {
			builder.and(hasUnhandledReport(entry));
		} else if (filter == AdminGuestbookFilter.HIDDEN) {
			builder.and(entry.deleted.isTrue().and(entry.hiddenByAdmin.isTrue()).and(entry.deletedAt.isNull()));
		} else if (filter == AdminGuestbookFilter.DELETED) {
			// GuestbookEntry.isDeletedByWriter와 같이 삭제 뒤 가린 글과 삭제 시각이 없는 기존 글도 포함한다.
			builder.and(entry.deletedAt.isNotNull()
					.or(entry.deleted.isTrue().and(entry.hiddenByAdmin.isFalse())));
		}

		return builder;
	}

	/** 처리한 신고까지 세면 한 번 본 글이 목록에서 내려가지 않아 새 신고가 묻힌다 */
	private BooleanExpression hasUnhandledReport(QGuestbookEntry entry) {
		QGuestbookReport report = QGuestbookReport.guestbookReport;

		return JPAExpressions
				.selectOne()
				.from(report)
				.where(report.entry.id.eq(entry.id), report.handledAt.isNull())
				.exists();
	}

	private Map<Long, Long> countReports(List<Long> entryIds, boolean unhandledOnly) {
		if (entryIds.isEmpty()) {
			return Map.of();
		}

		QGuestbookReport report = QGuestbookReport.guestbookReport;
		BooleanBuilder where = new BooleanBuilder(report.entry.id.in(entryIds));

		if (unhandledOnly) {
			where.and(report.handledAt.isNull());
		}

		return jpaQueryFactory
				.select(report.entry.id, report.count())
				.from(report)
				.where(where)
				.groupBy(report.entry.id)
				.fetch()
				.stream()
				.collect(Collectors.toMap(
						row -> row.get(report.entry.id),
						row -> Objects.requireNonNullElse(row.get(report.count()), 0L)
				));
	}
}
