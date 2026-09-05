import { getApiData } from '../../../shared/api/blogApiClient';

const ADMIN_BLOG_API_BASE = '/api/v1/admin/blog';

export async function getAdminDashboard() {
  const dashboard = await getApiData(`${ADMIN_BLOG_API_BASE}/dashboard`);
  return toAdminDashboard(dashboard);
}

export async function getAdminSidebarMenus() {
  const menus = await getApiData(`${ADMIN_BLOG_API_BASE}/sidebar/menus`);

  return toAdminMenus(menus);
}

function toAdminDashboard(dashboard) {
  return {
    daysSinceStart: toNumberOrNull(dashboard?.daysSinceStart),
    draftPostCount: toNumberOrNull(dashboard?.draftPostCount),
    totalViews: toNumberOrNull(dashboard?.totalViews),
    publishedPostCount: toNumberOrNull(dashboard?.publishedPostCount),
    postCountThisMonth: toNumberOrNull(dashboard?.postCountThisMonth),
    unansweredCommentCount: toNumberOrNull(dashboard?.unansweredCommentCount),
    oldestUnansweredAt: dashboard?.oldestUnansweredAt ?? null,
    mostViewedCategory: dashboard?.mostViewedCategory ?? null,
    categoryShares: Array.isArray(dashboard?.categoryShares)
      ? dashboard.categoryShares.map(toCategoryShare)
      : [],
    unansweredComments: Array.isArray(dashboard?.unansweredComments)
      ? dashboard.unansweredComments.map(toUnansweredComment)
      : [],
  };
}

function toAdminMenus(menus) {
  const source = Array.isArray(menus) ? menus : menus?.items;

  if (!Array.isArray(source)) {
    return [];
  }

  return source.map(toAdminMenu);
}

function toAdminMenu(menu) {
  const routePath = menu.routePath ?? menu.path ?? '';

  return {
    id: menu.id,
    parentId: menu.parentId ?? null,
    menuName: menu.menuName ?? menu.label ?? menu.name ?? '이름 없는 메뉴',
    menuDescription: menu.menuDescription ?? menu.description ?? '',
    menuType: menu.menuType ?? 'ITEM',
    routePath,
    routeName: menu.routeName ?? '',
    sortOrder: toNumber(menu.sortOrder),
    visible: toVisible(menu),
    system: Boolean(menu.system),
    items: Array.isArray(menu.items) ? menu.items.map(toAdminMenu) : [],
  };
}

function toCategoryShare(categoryShare) {
  return {
    categoryId: categoryShare.categoryId,
    name: categoryShare.name ?? '미분류',
    postCount: toNumber(categoryShare.postCount),
  };
}

function toUnansweredComment(comment) {
  return {
    id: comment.id,
    postId: comment.postId,
    postTitle: comment.postTitle ?? '제목 없는 글',
    guestNickname: comment.guestNickname ?? comment.nickname ?? '방문자',
    content: comment.content ?? '',
    createdAt: comment.createdAt ?? null,
  };
}

function toNumber(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
}

function toNumberOrNull(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toVisible(menu) {
  if (typeof menu.visible === 'boolean') {
    return menu.visible;
  }

  return true;
}
