package me.jsjlog.blog.post.repository;

import java.time.LocalDateTime;
import java.util.List;

import me.jsjlog.blog.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
