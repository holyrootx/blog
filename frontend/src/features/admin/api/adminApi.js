import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';

const ADMIN_BLOG_API_BASE = '/api/v1/admin/blog';

export async function getAdminDashboard() {
  const dashboard = await getApiData(`${ADMIN_BLOG_API_BASE}/dashboard`);
  return toAdminDashboard(dashboard);
}

export async function getAdminSidebarMenus() {
  const menus = await getApiData(`${ADMIN_BLOG_API_BASE}/sidebar/menus`);

  return toAdminMenus(menus);
}

// 메뉴 관리 화면용. 숨긴 메뉴까지 전부 내려온다
export async function getAdminMenus(condition = {}) {
  const menus = await getApiData(withQuery(`${ADMIN_BLOG_API_BASE}/menus`, condition));

  return toAdminMenus(menus);
}

// 빈 값은 보내지 않는다. 서버에서 빈 문자열도 조건으로 잡힐 수 있기 때문
function withQuery(path, condition) {
  const searchParams = new URLSearchParams();

  Object.entries(condition).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const query = searchParams.toString();

  return query ? `${path}?${query}` : path;
}

// 메뉴 수정. menuType 은 바꿀 수 없어서 보내지 않는다
export function updateAdminMenu(menuId, request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/menus/${menuId}`, {
    method: 'PUT',
    body: request,
  });
}

// 메뉴 등록. 수정과 달리 menuType 을 보낸다 (만들 때만 정할 수 있다)
export function createAdminMenu(request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/menus`, {
    method: 'POST',
    body: request,
  });
}

export function deleteAdminMenu(menuId) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/menus/${menuId}`, {
    method: 'DELETE',
  });
}

// 카테고리 관리 화면용. 공개 목록과 달리 글 수가 함께 내려온다
export async function getAdminCategories(condition = {}) {
  const categories = await getApiData(withQuery(`${ADMIN_BLOG_API_BASE}/categories`, condition));

  return Array.isArray(categories) ? categories.map(toAdminCategory) : [];
}

export function createAdminCategory(request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/categories`, {
    method: 'POST',
    body: request,
  });
}

export function updateAdminCategory(categoryId, request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/categories/${categoryId}`, {
    method: 'PUT',
    body: request,
  });
}

export function deleteAdminCategory(categoryId) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/categories/${categoryId}`, {
    method: 'DELETE',
  });
}

function toAdminCategory(category) {
  return {
    id: category.id,
    name: category.name ?? '이름 없는 카테고리',
    sortOrder: toNumber(category.sortOrder),
    // 전용 API 에서 내려줄 값. 없으면 화면에 "연결 전"으로 표시한다
    postCount: toNumberOrNull(category.postCount),
  };
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
