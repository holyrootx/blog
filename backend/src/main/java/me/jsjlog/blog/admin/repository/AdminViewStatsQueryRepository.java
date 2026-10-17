package me.jsjlog.blog.admin.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.QMember;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.domain.PostReactionType;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.domain.QComment;
import me.jsjlog.blog.post.domain.QPost;
import me.jsjlog.blog.post.domain.QPostReaction;
import me.jsjlog.blog.post.domain.QPostView;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 기간 조회 통계. 관리자 본인 조회와 검색 봇은 모든 숫자에서 뺀다 — 둘 다 독자가 아니다.
 * 뺀 건수는 따로 세어 화면이 알린다.
 *
 * <p>기간은 [시작, 끝) 으로 받는다. 경계 시각은 서비스가 서울 자정으로 계산해 넘긴다.</p>
 */
@Repository
@RequiredArgsConstructor
public class AdminViewStatsQueryRepository {

    private static final QPostView VIEW = QPostView.postView;
    private static final QMember MEMBER = QMember.member;

    private final JPAQueryFactory jpaQueryFactory;

    public record Totals(long pageViews, long memberViews, long uniqueMembers) {
    }

    public record DailyCount(LocalDate date, long pageViews, long memberViews) {
    }

    public record PostCount(Long postId, long pageViews, long uniqueMembers, long memberViews) {
    }

    public record PostInfo(Long postId, String title, PostStatus status, boolean deleted, long totalViews) {
    }

    public Totals totals(LocalDateTime from, LocalDateTime to) {
        Tuple row = jpaQueryFactory
                .select(VIEW.count(), VIEW.memberId.count(), VIEW.memberId.countDistinct())
                .from(VIEW)
                .leftJoin(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), readers())
                .fetchOne();

