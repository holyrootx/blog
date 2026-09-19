package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.NumberExpression;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.domain.CommentReactionType;
import me.jsjlog.blog.post.domain.QComment;
import me.jsjlog.blog.post.domain.QCommentReaction;
import me.jsjlog.blog.post.dto.CommentItemResponse;
import me.jsjlog.blog.post.dto.CommentListResponse;
import me.jsjlog.blog.post.dto.CommentReplyResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    /** 최상위 댓글 한 페이지와 그 답글·반응을 고정된 수의 쿼리로 조회한다. */
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
                        comment.member.nickname,
                        comment.content,
                        comment.createdAt,
                        comment.member.role,
                        comment.deleted
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

        Map<Long, List<ReplyRow>> repliesByParentId = getReplyRowsByParentId(parentIds);
        List<Long> visibleCommentIds = collectVisibleCommentIds(parentIds, repliesByParentId);
        ReactionData reactionData = getReactionData(visibleCommentIds, memberId);

        List<CommentItemResponse> items = new ArrayList<>();
        for (Tuple commentRow : commentRows) {
            Long commentId = commentRow.get(comment.id);
            boolean deleted = Boolean.TRUE.equals(commentRow.get(comment.deleted));
            ReactionSummary reactions = reactionData.summary(commentId);

            List<CommentReplyResponse> replies = repliesByParentId
                    .getOrDefault(commentId, List.of())
                    .stream()
                    .map(reply -> toReplyResponse(reply, reactionData.summary(reply.id())))
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
                    replies
            ));
        }

        Long nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            nextCursor = items.get(items.size() - 1).id();
        }

        return new CommentListResponse(countVisibleComments(postId), items, nextCursor, hasNext);
    }

    private Map<Long, List<ReplyRow>> getReplyRowsByParentId(List<Long> parentIds) {
        Map<Long, List<ReplyRow>> repliesByParentId = new HashMap<>();
        if (parentIds.isEmpty()) {
            return repliesByParentId;
        }

        QComment reply = new QComment("reply");

        List<Tuple> replyRows = jpaQueryFactory.select(
                        reply.parent.id,
                        reply.id,
                        reply.member.nickname,
                        reply.content,
                        reply.createdAt,
                        reply.member.role,
                        reply.deleted
                ).from(reply)
                .where(
                        reply.parent.id.in(parentIds),
                        reply.deleted.isFalse()
                )
                .orderBy(reply.parent.id.desc(), reply.id.asc())
                .fetch();

        for (Tuple row : replyRows) {
            Long parentId = row.get(reply.parent.id);
            ReplyRow replyRow = new ReplyRow(
                    row.get(reply.id),
                    row.get(reply.member.nickname),
                    row.get(reply.content),
                    row.get(reply.createdAt),
                    row.get(reply.member.role),
                    Boolean.TRUE.equals(row.get(reply.deleted))
            );

            repliesByParentId.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(replyRow);
        }

        return repliesByParentId;
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

    private CommentReplyResponse toReplyResponse(ReplyRow reply, ReactionSummary reactions) {
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
                reactions.dislikedByMe()
        );
    }

    private long countVisibleComments(Long postId) {
        QComment comment = QComment.comment;

        Long total = jpaQueryFactory.select(comment.count())
                .from(comment)
                .where(
                        comment.post.id.eq(postId),
                        comment.deleted.isFalse()
                )
                .fetchOne();

        return total == null ? 0L : total;
    }

    private record ReplyRow(
            Long id,
            String nickname,
            String content,
            LocalDateTime createdAt,
            MemberRole role,
            boolean deleted
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
