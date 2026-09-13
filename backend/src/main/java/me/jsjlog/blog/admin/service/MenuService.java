package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.domain.Menu;
import me.jsjlog.blog.admin.domain.MenuType;
import me.jsjlog.blog.admin.dto.MenuCreateRequest;
import me.jsjlog.blog.admin.dto.MenuRequest;
import me.jsjlog.blog.admin.dto.MenuResponse;
import me.jsjlog.blog.admin.dto.MenuSearchCondition;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;
import me.jsjlog.blog.admin.repository.MenuRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ConcurrencyGuard;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    public List<MenuSidebarResponse> getMenuListForSidebar() {
        List<MenuSidebarResponse> groupMenuList = menuRepository.getMenuGroupListForSidebar();
        List<MenuSidebarResponse> itemMenuList = menuRepository.getMenuItemListForSidebar();

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
    /**
     * 관리 화면용 메뉴 조회.
     * 사이드바와 달리 숨긴 메뉴도, 항목이 없는 그룹도 모두 내려준다.
     */
    public List<MenuResponse> getMenuList(MenuSearchCondition menuSearchCondition) {

        // 1. 조회조건에 맞는 항목을 먼저 찾는다
        List<MenuResponse> itemMenuList = menuRepository.getMenuItemList(menuSearchCondition);

        // 2. 그 항목들이 속한 그룹 번호를 모은다
        Set<Long> parentIds = itemMenuList.stream()
                .map(MenuResponse::parentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 조건에 맞는 그룹 + 위에서 모은 소속 그룹
        List<MenuResponse> groupMenuList =
                menuRepository.getMenuGroupList(menuSearchCondition, parentIds);

        // 4. 항목을 자기 그룹에 붙인다
        Map<Long, MenuResponse> groupMenuById = groupMenuList.stream()
                .collect(Collectors.toMap(MenuResponse::id, groupMenu -> groupMenu));

        itemMenuList.forEach(itemMenu -> {
            MenuResponse groupMenu = groupMenuById.get(itemMenu.parentId());
            if (groupMenu != null) {
                groupMenu.addItem(itemMenu);
            }
        });

        return groupMenuList;
    }

    /**
     * 메뉴 등록.
     * 그룹은 최상위로만, 항목은 그룹 밑으로만 만든다 (사이드바가 2단 구조라서).
     */
    @Transactional
    public Long createMenu(MenuCreateRequest request) {
        if (!StringUtils.hasText(request.menuName())) {
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        if (request.menuType() == null) {
            // 그룹인지 항목인지는 만들 때만 정할 수 있어서 반드시 받아야 한다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        boolean isGroup = request.menuType() == MenuType.GROUP;
        Menu parent = isGroup ? null : findGroup(request.parentId());
        String routePath = isGroup ? null : request.routePath();

        Menu menu = new Menu(
                parent,
                request.menuName().trim(),
                request.menuDescription(),
                request.menuType(),
                routePath,
                resolveSortOrder(request.sortOrder(), isGroup, request.parentId()),
                Objects.requireNonNullElse(request.visible(), true)
        );

        return menuRepository.save(menu).getId();
    }

    /** 순서를 안 주면 같은 자리의 마지막 다음 번호 */
    private Long resolveSortOrder(Long sortOrder, boolean isGroup, Long parentId) {
        if (sortOrder != null) {
            return sortOrder;
        }

        Long maxSortOrder = isGroup
                ? menuRepository.findMaxGroupSortOrder()
                : menuRepository.findMaxItemSortOrder(parentId);

        return maxSortOrder + 1;
    }

    private Menu findGroup(Long parentId) {
        if (parentId == null) {
            // 항목은 속할 그룹이 있어야 한다. 없으면 사이드바 조립에서 빠진다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        Menu parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new BlogException(ErrorCode.MENU_NOT_FOUND));

        if (!parent.isGroup()) {
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        return parent;
    }

    /**
     * 메뉴 수정.
     * 그룹과 항목은 고칠 수 있는 항목이 다르다.
     * - 그룹: 소속과 경로를 가질 수 없다 (사이드바에서 누르는 대상이 아니다)
     * - 항목: 반드시 그룹에 속해야 한다 (속한 곳이 없으면 사이드바 조립에서 빠진다)
     */
    @Transactional
    public void updateMenu(Long menuId, MenuRequest request) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BlogException(ErrorCode.MENU_NOT_FOUND));

        // 화면이 불러온 뒤 다른 곳에서 바뀌었으면 덮어쓰지 않는다
        ConcurrencyGuard.check(request.updatedAt(), menu.getUpdatedAt());

        if (!StringUtils.hasText(request.menuName())) {
            // menu_name 이 nullable = false 라 그냥 두면 500 이 나간다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        Menu parent = menu.isGroup() ? null : resolveParent(menu, request.parentId());
        String routePath = menu.isGroup() ? null : request.routePath();

        menu.update(
                parent,
                request.menuName().trim(),
                request.menuDescription(),
                routePath,
                Objects.requireNonNullElse(request.sortOrder(), menu.getSortOrder()),
                Objects.requireNonNullElse(request.visible(), menu.getVisible())
        );
    }

    /**
     * 메뉴 삭제.
     * 항목이 남아 있는 그룹은 지울 수 없다 — 그 항목들이 갈 곳을 잃는다.
     */
    @Transactional
    public void deleteMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BlogException(ErrorCode.MENU_NOT_FOUND));

        if (menu.isGroup() && menuRepository.existsByParentMenuId(menuId)) {
            throw new BlogException(ErrorCode.MENU_HAS_ITEMS);
        }

        menuRepository.delete(menu);
    }

    private Menu resolveParent(Menu menu, Long parentId) {
        if (parentId == null) {
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        if (Objects.equals(parentId, menu.getId())) {
            // 자기 자신을 소속으로 두면 사이드바 조립이 끝나지 않는다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        Menu parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new BlogException(ErrorCode.MENU_NOT_FOUND));

        if (!parent.isGroup()) {
            // 항목 밑에 항목을 넣으면 2단 구조가 깨진다
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        return parent;
    }

}
