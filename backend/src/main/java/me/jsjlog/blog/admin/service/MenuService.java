package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.domain.MenuType;
import me.jsjlog.blog.admin.dto.MenuResponse;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;
import me.jsjlog.blog.admin.repository.MenuRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    public List<MenuSidebarResponse> getMenuListForSidebar() {
        List<MenuSidebarResponse> groupMenuList = menuRepository.getMenuGroupListForSidebar();
        List<MenuSidebarResponse> itemMenuList = menuRepository.getMenuItemListForSidebar();

        if (groupMenuList == null || groupMenuList.isEmpty()) {
            throw new BlogException(ErrorCode.MENU_NOT_FOUND);
        }

        if (itemMenuList == null || itemMenuList.isEmpty()) {
            throw new BlogException(ErrorCode.MENU_NOT_FOUND);
        }

        groupMenuList.forEach(groupMenu -> {
            itemMenuList.forEach(itemMenu -> {
                if (Objects.equals(groupMenu.id(), itemMenu.parentId()) && groupMenu.visible() == true) {
                    groupMenu.addItem(itemMenu);
                }
            });
        });

        List<MenuSidebarResponse> menus = new ArrayList<>();
        for (MenuSidebarResponse groupMenu : groupMenuList) {
            if(groupMenu.items().isEmpty()) {
                continue;
            } else {
                menus.add(groupMenu);
            }
        }

        return menus;
    }

}
