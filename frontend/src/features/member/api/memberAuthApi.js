import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';

const MEMBER_AUTH_API_BASE = '/api/v1/auth';
const OAUTH_API_BASE = `${MEMBER_AUTH_API_BASE}/oauth`;

/**
 * 소셜 로그인을 시작하는 주소.
 *
 * 이 경로는 fetch 로 부르지 않는다. 제공자 동의 화면으로 넘어갔다가 돌아와야 하므로
 * 브라우저가 통째로 이동해야 한다. XHR 로 부르면 리다이렉트를 따라가다 막힌다.
 */
export function socialLoginUrl(provider) {
  return `/oauth2/authorization/${provider}`;
}

/**
 * 가입·재가입 화면에 필요한 값.
 *
 * 제공자 식별자는 내려오지 않는다 — 서버 세션에만 있다. 화면이 들고 있다가 되돌려 주는
 * 구조였다면 값을 바꿔 남의 계정으로 가입할 수 있다.
 */
export async function getOAuthPending() {
  const pending = await getApiData(`${OAUTH_API_BASE}/pending`);

  return {
    loginState: pending?.loginState ?? '',
    suggestedNickname: pending?.suggestedNickname ?? '',
  };
}

/**
 * 지금 로그인한 회원. 로그인하지 않았으면 {@code null} 이다.
 *
 * 로그인하지 않은 상태가 오류가 아니라서 서버도 401 이 아니라 빈 데이터로 답한다 —
 * 공개 화면은 로그인 안 한 사람이 보는 것이 정상이다.
 */
export async function getMemberSession() {
  const session = await getApiData(`${MEMBER_AUTH_API_BASE}/me`);

  return session === null ? null : toMemberSession(session);
}

export function logoutMember() {
  return sendApiData(`${MEMBER_AUTH_API_BASE}/logout`, { method: 'POST' });
}

/** 신규 가입. 성공하면 그 자리에서 로그인까지 끝난다 */
export async function signUpMember(nickname) {
  return toMemberSession(await sendApiData(`${OAUTH_API_BASE}/signup`, {
    method: 'POST',
    body: { nickname },
  }));
}

/** 탈퇴했던 계정을 되살린다. 닉네임과 예전 댓글이 그대로 돌아온다 */
export async function reactivateMember() {
  return toMemberSession(await sendApiData(`${OAUTH_API_BASE}/reactivate`, { method: 'POST' }));
}

/** 예전 계정을 두고 새 계정으로 시작한다. 예전 댓글은 남지만 내 것이 아니게 된다 */
export async function rejoinMember(nickname) {
  return toMemberSession(await sendApiData(`${OAUTH_API_BASE}/rejoin`, {
    method: 'POST',
    body: { nickname },
  }));
}

function toMemberSession(session) {
  return {
    id: session?.id ?? null,
    nickname: session?.nickname ?? '',
    // 제공자에서 프사 동의를 안 받았으면 비어 있다. 화면이 대체 표시를 고를 수 있게 빈 문자열로 맞춘다
    profileImageUrl: session?.profileImageUrl ?? '',
    role: session?.role ?? '',
  };
}
