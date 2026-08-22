package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.dto.CategoryResponse;
import me.jsjlog.blog.post.repository.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories(){
        Sort sortOrder = Sort.by(Sort.Direction.ASC, "sortOrder");
        return categoryRepository.findAllBy(sortOrder);
    }
}
