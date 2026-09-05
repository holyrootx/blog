package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;
import me.jsjlog.blog.admin.service.MenuService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
