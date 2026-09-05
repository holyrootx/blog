package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.dto.MenuSidebarResponse;

import java.util.List;

public interface MenuRepositoryCustom {

    List<MenuSidebarResponse> getMenuGroupListForSidebar();
    List<MenuSidebarResponse> getMenuItemListForSidebar();
}
