package me.jsjlog.blog.post.repository;

import jakarta.persistence.LockModeType;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.dto.CategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, CategoryRepositoryCustom {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select category from Category category where category.id = :categoryId")
	Optional<Category> findLockedById(@Param("categoryId") Long categoryId);

	List<CategoryResponse> findAllBy(Sort sort);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	Optional<Category> findTopByOrderBySortOrderDesc();
}
