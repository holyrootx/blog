package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.admin.domain.MenuType;

import java.util.ArrayList;
import java.util.List;

public record MenuResponse(
        Long parentId,
        Long id,
        String menuName,
        String menuDescription,
        MenuType menuType,
        String routePath,
        Long sortOrder,
        Boolean visible,
        List<MenuResponse> items
) {
    public MenuResponse(
            Long parentId,
            Long id,
            String menuName,
            String menuDescription,
            MenuType menuType,
            String routePath,
            Long sortOrder,
            Boolean visible
    ) {
        this(parentId, id, menuName, menuDescription, menuType, routePath, sortOrder, visible, new ArrayList<MenuResponse>());
    }

    public MenuResponse withItems(List<MenuResponse> items) {
        return new MenuResponse(
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

    public void addItem(MenuResponse itemMenu){
        this.items.add(itemMenu);
    }


}
