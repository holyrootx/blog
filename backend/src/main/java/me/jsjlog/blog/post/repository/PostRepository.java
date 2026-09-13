package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post,Long>, PostRepositoryCustom, AdminPostRepositoryCustom {

    /**
     * 카테고리 삭제 가능 여부 판단용.
     * 발행 여부와 상관없이 한 건이라도 있으면 삭제할 수 없다
     * (Post.category 가 nullable = false 라 남겨둘 수 없기 때문).
     */
    boolean existsByCategoryId(Long categoryId);
}