        return row == null ? new Totals(0, 0, 0) : new Totals(
                orZero(row.get(VIEW.count())),
                orZero(row.get(VIEW.memberId.count())),
                orZero(row.get(VIEW.memberId.countDistinct()))
        );
    }

    public long adminViews(LocalDateTime from, LocalDateTime to) {
        return orZero(jpaQueryFactory
                .select(VIEW.count())
                .from(VIEW)
                .join(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), MEMBER.role.eq(MemberRole.ADMIN))
                .fetchOne());
    }

    /** 스스로 봇이라고 밝힌 비회원 기록 */
    public long botViews(LocalDateTime from, LocalDateTime to) {
        return orZero(jpaQueryFactory
                .select(VIEW.count())
                .from(VIEW)
                .where(inRange(from, to), VIEW.memberId.isNull(), VIEW.deviceType.eq(DeviceType.BOT))
                .fetchOne());
    }

    /**
     * 날짜별 건수.
     *
     * <p>DB 의 날짜 함수 대신 연·월·일 숫자로 묶는다. 저장된 시각은 서울 시각이라
     * ({@code hibernate.jdbc.time_zone}) 숫자가 곧 서울 날짜다. 날짜 값으로 받으면 드라이버가
     * 서버 시간대로 한 번 더 옮겨 하루가 밀릴 수 있다.</p>
     */
    public List<DailyCount> daily(LocalDateTime from, LocalDateTime to) {
        NumberExpression<Integer> year = VIEW.viewedAt.year();
        NumberExpression<Integer> month = VIEW.viewedAt.month();
        NumberExpression<Integer> day = VIEW.viewedAt.dayOfMonth();

        return jpaQueryFactory
                .select(year, month, day, VIEW.count(), VIEW.memberId.count())
                .from(VIEW)
                .leftJoin(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), readers())
                .groupBy(year, month, day)
                .fetch()
                .stream()
                .map(row -> new DailyCount(
                        LocalDate.of(row.get(year), row.get(month), row.get(day)),
                        orZero(row.get(VIEW.count())),
                        orZero(row.get(VIEW.memberId.count()))
                ))
                .toList();
    }

    public Map<DeviceType, Long> devices(LocalDateTime from, LocalDateTime to) {
        return jpaQueryFactory
                .select(VIEW.deviceType, VIEW.count())
                .from(VIEW)
                .leftJoin(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), readers())
                .groupBy(VIEW.deviceType)
                .fetch()
                .stream()
                .collect(Collectors.toMap(row -> row.get(VIEW.deviceType), row -> orZero(row.get(VIEW.count()))));
    }

    /** 기간 안 기록이 많은 글부터. 같으면 최근 글(번호가 큰 글)이 먼저다 */
    public List<PostCount> postCounts(LocalDateTime from, LocalDateTime to, int page, int size) {
        return jpaQueryFactory
                .select(VIEW.postId, VIEW.count(), VIEW.memberId.countDistinct(), VIEW.memberId.count())
                .from(VIEW)
                .leftJoin(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), readers())
                .groupBy(VIEW.postId)
                .orderBy(VIEW.count().desc(), VIEW.postId.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(row -> new PostCount(
                        row.get(VIEW.postId),
                        orZero(row.get(VIEW.count())),
                        orZero(row.get(VIEW.memberId.countDistinct())),
                        orZero(row.get(VIEW.memberId.count()))
                ))
                .toList();
    }

    public long countPosts(LocalDateTime from, LocalDateTime to) {
        return orZero(jpaQueryFactory
                .select(VIEW.postId.countDistinct())
                .from(VIEW)
                .leftJoin(MEMBER).on(MEMBER.id.eq(VIEW.memberId))
                .where(inRange(from, to), readers())
                .fetchOne());
    }

    public Map<Long, PostInfo> postInfos(Collection<Long> postIds) {
        QPost post = QPost.post;

        return jpaQueryFactory
                .select(post.id, post.title, post.status, post.deletedAt, post.views)
                .from(post)
                .where(post.id.in(postIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(row -> row.get(post.id), row -> new PostInfo(
                        row.get(post.id),
                        row.get(post.title),
                        row.get(post.status),
                        row.get(post.deletedAt) != null,
                        orZero(row.get(post.views))
                )));
    }

    /** 지금 남아 있는 댓글 수. 공개 화면의 댓글 수와 같은 기준(지우거나 가린 댓글 제외)이다 */
    public Map<Long, Long> commentCounts(Collection<Long> postIds) {
        QComment comment = QComment.comment;

        return jpaQueryFactory
                .select(comment.post.id, comment.count())
                .from(comment)
                .where(comment.post.id.in(postIds), comment.deleted.isFalse())
                .groupBy(comment.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(row -> row.get(comment.post.id), row -> orZero(row.get(comment.count()))));
    }

    /** 지금 눌려 있는 좋아요 수. 취소하면 빠지므로 과거 어느 기간의 수가 아니다 */
    public Map<Long, Long> likeCounts(Collection<Long> postIds) {
        QPostReaction reaction = QPostReaction.postReaction;

        return jpaQueryFactory
                .select(reaction.post.id, reaction.count())
                .from(reaction)
                .where(reaction.post.id.in(postIds), reaction.type.eq(PostReactionType.LIKE))
                .groupBy(reaction.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(row -> row.get(reaction.post.id), row -> orZero(row.get(reaction.count()))));
    }

    private static BooleanExpression inRange(LocalDateTime from, LocalDateTime to) {
        return VIEW.viewedAt.goe(from).and(VIEW.viewedAt.lt(to));
    }

    /** 독자의 기록만. 관리자 기록과 검색 봇 기록을 뺀다. 회원 행이 없는 기록(비회원)은 남긴다 */
    private static BooleanExpression readers() {
        return MEMBER.role.isNull().or(MEMBER.role.ne(MemberRole.ADMIN))
                .and(VIEW.deviceType.ne(DeviceType.BOT));
    }

    private static long orZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
