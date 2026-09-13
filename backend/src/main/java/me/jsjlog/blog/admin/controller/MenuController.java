package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.MenuCreateRequest;
import me.jsjlog.blog.admin.dto.MenuRequest;
import me.jsjlog.blog.admin.dto.MenuResponse;
import me.jsjlog.blog.admin.dto.MenuSearchCondition;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;
import me.jsjlog.blog.admin.service.MenuService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/blog")
@Slf4j
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/sidebar/menus")
    public ApiResponse<List<MenuSidebarResponse>> getMenusForSidebar(){
        List<MenuSidebarResponse> menus = menuService.getMenuListForSidebar();
        return ApiResponse.ok(menus);
    }

    // /api/v1/admin/blog/menus
    @GetMapping("/menus")
    public ApiResponse<List<MenuResponse>> getMenus(MenuSearchCondition menuSearchCondition) {
        List<MenuResponse> menuList = menuService.getMenuList(menuSearchCondition);
        return ApiResponse.ok(menuList);
    }

    // POST /api/v1/admin/blog/menus
    @PostMapping("/menus")
    public ApiResponse<Long> createMenu(@RequestBody MenuCreateRequest request) {
        Long menuId = menuService.createMenu(request);
        return ApiResponse.ok(menuId);
    }

    // DELETE /api/v1/admin/blog/menus/{menuId}
    @DeleteMapping("/menus/{menuId}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long menuId) {
        menuService.deleteMenu(menuId);
        return ApiResponse.ok();
    }

    // PUT /api/v1/admin/blog/menus/{menuId}
    @PutMapping("/menus/{menuId}")
    public ApiResponse<Void> updateMenu(@PathVariable Long menuId,
                                        @RequestBody MenuRequest request) {
        menuService.updateMenu(menuId, request);
        return ApiResponse.ok();
    }
}
