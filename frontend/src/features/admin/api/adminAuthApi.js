import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';

const ADMIN_AUTH_API_BASE = '/api/v1/admin/auth';

// 회원 가입 화면도 같은 토큰이 필요해져서 shared 로 옮겼다.
// 관리자 코드가 부르던 이름은 그대로 두려고 여기서 다시 내보낸다
export { refreshCsrfToken } from '../../../shared/api/csrfApi';

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
