package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.admin.dto.AdminCategoryResponse;
import me.jsjlog.blog.admin.dto.AdminCategorySearchCondition;

import java.util.List;

public interface CategoryRepositoryCustom {

	List<AdminCategoryResponse> getCategoryListWithPostCount(AdminCategorySearchCondition condition);

}
