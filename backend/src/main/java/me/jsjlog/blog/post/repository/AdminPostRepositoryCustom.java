package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.dto.AdminPostStatusCounts;
import me.jsjlog.blog.admin.dto.AdminPostSummaryResponse;

import java.util.List;

/**
 * 관리자 글 목록 조회.
 * 리포지토리는 공개용과 관리자용이 같이 쓰지만, 메서드는 쓰임새별로 나눈다.
 */
public interface AdminPostRepositoryCustom {

    /** 조건에 맞는 글 한 페이지 */
    List<AdminPostSummaryResponse> getAdminPosts(AdminPostSearchCondition condition);

    /** 조건에 맞는 전체 건수 (페이징 계산용) */
    long countAdminPosts(AdminPostSearchCondition condition);

    /** 상태별 건수. status 필터를 빼고 센다 */
    AdminPostStatusCounts countByStatus(AdminPostSearchCondition condition);
}
