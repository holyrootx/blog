package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.admin.domain.MenuType;
import java.util.ArrayList;
import java.util.List;

public record MenuSidebarResponse(
        Long parentId,
        Long id,
        String menuName,
        String menuDescription,
        MenuType menuType,
        String routePath,
        Long sortOrder,
        Boolean visible,
        List<MenuSidebarResponse> items
) {
    public MenuSidebarResponse(
            Long parentId,
            Long id,
            String menuName,
            String menuDescription,
            MenuType menuType,
            String routePath,
            Long sortOrder,
            Boolean visible
    ) {
        this(parentId, id, menuName, menuDescription, menuType, routePath, sortOrder, visible, new ArrayList<MenuSidebarResponse>());
    }

    public MenuSidebarResponse withItems(List<MenuSidebarResponse> items) {
        return new MenuSidebarResponse(
                parentId,
                id,
                menuName,
                menuDescription,
                menuType,
                routePath,
                sortOrder,
                visible,
                items
        );
    }

    public void addItem(MenuSidebarResponse itemMenu){
        this.items.add(itemMenu);
    }
}
