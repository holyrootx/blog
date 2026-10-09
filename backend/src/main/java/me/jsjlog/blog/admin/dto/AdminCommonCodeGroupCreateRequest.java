package me.jsjlog.blog.admin.dto;

/**
 * 공통 코드 그룹 추가. 그룹 코드는 만든 뒤 바꿀 수 없다.
 */
public record AdminCommonCodeGroupCreateRequest(
        String groupCode,
        String groupName,
        String description
) {
}
