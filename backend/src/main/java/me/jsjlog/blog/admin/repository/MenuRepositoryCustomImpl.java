package me.jsjlog.blog.admin.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.domain.MenuType;
import me.jsjlog.blog.admin.domain.QMenu;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class MenuRepositoryCustomImpl implements MenuRepositoryCustom{

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<MenuSidebarResponse> getMenuGroupListForSidebar() {

        QMenu menu = QMenu.menu;

        BooleanBuilder groupBuilder = new BooleanBuilder();
        groupBuilder.and(menu.visible.eq(true));
        groupBuilder.and(menu.menuType.eq(MenuType.GROUP));


        ConstructorExpression<MenuSidebarResponse> menuConstructionExp = Projections.constructor(
                MenuSidebarResponse.class,
                menu.parent.id,
                menu.id,
                menu.menuName,
                menu.menuDescription,
                menu.menuType,
                menu.routePath,
                menu.sortOrder,
                menu.visible
        );

        return jpaQueryFactory.select(menuConstructionExp)
                .from(menu)
                .where(groupBuilder)
                .orderBy(menu.sortOrder.asc(), menu.id.asc())
                .fetch();
    }

    @Override
    public List<MenuSidebarResponse> getMenuItemListForSidebar() {

        QMenu menu = QMenu.menu;

        BooleanBuilder itemBuilder = new BooleanBuilder();
        itemBuilder.and(menu.visible.eq(true));
        itemBuilder.and(menu.menuType.eq(MenuType.ITEM));
        itemBuilder.and(menu.parent.id.isNotNull());

        ConstructorExpression<MenuSidebarResponse> menuConstructionExp = Projections.constructor(
                MenuSidebarResponse.class,
                menu.parent.id,
                menu.id,
                menu.menuName,
                menu.menuDescription,
                menu.menuType,
                menu.routePath,
                menu.sortOrder,
                menu.visible
        );

        return jpaQueryFactory.select(menuConstructionExp)
                .from(menu)
                .where(itemBuilder)
                .orderBy(menu.sortOrder.asc(), menu.id.asc())
                .fetch();
    }
}
