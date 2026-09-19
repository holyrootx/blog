package me.jsjlog.blog.post.repository;

import java.time.LocalDateTime;
import java.util.List;

import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post,Long>, PostRepositoryCustom, AdminPostRepositoryCustom {

    /**
     * 공개할 때가 된 예약 글.
     *
     * 서버가 꺼져 있던 동안 지나간 시각도 여기서 함께 잡힌다 — 시각을 저장해 두고 매번 비교하므로
     * 놓친 예약이 생기지 않는다.
     */
    @Query("select post from Post post where post.status = me.jsjlog.blog.post.domain.PostStatus.SCHEDULED"
            + " and post.publishedAt <= :now")
    List<Post> findDueScheduledPosts(@Param("now") LocalDateTime now);

    /**
     * 카테고리 삭제 가능 여부 판단용.
     * 발행 여부와 상관없이 한 건이라도 있으면 삭제할 수 없다
     * (Post.category 가 nullable = false 라 남겨둘 수 없기 때문).
     */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 조회수를 DB에서 직접 증가시킨다.
     *
     * <p>엔티티를 읽고 {@code views + 1}을 저장하면 동시에 들어온 조회가 같은 값을 읽어
     * 증가분 하나를 덮어쓸 수 있다. 단일 UPDATE 문으로 처리하면 DB가 증가 연산을 직렬화한다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.views = post.views + 1 "
            + "where post.id = :postId and post.status = :status")
    int increaseViewCount(
            @Param("postId") Long postId,
            @Param("status") PostStatus status
    );
}
