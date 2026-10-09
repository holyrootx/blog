package me.jsjlog.blog.admin.dto;

/**
 * 공통 코드 그룹 목록 조회 조건.
 *
 * @param keyword 그룹 코드·이름, 또는 그 안 코드의 값·이름 중 하나에 들어 있으면 나온다
 * @param enabled 그룹 사용 여부. 비우면 전체
 */
public record AdminCommonCodeGroupSearchCondition(
        Integer page,
        Integer size,
        String keyword,
        Boolean enabled
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }
}
