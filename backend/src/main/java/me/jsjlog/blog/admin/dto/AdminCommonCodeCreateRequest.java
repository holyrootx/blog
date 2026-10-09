package me.jsjlog.blog.admin.dto;

/**
 * 그룹 안에 코드 추가. 코드 값은 만든 뒤 바꿀 수 없다.
 *
 * @param sortOrder 비우면 그룹의 맨 뒤
 */
public record AdminCommonCodeCreateRequest(
        String code,
        String codeName,
        String description,
        Integer sortOrder
) {
}
