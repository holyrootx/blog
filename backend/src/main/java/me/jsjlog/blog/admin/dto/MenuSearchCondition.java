package me.jsjlog.blog.admin.dto;

import org.springframework.util.StringUtils;

public record MenuSearchCondition(
        String menuName,
        String menuDescription,
        String routePath
) {

    /**
     * 조회조건이 하나도 없는지.
     * 조건이 없을 때는 그룹을 좁히지 않고 전부 내려줘야 하므로 구분이 필요하다.
     */
    public boolean isEmpty() {
        return !StringUtils.hasText(menuName)
                && !StringUtils.hasText(menuDescription)
                && !StringUtils.hasText(routePath);
    }

}
