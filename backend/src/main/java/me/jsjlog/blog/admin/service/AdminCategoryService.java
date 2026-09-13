package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminCategoryRequest;
import me.jsjlog.blog.admin.dto.AdminCategoryResponse;
import me.jsjlog.blog.admin.dto.AdminCategorySearchCondition;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ConcurrencyGuard;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 관리자 카테고리 관리.
 *
 * 클래스 이름에 Admin 을 붙인 이유:
 * 빈 이름은 클래스 단순명에서 나오므로 post.service.CategoryService 와 이름이 겹치면
 * 패키지가 달라도 부팅이 실패한다(ConflictingBeanDefinitionException).
 *
 * 엔티티와 리포지토리는 공개용과 공유한다. 테이블이 하나인데 엔티티가 둘이면 안 된다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;

    /** 목록 (글 수 포함) */
    public List<AdminCategoryResponse> getCategoryList(AdminCategorySearchCondition condition) {
        return categoryRepository.getCategoryListWithPostCount(condition);
    }

    /** 등록 */
    @Transactional
    public Long createCategory(AdminCategoryRequest request) {
        String name = requireName(request.name());

        // name 이 unique 라 DB 에서도 막히지만, 그때는 500 이 나간다.
        // 어떤 이유로 실패했는지 알려주려면 먼저 검사해야 한다
        if (categoryRepository.existsByName(name)) {
            throw new BlogException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        Category category = new Category(name, resolveSortOrder(request.sortOrder()));

        return categoryRepository.save(category).getId();
    }

    /**
     * 수정.
     *
     * 주의: sort_order 에 unique 제약이 남아 있으면 순서 변경은 항상 실패한다.
     * (1번을 2번으로 바꾸려면 기존 2번이 먼저 비켜야 하는데 그럴 방법이 없다)
     */
    @Transactional
    public void updateCategory(Long categoryId, AdminCategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BlogException(ErrorCode.CATEGORY_NOT_FOUND));

        // 화면이 불러온 뒤 다른 곳에서 바뀌었으면 덮어쓰지 않는다
        ConcurrencyGuard.check(request.updatedAt(), category.getUpdatedAt());

        String name = requireName(request.name());

        // 이름을 안 바꾸는 수정도 있으므로 자기 자신은 중복 검사에서 뺀다
        if (categoryRepository.existsByNameAndIdNot(name, categoryId)) {
            throw new BlogException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        Long sortOrder = request.sortOrder() == null ? category.getSortOrder() : request.sortOrder();

        category.update(name, sortOrder);
    }

    /** 삭제 */
    @Transactional
    public void deleteCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BlogException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        // 발행 여부와 상관없이 한 건이라도 있으면 못 지운다.
        // Post.category 가 nullable = false 라 글을 남겨둘 수 없기 때문
        if (postRepository.existsByCategoryId(categoryId)) {
            throw new BlogException(ErrorCode.CATEGORY_IN_USE);
        }

        categoryRepository.deleteById(categoryId);
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            // name 이 nullable = false 라 그냥 두면 500 이 나간다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        return name.trim();
    }

    /** 순서를 안 주면 마지막 다음 번호를 매긴다 */
    private Long resolveSortOrder(Long sortOrder) {
        if (sortOrder != null) {
            return sortOrder;
        }

        return categoryRepository.findTopByOrderBySortOrderDesc()
                .map(category -> category.getSortOrder() + 1)
                .orElse(1L);
    }

}
