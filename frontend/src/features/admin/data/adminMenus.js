export function getVisibleAdminMenus(menus = []) {
  return sortAdminMenus(menus)
    .filter((menu) => menu.visible)
    .map((menu) => ({
      ...menu,
      items: getVisibleAdminMenus(menu.items ?? []),
    }))
    .filter((menu) => menu.menuType !== 'GROUP' || hasVisibleChildren(menu, menus));
}

export function sortAdminMenus(menus = []) {
  return [...menus]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((menu) => ({
      ...menu,
      items: Array.isArray(menu.items) ? sortAdminMenus(menu.items) : [],
    }));
}

export function flattenAdminMenus(menus = []) {
  return sortAdminMenus(menus).flatMap((menu) => [
    menu,
    ...flattenAdminMenus(menu.items ?? []),
  ]);
}

function hasVisibleChildren(parentMenu, menus) {
  if (Array.isArray(parentMenu.items) && parentMenu.items.length > 0) {
    return true;
  }

  return menus.some((menu) => menu.parentId === parentMenu.id && menu.visible);
}
