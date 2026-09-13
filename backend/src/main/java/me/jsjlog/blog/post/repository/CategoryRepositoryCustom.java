package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.admin.dto.AdminCategoryResponse;
import me.jsjlog.blog.admin.dto.AdminCategorySearchCondition;

import java.util.List;

/**
 * 카테고리 QueryDSL 조회.
 * 리포지토리는 데이터 접근 계층이라 공개용과 관리자용이 같이 쓴다.
 */
public interface CategoryRepositoryCustom {

    /** 카테고리 목록 + 카테고리별 글 수 */
    List<AdminCategoryResponse> getCategoryListWithPostCount(AdminCategorySearchCondition condition);

}
