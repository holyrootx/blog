import { ref } from 'vue';

import { getAdminSession, loginAdmin, logoutAdmin, refreshCsrfToken } from '../api/adminAuthApi';
import { clearMemberSession, onMemberSessionCleared, refreshMemberSession } from '../../member/data/memberAuthStore';
import { advanceAuthenticationGeneration } from '../../../shared/api/blogApiClient';

/**
 * 로그인한 관리자 상태.
 *
 * 모듈 수준에 두어 라우터 가드와 사이드바가 같은 값을 본다.
 * 화면을 옮길 때마다 /me 를 부르지 않는다 — 한 번 확인하면 그 결과를 들고 다니고,
 * 새로고침하거나 서버가 401 을 줄 때만 다시 확인한다.
 *
 * 이건 편의용 상태지 보안 경계가 아니다. 진짜 판단은 서버가 한다.
 */
const admin = ref(null);

// 진행 중이거나 이미 끝난 세션 확인. 여러 화면이 동시에 들어와도 요청은 한 번만 나간다
let sessionCheck = null;
let sessionGeneration = 0;
onMemberSessionCleared(clearAdminSession);

export function useAdminAuth() {
  return { admin };
}

/** @returns 로그인 상태면 true */
export function ensureAdminSession() {
  if (!sessionCheck) {
    sessionCheck = checkSession();
  }

  return sessionCheck;
}

function checkSession() {
  const generation = sessionGeneration;
  // 세션 확인과 함께 토큰도 받아 둔다. 새로고침하면 화면이 들고 있던 토큰이 사라지는데,
  // 그대로 두면 새로고침 직후 첫 저장이 403 이 된다
  return refreshCsrfToken()
    .catch(() => null)
    .then(() => getAdminSession())
    .then((session) => {
      if (generation !== sessionGeneration) return false;
      admin.value = session;
      return true;
    })
    .catch(() => {
      if (generation !== sessionGeneration) return false;
      // 401 이면 로그인 안 된 것이고, 서버가 죽었어도 화면에서 할 수 있는 건 로그인뿐이다
      admin.value = null;
      return false;
    });
}

export async function signInAdmin(username, password) {
  const generation = ++sessionGeneration;
  advanceAuthenticationGeneration();
  // 로그인 요청 자체도 CSRF 검사를 받는다
  await refreshCsrfToken();
  if (generation !== sessionGeneration) throw new Error('로그인 상태가 변경되었습니다. 다시 로그인해 주세요.');

  const session = await loginAdmin(username, password);
  if (generation !== sessionGeneration) throw new Error('로그인 상태가 변경되었습니다. 다시 로그인해 주세요.');

  // 로그인 대기 중 시작한 요청도 새 인증의 만료 응답으로 처리하지 않는다.
  advanceAuthenticationGeneration();
  // 로그인에 성공하면 서버가 토큰을 새로 발급한다. 옛 토큰으로는 아무것도 저장하지 못한다
  await refreshCsrfToken();

  if (generation !== sessionGeneration) throw new Error('로그인 상태가 변경되었습니다. 다시 로그인해 주세요.');
  admin.value = session;
  sessionCheck = Promise.resolve(true);

  // 관리자도 회원이다. 로그인하면 회원 세션도 같이 생기는데, 회원 쪽 스토어는 그걸
  // 모르고 페이지가 처음 뜰 때 받아 둔 "비로그인" 을 들고 있다. 그대로 두면 관리자
  // 화면의 알림 종이 새로고침하기 전까지 나타나지 않는다
  await refreshMemberSession().catch(() => null);

  return session;
}

export async function signOutAdmin() {
  const generation = sessionGeneration;
  try {
    await logoutAdmin();
  } catch (error) {
    if (generation !== sessionGeneration) throw error;
    try {
      const session = await getAdminSession();
      if (generation === sessionGeneration) admin.value = session;
    } catch (checkError) {
      // 재조회에서 명확한 401을 받은 경우에만 종료로 확인한다.
      if (checkError?.status !== 401) throw error;
      if (generation !== sessionGeneration) {
        if (admin.value === null) return;
        throw error;
      }
      clearAdminSession();
      clearMemberSession();
      await refreshCsrfToken().catch(() => null);
      return;
    }
    throw error;
  }
  if (generation !== sessionGeneration) return;
  clearAdminSession();
  clearMemberSession();
  await refreshCsrfToken().catch(() => null);
}

/** 세션이 끊겼다고 알려졌을 때. 다음 진입에서 서버에 다시 물어본다 */
export function clearAdminSession() {
  sessionGeneration += 1;
  advanceAuthenticationGeneration();
  admin.value = null;
  sessionCheck = null;
}
