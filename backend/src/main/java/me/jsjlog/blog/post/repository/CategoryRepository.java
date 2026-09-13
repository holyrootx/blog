package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.dto.CategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, CategoryRepositoryCustom {
    List<CategoryResponse> findAllBy(Sort sort);

    boolean existsByName(String name);

    /** 수정할 때 자기 자신은 빼고 이름 중복을 본다 */
    boolean existsByNameAndIdNot(String name, Long id);

    /** 등록할 때 순서를 안 주면 마지막 다음 번호를 매기려고 쓴다 */
    Optional<Category> findTopByOrderBySortOrderDesc();
}
