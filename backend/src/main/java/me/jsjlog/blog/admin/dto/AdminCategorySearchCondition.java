package me.jsjlog.blog.admin.dto;

import org.springframework.util.StringUtils;

public record AdminCategorySearchCondition(
        String name
) {

    public boolean isEmpty() {
        return !StringUtils.hasText(name);
    }

}
