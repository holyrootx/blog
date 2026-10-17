package me.jsjlog.blog.admin.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminPostViewCounts;
import me.jsjlog.blog.admin.dto.AdminPostViewResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewSearchCondition;
import me.jsjlog.blog.admin.dto.AdminPostViewerType;
import me.jsjlog.blog.common.time.ServerTime;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.QMember;
import me.jsjlog.blog.member.domain.QMemberStatus;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.domain.QPostView;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class AdminPostViewQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<AdminPostViewResponse> getViews(Long postId, AdminPostViewSearchCondition condition) {
        QPostView view = QPostView.postView;
        QMember member = QMember.member;
        QMemberStatus status = QMemberStatus.memberStatus;

        List<Tuple> rows = jpaQueryFactory
                .select(
                        view.id,
                        view.viewedAt,
                        view.memberId,
                        member.nickname,
                        member.role,
                        status.memberStatusCode,
                        view.deviceType
                )
                .from(view)
                .leftJoin(member).on(member.id.eq(view.memberId))
                .leftJoin(status).on(status.memberId.eq(view.memberId))
                .where(view.postId.eq(postId), viewerIs(condition.viewer()))
                .orderBy(view.viewedAt.desc(), view.id.desc())
                .offset((long) condition.pageOrDefault() * condition.sizeOrDefault())
                .limit(condition.sizeOrDefault())
                .fetch();

        return rows.stream()
                .map(row -> new AdminPostViewResponse(
                        row.get(view.id),
                        ServerTime.toOffset(row.get(view.viewedAt)),
                        AdminPostViewerType.of(row.get(view.memberId), row.get(member.role), row.get(view.deviceType)),
                        row.get(view.memberId),
                        row.get(member.nickname),
                        row.get(status.memberStatusCode),
                        null
                ))
                .toList();
    }

    public long countViews(Long postId, AdminPostViewerType viewer) {
        QPostView view = QPostView.postView;
        QMember member = QMember.member;

        Long count = jpaQueryFactory
                .select(view.count())
                .from(view)
                .leftJoin(member).on(member.id.eq(view.memberId))
                .where(view.postId.eq(postId), viewerIs(viewer))
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    /**
     * 구분별 건수. 회원 역할로 묶으면 비회원과 "회원 행을 못 찾은 회원" 이 같은 칸에 섞이므로
     * 회원 번호가 없는 기록(비회원·검색 봇)을 따로 세서 뺀다.
     */
    public AdminPostViewCounts countByViewer(Long postId) {
        QPostView view = QPostView.postView;
        QMember member = QMember.member;

        List<Tuple> byRole = jpaQueryFactory
                .select(member.role, view.count())
                .from(view)
                .leftJoin(member).on(member.id.eq(view.memberId))
                .where(view.postId.eq(postId))
                .groupBy(member.role)
                .fetch();

        long all = 0;
        long admin = 0;
        long user = 0;
        long withoutRole = 0;
        for (Tuple row : byRole) {
            long count = Objects.requireNonNullElse(row.get(view.count()), 0L);
            MemberRole role = row.get(member.role);
            all += count;
            if (role == MemberRole.ADMIN) {
                admin += count;
            } else if (role == null) {
                withoutRole += count;
            } else {
                user += count;
            }
        }

        long anonymous = countViews(postId, AdminPostViewerType.ANONYMOUS);
        long bot = countViews(postId, AdminPostViewerType.BOT);

        return new AdminPostViewCounts(all, user + withoutRole - anonymous - bot, admin, anonymous, bot);
    }

    /** 블로그 전체에서 가장 오래된 기록. 번호가 시간 순으로 늘어나므로 기본 키 첫 줄만 읽는다 */
    public OffsetDateTime findRecordingStartedAt() {
        QPostView view = QPostView.postView;

        return ServerTime.toOffset(jpaQueryFactory
                .select(view.viewedAt)
                .from(view)
                .orderBy(view.id.asc())
                .fetchFirst());
    }

    /** 회원 행을 못 찾은 회원 번호는 회원으로 친다. {@link AdminPostViewerType#of} 와 같은 기준이다 */
    private BooleanExpression viewerIs(AdminPostViewerType viewer) {
        if (viewer == null) {
            return null;
        }

        QPostView view = QPostView.postView;
        QMember member = QMember.member;

        return switch (viewer) {
            case ANONYMOUS -> view.memberId.isNull().and(view.deviceType.ne(DeviceType.BOT));
            case BOT -> view.memberId.isNull().and(view.deviceType.eq(DeviceType.BOT));
            case ADMIN -> member.role.eq(MemberRole.ADMIN);
            case MEMBER -> view.memberId.isNotNull().and(member.role.isNull().or(member.role.ne(MemberRole.ADMIN)));
        };
    }
}
