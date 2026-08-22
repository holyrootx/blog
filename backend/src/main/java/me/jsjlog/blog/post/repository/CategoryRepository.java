package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.dto.CategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<CategoryResponse> findAllBy(Sort sort);
}
