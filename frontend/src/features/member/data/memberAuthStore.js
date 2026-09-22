import { computed, ref } from 'vue';

import {
  changeMemberNickname,
  getMemberSession,
  logoutMember,
  reactivateMember,
  rejoinMember,
  signUpMember,
  withdrawMember,
} from '../api/memberAuthApi';
import { refreshCsrfToken } from '../../../shared/api/csrfApi';
import { clearNotifications } from './notificationStore';

/**
 * 로그인한 회원 상태.
 *
 * 관리자 상태({@code adminAuthStore})와 따로 둔다. 로그인 방법도 다르고, 관리자 화면은
 * 라우터 가드가 막지만 공개 화면은 로그인하지 않은 사람도 그대로 본다.
 *
 * 이건 화면 표시용이지 보안 경계가 아니다. 실제 판단은 서버가 한다.
 */
const member = ref(null);

/**
 * 서버에 누구인지 물어본 결과가 도착했는가.
 *
 * 이게 없으면 응답 전에는 member 가 null 이라 "로그인 안 함" 과 구별되지 않는다.
 * 헤더가 그 사이에 로그인 링크를 그렸다가 닉네임으로 바꾸면 화면이 한 번 깜빡인다.
 */
const sessionResolved = ref(false);

// 진행 중이거나 이미 끝난 세션 확인. 여러 화면이 동시에 들어와도 요청은 한 번만 나간다
let sessionCheck = null;

export function useMemberAuth() {
  return {
    member,
    sessionResolved,
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
        sessionResolved.value = true;

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
        sessionResolved.value = true;
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

/**
 * 닉네임 변경.
 *
 * 서버가 돌려준 값으로 스토어를 갈아 끼운다. 보낸 값을 그대로 믿지 않는 것은
 * 서버가 앞뒤 공백을 다듬기 때문이다 — 화면과 DB 가 다른 이름을 들고 있으면 안 된다.
 */
export async function changeNickname(nickname) {
  const session = await changeMemberNickname(nickname);

  member.value = session;
  sessionCheck = Promise.resolve(session);

  return session;
}

/**
 * 탈퇴.
 *
 * 서버가 세션을 끊으므로 화면도 로그인 상태를 비운다. 남겨 두면 탈퇴했는데 로그인한
 * 것처럼 보이고, 다음 요청에서 401 이 나서야 알게 된다.
 */
export async function withdraw() {
  try {
    await withdrawMember();
  } finally {
    clearMemberSession();

    // 세션이 끊겼으니 토큰도 새로 받아야 한다. 안 받으면 다시 로그인할 때 첫 요청이 403 이 된다
    await refreshCsrfToken().catch(() => null);
  }
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

  // 알림도 같이 비운다. 남겨 두면 로그아웃한 뒤에도 종에 숫자가 남고,
  // 같은 브라우저에서 다른 사람이 로그인하면 남의 숫자를 보게 된다
  clearNotifications();
}

async function applySession(session) {
  member.value = session;
  sessionResolved.value = true;
  sessionCheck = Promise.resolve(session);

  // 로그인에 성공하면 서버가 CSRF 토큰을 새로 발급한다.
  // 옛 토큰을 들고 있으면 로그인 직후 첫 댓글이 403 으로 막힌다
  await refreshCsrfToken().catch(() => null);

  return session;
}
