package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.NumberExpression;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.QComment;
import me.jsjlog.blog.post.domain.QCommentReaction;
import me.jsjlog.blog.post.domain.QCommentReport;
import me.jsjlog.blog.post.dto.CommentItemResponse;
import me.jsjlog.blog.post.dto.CommentListResponse;
import me.jsjlog.blog.post.dto.CommentReplyResponse;
import me.jsjlog.blog.post.dto.CommentReplyListResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private static final int INITIAL_REPLY_SIZE = 10;

    private final JPAQueryFactory jpaQueryFactory;
    private final EntityManager entityManager;

    /** 각 부모에서 처음 답글 10개만 읽고, 반응은 반환할 댓글에 대해서만 모은다. */
    @Override
    public CommentListResponse getCommentPageByPostId(
            Long postId,
            Long cursor,
            long size,
            Long memberId
    ) {
        QComment comment = QComment.comment;
        QComment replyExists = new QComment("replyExists");

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(comment.post.id.eq(postId));
        builder.and(PublicPostCondition.of(comment.post));
        builder.and(comment.parent.isNull());

        if (cursor != null) {
            builder.and(comment.id.lt(cursor));
        }

        // 삭제된 댓글은 살아있는 답글이 있을 때만 자리를 남긴다.
        builder.and(
                comment.deleted.isFalse()
                        .or(JPAExpressions
                                .selectOne()
                                .from(replyExists)
                                .where(
                                        replyExists.parent.id.eq(comment.id),
                                        replyExists.deleted.isFalse()
                                )
                                .exists())
        );

        // 다음 묶음이 있는지 보려고 한 건 더 가져온다.
        List<Tuple> commentRows = jpaQueryFactory.select(
                        comment.id,
                        comment.member.id,
                        comment.member.nickname,
                        comment.content,
                        comment.createdAt,
                        comment.member.role,
                        comment.deleted,
                        comment.edited,
                        comment.hiddenByAdmin
                ).from(comment)
                .where(builder)
                .orderBy(comment.id.desc())
                .limit(size + 1)
                .fetch();

        boolean hasNext = commentRows.size() > size;
        if (hasNext) {
            commentRows = commentRows.subList(0, (int) size);
        }

        List<Long> parentIds = commentRows.stream()
                .map(row -> row.get(comment.id))
                .toList();

        Map<Long, List<ReplyRow>> repliesByParentId = getInitialReplyRows(postId, parentIds, INITIAL_REPLY_SIZE + 1);
        Set<Long> parentsWithMoreReplies = new HashSet<>();
        repliesByParentId.replaceAll((parentId, rows) -> {
            if (rows.size() <= INITIAL_REPLY_SIZE) {
                return rows;
            }
            parentsWithMoreReplies.add(parentId);
            return rows.subList(0, INITIAL_REPLY_SIZE);
        });
        List<Long> visibleCommentIds = collectVisibleCommentIds(parentIds, repliesByParentId);
        ReactionData reactionData = getReactionData(visibleCommentIds, memberId);
        Set<Long> reportedByMe = getReportedCommentIds(visibleCommentIds, memberId);

        List<CommentItemResponse> items = new ArrayList<>();
        for (Tuple commentRow : commentRows) {
            Long commentId = commentRow.get(comment.id);
            boolean deleted = Boolean.TRUE.equals(commentRow.get(comment.deleted));
            ReactionSummary reactions = reactionData.summary(commentId);

            List<CommentReplyResponse> replies = repliesByParentId
                    .getOrDefault(commentId, List.of())
                    .stream()
                    .map(reply -> toReplyResponse(
                            reply,
                            reactionData.summary(reply.id()),
                            memberId,
                            reportedByMe.contains(reply.id())))
                    .toList();

            items.add(new CommentItemResponse(
                    commentId,
                    deleted ? null : commentRow.get(comment.member.nickname),
                    deleted ? null : commentRow.get(comment.content),
                    commentRow.get(comment.createdAt),
                    commentRow.get(comment.member.role) == MemberRole.ADMIN,
                    deleted,
                    deleted ? 0L : reactions.likeCount(),
                    deleted ? 0L : reactions.dislikeCount(),
                    !deleted && reactions.likedByMe(),
                    !deleted && reactions.dislikedByMe(),
                    // 삭제된 댓글은 내 것이라도 내 것으로 치지 않는다. 지워진 자리에
                    // 신고할 것도 감출 것도 없다
                    !deleted && isMine(commentRow.get(comment.member.id), memberId),
                    // 지운 댓글은 내용이 사라지므로 고쳤다는 표시도 뜻이 없다
                    !deleted && Boolean.TRUE.equals(commentRow.get(comment.edited)),
                    deleted && Boolean.TRUE.equals(commentRow.get(comment.hiddenByAdmin)),
                    !deleted && reportedByMe.contains(commentId),
                    replies,
                    parentsWithMoreReplies.contains(commentId) ? replies.getLast().id() : null,
                    parentsWithMoreReplies.contains(commentId)
            ));
        }

        Long nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            nextCursor = items.get(items.size() - 1).id();
        }

        return new CommentListResponse(countVisibleComments(postId), items, nextCursor, hasNext);
    }

    @Override
    public CommentReplyListResponse getReplyPage(Long postId, Long parentId, Long cursor, long size, Long memberId) {
        List<ReplyRow> rows = getReplyRows(postId, parentId, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        if (hasNext) rows = rows.subList(0, (int) size);
        List<Long> ids = rows.stream().map(ReplyRow::id).toList();
        ReactionData reactions = getReactionData(ids, memberId);
        Set<Long> reported = getReportedCommentIds(ids, memberId);
        List<CommentReplyResponse> replies = rows.stream()
                .map(row -> toReplyResponse(row, reactions.summary(row.id()), memberId, reported.contains(row.id())))
                .toList();
        return new CommentReplyListResponse(replies, hasNext ? replies.getLast().id() : null, hasNext);
    }

    /**
     * 한 페이지의 부모 댓글들에 대해, 부모마다 앞쪽 답글을 {@code limit} 개까지 한 번에 읽는다.
     *
     * <p>부모마다 따로 물으면 한 페이지 50개에 답글 쿼리만 50번 나간다. 부모별로 끊어 세려면
     * 창 함수가 필요한데 QueryDSL(JPQL) 이 그것도, FROM 절 서브쿼리도 표현하지 못해서 여기만 HQL 로 쓴다.
     * MySQL 8 과 테스트용 H2 모두 {@code row_number() over} 를 지원한다.</p>
     *
     * <p>글이 공개 상태인지는 다시 보지 않는다. 부모를 고른 쿼리가 같은 글을 공개 조건으로 이미 걸렀다.</p>
     */
    private Map<Long, List<ReplyRow>> getInitialReplyRows(Long postId, List<Long> parentIds, int limit) {
        Map<Long, List<ReplyRow>> rowsByParentId = new HashMap<>();
        if (parentIds.isEmpty()) {
            return rowsByParentId;
        }

        List<Object[]> rows = entityManager.createQuery("""
                        select r.id, r.parentId, r.memberId, r.nickname, r.content, r.createdAt, r.role,
                               r.deleted, r.edited
                        from (
                            select c.id as id, c.parent.id as parentId, m.id as memberId, m.nickname as nickname,
                                   c.content as content, c.createdAt as createdAt, m.role as role,
                                   c.deleted as deleted, c.edited as edited,
                                   row_number() over (partition by c.parent.id order by c.id) as replyOrder
                            from Comment c join c.member m
                            where c.post.id = :postId and c.parent.id in :parentIds and c.deleted = false
                        ) r
                        where r.replyOrder <= :limit
                        order by r.parentId, r.id
                        """, Object[].class)
                .setParameter("postId", postId)
                .setParameter("parentIds", parentIds)
                .setParameter("limit", limit)
                .getResultList();

        for (Object[] row : rows) {
            rowsByParentId.computeIfAbsent((Long) row[1], parentId -> new ArrayList<>()).add(new ReplyRow(
                    (Long) row[0], (Long) row[2], (String) row[3], (String) row[4], (LocalDateTime) row[5],
                    (MemberRole) row[6], Boolean.TRUE.equals(row[7]), Boolean.TRUE.equals(row[8])));
        }
        return rowsByParentId;
    }

    private List<ReplyRow> getReplyRows(Long postId, Long parentId, Long cursor, long limit) {
        QComment reply = new QComment("reply");
        List<Tuple> rows = jpaQueryFactory.select(
                        reply.id, reply.member.id, reply.member.nickname, reply.content,
                        reply.createdAt, reply.member.role, reply.deleted, reply.edited)
                .from(reply)
                .where(reply.post.id.eq(postId), reply.parent.id.eq(parentId),
                        PublicPostCondition.of(reply.post),
                        reply.deleted.isFalse(), cursor == null ? null : reply.id.gt(cursor))
                .orderBy(reply.id.asc())
                .limit(limit)
                .fetch();
        return rows.stream().map(row -> new ReplyRow(
                row.get(reply.id), row.get(reply.member.id), row.get(reply.member.nickname),
                row.get(reply.content), row.get(reply.createdAt), row.get(reply.member.role),
                Boolean.TRUE.equals(row.get(reply.deleted)), Boolean.TRUE.equals(row.get(reply.edited))))
                .toList();
    }

    private List<Long> collectVisibleCommentIds(
            List<Long> parentIds,
            Map<Long, List<ReplyRow>> repliesByParentId
    ) {
        List<Long> commentIds = new ArrayList<>(parentIds);
        repliesByParentId.values().forEach(replies ->
                replies.forEach(reply -> commentIds.add(reply.id())));
        return commentIds;
    }

    /** 반응 개수와 현재 회원의 반응을 댓글 페이지 전체에 대해 각각 한 번씩 조회한다. */
    private ReactionData getReactionData(List<Long> commentIds, Long memberId) {
        if (commentIds.isEmpty()) {
            return ReactionData.empty();
        }

        QCommentReaction reaction = QCommentReaction.commentReaction;
        NumberExpression<Long> reactionCount = reaction.count();
        Map<Long, Map<CommentReactionType, Long>> counts = new HashMap<>();

        List<Tuple> countRows = jpaQueryFactory.select(
                        reaction.comment.id,
                        reaction.type,
                        reactionCount
                ).from(reaction)
                .where(reaction.comment.id.in(commentIds))
                .groupBy(reaction.comment.id, reaction.type)
                .fetch();

        for (Tuple row : countRows) {
            Long commentId = row.get(reaction.comment.id);
            CommentReactionType type = row.get(reaction.type);
            Long count = row.get(reactionCount);

            counts.computeIfAbsent(commentId, ignored -> new EnumMap<>(CommentReactionType.class))
                    .put(type, count == null ? 0L : count);
        }

        Map<Long, Set<CommentReactionType>> myReactions = new HashMap<>();
        if (memberId != null) {
            List<Tuple> memberRows = jpaQueryFactory.select(reaction.comment.id, reaction.type)
                    .from(reaction)
                    .where(
                            reaction.comment.id.in(commentIds),
                            reaction.member.id.eq(memberId)
                    )
                    .fetch();

            for (Tuple row : memberRows) {
                myReactions
                        .computeIfAbsent(
                                row.get(reaction.comment.id),
                                ignored -> EnumSet.noneOf(CommentReactionType.class)
                        )
                        .add(row.get(reaction.type));
            }
        }

        return new ReactionData(counts, myReactions);
    }

    private CommentReplyResponse toReplyResponse(
            ReplyRow reply,
            ReactionSummary reactions,
            Long memberId,
            boolean reportedByMe
    ) {
        return new CommentReplyResponse(
                reply.id(),
                reply.nickname(),
                reply.content(),
                reply.createdAt(),
                reply.role() == MemberRole.ADMIN,
                reply.deleted(),
                reactions.likeCount(),
                reactions.dislikeCount(),
                reactions.likedByMe(),
                reactions.dislikedByMe(),
                isMine(reply.memberId(), memberId),
                reply.edited(),
                reportedByMe
        );
    }

    /**
     * 이 사람이 이미 신고한 댓글들.
     *
     * <p>좋아요를 모아 오는 것과 같은 방식이다. 댓글마다 따로 물으면 목록 한 번에
     * 쿼리가 스무 번 나간다.</p>
     *
     * <p>로그인하지 않았으면 물어보지 않는다 — 신고한 것이 있을 수가 없다.</p>
     */
    private Set<Long> getReportedCommentIds(List<Long> commentIds, Long memberId) {
        if (memberId == null || commentIds.isEmpty()) {
            return Set.of();
        }

        QCommentReport report = QCommentReport.commentReport;

        return new HashSet<>(jpaQueryFactory
                .select(report.comment.id)
                .from(report)
                .where(report.comment.id.in(commentIds), report.member.id.eq(memberId))
                .fetch());
    }

    /**
     * 이 글을 보고 있는 사람이 쓴 것인가.
     *
     * <p>로그인하지 않았으면 언제나 아니다. {@code null == null} 로 걸려서 남의 댓글이
     * 전부 "내 것" 이 되는 일을 막는다.</p>
     */
    private boolean isMine(Long writerId, Long viewerId) {
        return viewerId != null && viewerId.equals(writerId);
    }

    private long countVisibleComments(Long postId) {
        QComment comment = QComment.comment;

        Long total = jpaQueryFactory.select(comment.count())
                .from(comment)
                .where(
                        comment.post.id.eq(postId),
                        PublicPostCondition.of(comment.post),
                        comment.deleted.isFalse()
                )
                .fetchOne();

        return total == null ? 0L : total;
    }

    private record ReplyRow(
            Long id,
            Long memberId,
            String nickname,
            String content,
            LocalDateTime createdAt,
            MemberRole role,
            boolean deleted,
            boolean edited
    ) {
    }

    private record ReactionSummary(
            long likeCount,
            long dislikeCount,
            boolean likedByMe,
            boolean dislikedByMe
    ) {
    }

    private record ReactionData(
            Map<Long, Map<CommentReactionType, Long>> counts,
            Map<Long, Set<CommentReactionType>> myReactions
    ) {
        private static ReactionData empty() {
            return new ReactionData(Map.of(), Map.of());
        }

        private ReactionSummary summary(Long commentId) {
            Map<CommentReactionType, Long> commentCounts = counts.getOrDefault(commentId, Map.of());
            Set<CommentReactionType> memberReactions = myReactions.getOrDefault(commentId, Set.of());
            return new ReactionSummary(
                    commentCounts.getOrDefault(CommentReactionType.LIKE, 0L),
                    commentCounts.getOrDefault(CommentReactionType.DISLIKE, 0L),
                    memberReactions.contains(CommentReactionType.LIKE),
                    memberReactions.contains(CommentReactionType.DISLIKE)
            );
        }
    }
}
