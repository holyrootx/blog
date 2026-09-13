package me.jsjlog.blog.admin.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.util.StringUtils;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.domain.MenuType;
import me.jsjlog.blog.admin.domain.QMenu;
import me.jsjlog.blog.admin.dto.MenuResponse;
import me.jsjlog.blog.admin.dto.MenuSearchCondition;
import me.jsjlog.blog.admin.dto.MenuSidebarResponse;

import java.util.ArrayList;
import java.util.Collection;
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

    @Override
    public List<MenuResponse> getMenuGroupList(MenuSearchCondition menuSearchCondition,
                                               Collection<Long> parentIds){

        QMenu menu = QMenu.menu;

        // 그룹 자체가 조회조건에 맞는지
        BooleanBuilder matchedBuilder = new BooleanBuilder();

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.menuName())) {
            matchedBuilder.and(menu.menuName.contains(menuSearchCondition.menuName()));
        }

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.menuDescription())) {
            matchedBuilder.and(menu.menuDescription.contains(menuSearchCondition.menuDescription()));
        }

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.routePath())) {
            matchedBuilder.and(menu.routePath.contains(menuSearchCondition.routePath()));
        }

        BooleanBuilder groupBuilder = new BooleanBuilder();
        groupBuilder.and(menu.menuType.eq(MenuType.GROUP));

        // 조건이 있을 때만 좁힌다.
        // 조건에 맞는 그룹이거나, 조건에 맞는 항목의 소속 그룹.
        // 그룹에만 조건을 걸면 항목이 붙을 자리가 사라져서 결과가 비어버린다
        if (!menuSearchCondition.isEmpty()) {
            BooleanBuilder scopeBuilder = new BooleanBuilder(matchedBuilder.getValue());

            // 빈 컬렉션으로 in () 을 만들지 않는다
            if (parentIds != null && !parentIds.isEmpty()) {
                scopeBuilder.or(menu.id.in(parentIds));
            }

            groupBuilder.and(scopeBuilder);
        }

        ConstructorExpression<MenuResponse> menuConstructionExp = Projections.constructor(
                MenuResponse.class,
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
    public List<MenuResponse> getMenuItemList(MenuSearchCondition menuSearchCondition){

        QMenu menu = QMenu.menu;

        BooleanBuilder itemBuilder = new BooleanBuilder();
        itemBuilder.and(menu.menuType.eq(MenuType.ITEM));
        itemBuilder.and(menu.parent.id.isNotNull());

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.menuName())) {
            itemBuilder.and(menu.menuName.contains(menuSearchCondition.menuName()));
        }

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.menuDescription())) {
            itemBuilder.and(menu.menuDescription.contains(menuSearchCondition.menuDescription()));
        }

        if (!StringUtils.isNullOrEmpty(menuSearchCondition.routePath())) {
            itemBuilder.and(menu.routePath.contains(menuSearchCondition.routePath()));
        }

        ConstructorExpression<MenuResponse> menuConstructionExp = Projections.constructor(
                MenuResponse.class,
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
