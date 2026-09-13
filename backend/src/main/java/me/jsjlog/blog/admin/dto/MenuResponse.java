package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.admin.domain.MenuType;

import java.time.LocalDateTime;
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
        // 수정 충돌 감지용. 화면이 불러온 값을 저장 때 그대로 돌려보낸다
        LocalDateTime updatedAt,
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
            Boolean visible,
            LocalDateTime updatedAt
    ) {
        this(parentId, id, menuName, menuDescription, menuType, routePath, sortOrder, visible, updatedAt,
                new ArrayList<MenuResponse>());
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
                updatedAt,
                items
        );
    }

    public void addItem(MenuResponse itemMenu){
        this.items.add(itemMenu);
    }


}
