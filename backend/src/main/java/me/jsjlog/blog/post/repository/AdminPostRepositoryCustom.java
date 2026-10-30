package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.dto.AdminPostStatusCounts;
import me.jsjlog.blog.admin.dto.AdminPostSummaryResponse;

import java.util.List;

public interface AdminPostRepositoryCustom {

	List<AdminPostSummaryResponse> getAdminPosts(AdminPostSearchCondition condition);

	long countAdminPosts(AdminPostSearchCondition condition);

	/** 상태별 건수. status 필터를 빼고 센다 */
	AdminPostStatusCounts countByStatus(AdminPostSearchCondition condition);
}
