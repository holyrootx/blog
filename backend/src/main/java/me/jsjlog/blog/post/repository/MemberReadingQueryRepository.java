package me.jsjlog.blog.post.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.time.ServerTime;
import me.jsjlog.blog.post.domain.QCategory;
import me.jsjlog.blog.post.domain.QPost;
import me.jsjlog.blog.post.domain.QPostView;
import me.jsjlog.blog.post.dto.RecentPostResponse;
import me.jsjlog.blog.post.dto.ViewedPostResponse;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 회원 한 사람의 조회 기록으로 "최근 본 글" 을 만든다.
 *
 * <p>지금 공개되지 않은 글은 뺀다. 내렸거나 휴지통에 간 글의 제목을 보여 주면, 공개 화면에서
 * 감춘 글이 이 목록으로 새어 나간다.</p>
 */
@Repository
@RequiredArgsConstructor
public class MemberReadingQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<RecentPostResponse> getRecentPosts(Long memberId, int page, int size) {
        QPostView view = QPostView.postView;
        QPost post = QPost.post;
        QCategory category = QCategory.category;
        DateTimeExpression<LocalDateTime> lastViewedAt = view.lastViewedAt.coalesce(view.viewedAt).max();

        List<Tuple> rows = jpaQueryFactory
                .select(view.postId, post.title, category.name, post.thumbnailImageUrl, lastViewedAt)
                .from(view)
                .join(post).on(post.id.eq(view.postId))
                .leftJoin(post.category, category)
                .where(view.memberId.eq(memberId), PublicPostCondition.of(post))
                .groupBy(view.postId, post.title, category.name, post.thumbnailImageUrl)
                .orderBy(lastViewedAt.desc(), view.postId.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();

        return rows.stream()
                .map(row -> new RecentPostResponse(
                        row.get(view.postId),
                        row.get(post.title),
                        row.get(category.name),
                        row.get(post.thumbnailImageUrl),
                        ServerTime.toOffset(row.get(lastViewedAt))
                ))
                .toList();
    }

    public long countRecentPosts(Long memberId) {
        QPostView view = QPostView.postView;
        QPost post = QPost.post;

        Long count = jpaQueryFactory
                .select(view.postId.countDistinct())
                .from(view)
                .join(post).on(post.id.eq(view.postId))
                .where(view.memberId.eq(memberId), PublicPostCondition.of(post))
                .fetchOne();

        return Objects.requireNonNullElse(count, 0L);
    }

    /** 글 목록 한 화면의 카드를 한 번에 조회한다. 기존 행은 최초 기록 시각을 사용한다. */
    public List<ViewedPostResponse> getViewedPosts(Long memberId, Collection<Long> postIds) {
        QPostView view = QPostView.postView;
        QPost post = QPost.post;
        DateTimeExpression<LocalDateTime> lastViewedAt = view.lastViewedAt.coalesce(view.viewedAt).max();

        return jpaQueryFactory
                .select(view.postId, lastViewedAt)
                .from(view)
                .join(post).on(post.id.eq(view.postId))
                .where(view.memberId.eq(memberId), view.postId.in(postIds), PublicPostCondition.of(post))
                .groupBy(view.postId)
                .fetch()
                .stream()
                .map(row -> new ViewedPostResponse(row.get(view.postId), ServerTime.toOffset(row.get(lastViewedAt))))
                .toList();
    }
}
