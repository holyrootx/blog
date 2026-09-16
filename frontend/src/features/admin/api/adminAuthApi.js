import { getApiData, sendApiData, setCsrfToken } from '../../../shared/api/blogApiClient';

const ADMIN_AUTH_API_BASE = '/api/v1/admin/auth';

/**
 * CSRF 토큰을 받아 클라이언트에 심는다.
 *
 * 로그인 전에 한 번, 로그인·로그아웃 직후에 다시 불러야 한다.
 * 서버가 그때마다 토큰을 새로 발급하기 때문이다 — 옛 토큰을 계속 쓰면 첫 저장이 403 이 된다.
 */
export async function refreshCsrfToken() {
  const token = await getApiData(`${ADMIN_AUTH_API_BASE}/csrf`);

  setCsrfToken(token);

  return token;
}

/**
 * 로그인.
 *
 * 세션 쿠키는 브라우저가 알아서 보관하고 다음 요청에 실어 보낸다.
 * fetch 가 상대경로라 같은 출처이고, 기본값으로 쿠키가 실린다 — credentials 를 따로 줄 필요가 없다.
 */
export async function loginAdmin(username, password) {
  const session = await sendApiData(`${ADMIN_AUTH_API_BASE}/login`, {
    method: 'POST',
    body: { username, password },
  });

  return toAdminSession(session);
}

/** 세션이 아직 살아 있는지. 없으면 서버가 401 을 주고 ApiError 가 던져진다 */
export async function getAdminSession() {
  return toAdminSession(await getApiData(`${ADMIN_AUTH_API_BASE}/me`));
}

export function logoutAdmin() {
  return sendApiData(`${ADMIN_AUTH_API_BASE}/logout`, { method: 'POST' });
}

function toAdminSession(session) {
  return {
    username: session?.username ?? '',
    role: session?.role ?? '',
  };
}
