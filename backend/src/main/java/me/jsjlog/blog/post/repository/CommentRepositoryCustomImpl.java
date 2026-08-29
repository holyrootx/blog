package me.jsjlog.blog.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.domain.QComment;
import me.jsjlog.blog.post.dto.CommentItemResponse;
import me.jsjlog.blog.post.dto.CommentListResponse;
import me.jsjlog.blog.post.dto.CommentReplyResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    /**
     * 커서 기준으로 최상위 댓글을 가져오고, 답글은 부모에 묶어서 함께 내려줍니다.
     * @return
     */
    @Override
    public CommentListResponse getCommentPageByPostId(Long postId, Long cursor, long size) {

        QComment comment = QComment.comment;
        QComment replyExists = new QComment("replyExists");

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(comment.post.id.eq(postId));

        builder.and(comment.parent.isNull());

        if (cursor != null) {
            builder.and(comment.id.lt(cursor));
        }

        // 삭제된 댓글은 살아있는 답글이 있을 때만 자리를 남긴다
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

        // 다음 묶음이 있는지 보려고 한 건 더 가져온다
        List<Tuple> commentRows = jpaQueryFactory.select(
                        comment.id,
                        comment.guestNickname,
                        comment.content,
                        comment.createdAt,
                        comment.authorComment,
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

        List<Long> parentIds = new ArrayList<>();
        for (Tuple commentRow : commentRows) {
            parentIds.add(commentRow.get(comment.id));
        }

        Map<Long, List<CommentReplyResponse>> repliesByParentId = getRepliesByParentId(parentIds);

        List<CommentItemResponse> items = new ArrayList<>();
        for (Tuple commentRow : commentRows) {
            Long commentId = commentRow.get(comment.id);
            boolean deleted = Boolean.TRUE.equals(commentRow.get(comment.deleted));

            List<CommentReplyResponse> replies = repliesByParentId.get(commentId);
            if (replies == null) {
                replies = new ArrayList<>();
            }

            // 삭제된 댓글은 닉네임과 본문을 내려주지 않는다
            String nickname = deleted ? null : commentRow.get(comment.guestNickname);
            String content = deleted ? null : commentRow.get(comment.content);

            items.add(new CommentItemResponse(
                    commentId,
                    nickname,
                    content,
                    commentRow.get(comment.createdAt),
                    Boolean.TRUE.equals(commentRow.get(comment.authorComment)),
                    deleted,
                    replies
            ));
        }

        Long nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            nextCursor = items.get(items.size() - 1).id();
        }

        long total = countVisibleComments(postId);

        return new CommentListResponse(total, items, nextCursor, hasNext);
    }

    /**
     * 부모 댓글 id로 답글을 한 번에 가져와 부모별로 묶습니다.
     * 댓글마다 따로 조회하면 N+1이 됩니다.
     * @return
     */
    private Map<Long, List<CommentReplyResponse>> getRepliesByParentId(List<Long> parentIds) {

        Map<Long, List<CommentReplyResponse>> repliesByParentId = new HashMap<>();

        if (parentIds.isEmpty()) {
            return repliesByParentId;
        }

        QComment reply = new QComment("reply");

        ConstructorExpression<CommentReplyResponse> replyProjection = Projections.constructor(
                CommentReplyResponse.class,
                reply.id,
                reply.guestNickname,
                reply.content,
                reply.createdAt,
                reply.authorComment,
                reply.deleted
        );

        List<Tuple> replyRows = jpaQueryFactory.select(
                        reply.parent.id,
                        replyProjection
                ).from(reply)
                .where(
                        reply.parent.id.in(parentIds),
                        reply.deleted.isFalse()
                )
                .orderBy(reply.parent.id.desc(), reply.id.asc())
                .fetch();

        for (Tuple replyRow : replyRows) {
            Long parentId = replyRow.get(reply.parent.id);

            List<CommentReplyResponse> replies = repliesByParentId.get(parentId);
            if (replies == null) {
                replies = new ArrayList<>();
                repliesByParentId.put(parentId, replies);
            }

            replies.add(replyRow.get(replyProjection));
        }

        return repliesByParentId;
    }

    /**
     * 화면에 보이는 댓글 수. 답글을 포함하고 삭제된 댓글은 세지 않습니다.
     * @return
     */
    private long countVisibleComments(Long postId) {

        QComment comment = QComment.comment;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(comment.post.id.eq(postId));

        builder.and(comment.deleted.isFalse());

        Long total = jpaQueryFactory.select(comment.count())
                .from(comment)
                .where(builder)
                .fetchOne();

        if (total == null) {
            return 0L;
        }

        return total;
    }

}
