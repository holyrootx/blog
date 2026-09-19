import { computed, ref } from 'vue';

import {
  getMemberSession,
  logoutMember,
  reactivateMember,
  rejoinMember,
  signUpMember,
} from '../api/memberAuthApi';
import { refreshCsrfToken } from '../../../shared/api/csrfApi';

/**
 * 로그인한 회원 상태.
 *
 * 관리자 상태({@code adminAuthStore})와 따로 둔다. 로그인 방법도 다르고, 관리자 화면은
 * 라우터 가드가 막지만 공개 화면은 로그인하지 않은 사람도 그대로 본다.
 *
 * 이건 화면 표시용이지 보안 경계가 아니다. 실제 판단은 서버가 한다.
 */
const member = ref(null);

// 진행 중이거나 이미 끝난 세션 확인. 여러 화면이 동시에 들어와도 요청은 한 번만 나간다
let sessionCheck = null;

export function useMemberAuth() {
  return {
    member,
    isSignedIn: computed(() => member.value !== null),
  };
}

/**
 * 새로고침 뒤 서버에 누구인지 물어본다.
 *
 * 로그인하지 않은 경우가 정상이라 실패로 다루지 않는다 — 서버도 401 대신 빈 데이터를 준다.
 */
export function ensureMemberSession() {
  if (!sessionCheck) {
    sessionCheck = getMemberSession()
      .then(async (session) => {
        member.value = session;

        // 새로고침 뒤에도 쓰기 요청에는 CSRF 토큰이 필요하다. 로그인 세션만 복원하고
        // 토큰을 비워 두면 첫 댓글 등록이 403으로 실패한다.
        if (session) {
          await refreshCsrfToken().catch(() => null);
        }

        return session;
      })
      .catch(() => {
        // 서버가 죽었어도 공개 화면은 읽을 수 있어야 한다. 로그인 안 한 것으로 본다
        member.value = null;
        return null;
      });
  }

  return sessionCheck;
}

export async function completeSignUp(nickname) {
  return applySession(await signUpMember(nickname));
}

export async function completeReactivation() {
  return applySession(await reactivateMember());
}

export async function completeRejoin(nickname) {
  return applySession(await rejoinMember(nickname));
}

export async function signOutMember() {
  try {
    await logoutMember();
  } finally {
    // 서버 응답이 실패해도 화면에서는 로그아웃으로 친다.
    // 눌렀는데 로그인 상태로 남아 있는 것이 더 나쁘다
    clearMemberSession();

    // 로그아웃하면 세션이 바뀌어 토큰도 새로 받아야 한다
    await refreshCsrfToken().catch(() => null);
  }
}

export function clearMemberSession() {
  member.value = null;
  sessionCheck = null;
}

async function applySession(session) {
  member.value = session;
  sessionCheck = Promise.resolve(session);

  // 로그인에 성공하면 서버가 CSRF 토큰을 새로 발급한다.
  // 옛 토큰을 들고 있으면 로그인 직후 첫 댓글이 403 으로 막힌다
  await refreshCsrfToken().catch(() => null);

  return session;
}
