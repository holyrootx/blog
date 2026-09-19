import { getApiData, sendApiData, sendApiFile } from '../../../shared/api/blogApiClient';

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

// 글 관리 화면용. 목록 + 상태별 건수 + 페이징 정보가 함께 온다
export async function getAdminPosts(condition = {}) {
  const result = await getApiData(withQuery(`${ADMIN_BLOG_API_BASE}/posts`, condition));

  return {
    items: Array.isArray(result?.items) ? result.items.map(toAdminPost) : [],
    statusCounts: {
      all: toNumber(result?.statusCounts?.all),
      published: toNumber(result?.statusCounts?.published),
      scheduled: toNumber(result?.statusCounts?.scheduled),
      private: toNumber(result?.statusCounts?.private),
      draft: toNumber(result?.statusCounts?.draft),
    },
    page: toNumber(result?.page),
    size: toNumber(result?.size),
    totalElements: toNumber(result?.totalElements),
    totalPages: toNumber(result?.totalPages),
  };
}

function toAdminPost(post) {
  return {
    id: post.id,
    title: post.title ?? '제목 없는 글',
    categoryId: post.categoryId ?? null,
    categoryName: post.categoryName ?? '미분류',
    status: post.status ?? 'DRAFT',
    publishedAt: post.publishedAt ?? null,
    createdAt: post.createdAt ?? null,
    views: toNumber(post.views),
  };
}

export async function getAdminPost(postId) {
  const post = await getApiData(`${ADMIN_BLOG_API_BASE}/posts/${postId}`);

  return {
    id: post?.id ?? null,
    title: post?.title ?? '',
    categoryId: post?.categoryId ?? null,
    categoryName: post?.categoryName ?? '',
    // null 을 그대로 두면 textarea 에 "null" 이 찍힌다
    excerpt: post?.excerpt ?? '',
    content: post?.content ?? '',
    thumbnailImageUrl: post?.thumbnailImageUrl ?? '',
    status: post?.status ?? 'DRAFT',
    publishedAt: post?.publishedAt ?? null,
    // 로컬 스냅샷이 어느 서버 값을 기준으로 만들어졌는지 비교하는 데 쓴다.
    // 여기서 빠뜨리면 충돌 감지가 조용히 죽는다
    updatedAt: post?.updatedAt ?? null,
  };
}

export function createAdminPost(request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/posts`, { method: 'POST', body: request });
}

export function updateAdminPost(postId, request) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/posts/${postId}`, { method: 'PUT', body: request });
}

// 발행·내리기는 수정(PUT)과 나눠져 있다.
// 발행이 publishedAt 을 건드리는 부수효과를 갖기 때문이다
/**
 * 글을 발행한다.
 *
 * publishedAt 을 주면 그 시각에 공개된다. 미래 시각이면 그때까지 공개 목록에 나오지 않는다.
 * 비우면 지금 발행이다.
 */
export function publishAdminPost(postId, publishedAt = null) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/posts/${postId}/publish`, {
    method: 'POST',
    body: { publishedAt },
  });
}

export function unpublishAdminPost(postId) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/posts/${postId}/unpublish`, { method: 'POST' });
}

export function deleteAdminPost(postId) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/posts/${postId}`, { method: 'DELETE' });
}

export async function getAdminComments(condition = {}) {
  const result = await getApiData(withQuery(`${ADMIN_BLOG_API_BASE}/comments`, condition));

  return {
    items: Array.isArray(result?.items) ? result.items.map(toAdminComment) : [],
    statusCounts: {
      all: toNumber(result?.statusCounts?.all),
      unanswered: toNumber(result?.statusCounts?.unanswered),
      hidden: toNumber(result?.statusCounts?.hidden),
    },
    page: toNumber(result?.page),
    size: toNumber(result?.size),
    totalElements: toNumber(result?.totalElements),
    totalPages: toNumber(result?.totalPages),
  };
}

export function replyAdminComment(commentId, content) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/comments/${commentId}/replies`, {
    method: 'POST',
    body: { content },
  });
}

export function updateAdminCommentVisibility(commentId, hidden) {
  return sendApiData(`${ADMIN_BLOG_API_BASE}/comments/${commentId}/visibility`, {
    method: 'PUT',
    body: { hidden },
  });
}

function toAdminComment(comment) {
  return {
    id: comment.id,
    postId: comment.postId,
    postTitle: comment.postTitle ?? '제목 없는 글',
    parentId: comment.parentId ?? null,
    nickname: comment.nickname ?? '알 수 없는 회원',
    memberRole: comment.memberRole ?? 'USER',
    content: comment.content ?? '',
    createdAt: comment.createdAt ?? null,
    hidden: Boolean(comment.hidden),
    answered: Boolean(comment.answered),
  };
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
    updatedAt: category.updatedAt ?? null,
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
    // 수정 충돌 감지용. 저장 때 그대로 돌려보낸다
    updatedAt: menu.updatedAt ?? null,
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
    nickname: comment.nickname ?? '알 수 없는 회원',
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

/**
 * 본문에 넣을 이미지를 올린다.
 *
 * 응답의 url 을 그대로 마크다운에 박는다. 파일은 R2 에 있고 DB 에는 기록만 남는다.
 */
export async function uploadAdminImage(file) {
  const form = new FormData();
  form.append('file', file);

  const image = await sendApiFile(`${ADMIN_BLOG_API_BASE}/images`, form);

  return {
    id: image?.id ?? null,
    url: image?.url ?? '',
    originalName: image?.originalName ?? '',
  };
}
