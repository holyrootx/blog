package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.dto.MenuResponse;
import me.jsjlog.blog.admin.dto.MenuSearchCondition;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;

import java.util.Collection;
import java.util.List;

public interface MenuRepositoryCustom {

    List<MenuSidebarResponse> getMenuGroupListForSidebar();
    List<MenuSidebarResponse> getMenuItemListForSidebar();

    /**
     * 관리 화면용 그룹 조회.
     *
     * @param parentIds 조회조건에 걸린 항목들의 소속 그룹 번호.
     *                  그룹 자체가 조건에 안 맞아도 이 번호에 들면 함께 내려준다.
     */
    List<MenuResponse> getMenuGroupList(MenuSearchCondition menuSearchCondition, Collection<Long> parentIds);
    List<MenuResponse> getMenuItemList(MenuSearchCondition menuSearchCondition);

}
