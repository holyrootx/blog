package me.jsjlog.blog.post.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.domain.QPost;

import java.time.LocalDateTime;

/**
 * 독자에게 보여도 되는 글: 발행 상태이고, 휴지통에 없고, 발행 시각이 지났다.
 *
 * <p>같은 조건이 쿼리마다 복사돼 있다가 어떤 곳은 시각을 보고 어떤 곳은 안 보는 식으로 갈라졌다.
 * 예약 글이 공개 화면에 새어 나간 사고가 그 틈에서 났다. 그래서 조회 쿼리는 이 한 곳을 쓰고,
 * 엔티티 쪽 판단은 {@link me.jsjlog.blog.post.domain.Post#isPubliclyVisible} 가 같은 기준을 지킨다.</p>
 */
final class PublicPostCondition {

    private PublicPostCondition() {
    }

    static BooleanExpression of(QPost post) {
        return post.status.eq(PostStatus.PUBLISHED)
                .and(post.deletedAt.isNull())
                .and(post.publishedAt.loe(LocalDateTime.now()));
    }
}
