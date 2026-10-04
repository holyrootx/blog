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
import { advanceAuthenticationGeneration } from '../../../shared/api/blogApiClient';

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
let sessionGeneration = 0;
let sessionClearedHandler = null;

// 관리자 스토어가 같은 서버 세션의 종료를 구독한다. 회원 쪽에서 역방향으로 import하지 않는다.
export function onMemberSessionCleared(handler) {
  sessionClearedHandler = handler;
}

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
    const generation = sessionGeneration;
    sessionCheck = getMemberSession()
      .then(async (session) => {
        if (generation !== sessionGeneration) return null;
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
        if (generation !== sessionGeneration) return null;
        // 서버가 죽었어도 공개 화면은 읽을 수 있어야 한다. 로그인 안 한 것으로 본다
        member.value = null;
        sessionResolved.value = true;
        return null;
      });
  }

  return sessionCheck;
}

/**
 * 서버 세션이 바뀌었으니 다시 물어본다.
 *
 * {@link ensureMemberSession} 은 한 번 받은 답을 들고 있어서 두 번 묻지 않는다. 평소에는
 * 그게 맞지만 로그인·로그아웃으로 세션 자체가 바뀌면 들고 있던 답이 거짓이 된다 —
 * 관리자로 로그인해도 "비로그인" 으로 굳은 값이 남아서 종이 뜨지 않았다.
 */
export function refreshMemberSession() {
  sessionGeneration += 1;
  sessionCheck = null;

  return ensureMemberSession();
}

export async function completeSignUp(nickname) {
  return completeAuthentication(() => signUpMember(nickname));
}

export async function completeReactivation() {
  return completeAuthentication(reactivateMember);
}

export async function completeRejoin(nickname) {
  return completeAuthentication(() => rejoinMember(nickname));
}

async function completeAuthentication(authenticate) {
  const generation = ++sessionGeneration;
  advanceAuthenticationGeneration();
  await refreshCsrfToken();
  if (generation !== sessionGeneration) throw new Error('로그인 상태가 변경되었습니다. 다시 로그인해 주세요.');
  const session = await authenticate();
  if (generation !== sessionGeneration) throw new Error('로그인 상태가 변경되었습니다. 다시 로그인해 주세요.');
  return applySession(session);
}

/**
 * 닉네임 변경.
 *
 * 서버가 돌려준 값으로 스토어를 갈아 끼운다. 보낸 값을 그대로 믿지 않는 것은
 * 서버가 앞뒤 공백을 다듬기 때문이다 — 화면과 DB 가 다른 이름을 들고 있으면 안 된다.
 */
export async function changeNickname(nickname) {
  const generation = sessionGeneration;
  const session = await changeMemberNickname(nickname);

  if (generation === sessionGeneration) {
    member.value = session;
    sessionCheck = Promise.resolve(session);
  }

  return session;
}

/**
 * 탈퇴.
 *
 * 서버가 세션을 끊으므로 화면도 로그인 상태를 비운다. 남겨 두면 탈퇴했는데 로그인한
 * 것처럼 보이고, 다음 요청에서 401 이 나서야 알게 된다.
 */
export async function withdraw() {
  const generation = sessionGeneration;
  // 탈퇴 실패는 로그아웃 성공을 뜻하지 않는다. 서버가 성공한 뒤에만 상태를 비운다.
  await withdrawMember();
  if (generation !== sessionGeneration) return;
  clearMemberSession();
  await refreshCsrfToken().catch(() => null);
}

export async function signOutMember() {
  const generation = sessionGeneration;
  try {
    await logoutMember();
  } catch (error) {
    if (generation !== sessionGeneration) throw error;
    // 응답을 잃었어도 서버에서 종료됐을 수 있다. 실패를 비로그인으로 바꾸는
    // ensureMemberSession 대신 원래 API로 실제 비로그인을 확인한다.
    let session;
    try {
      session = await getMemberSession();
    } catch {
      throw error;
    }
    if (generation !== sessionGeneration) {
      if (session === null && member.value === null) return;
      throw error;
    }
    if (session !== null) {
      member.value = session;
      sessionCheck = Promise.resolve(session);
      throw error;
    }
  }
  if (generation !== sessionGeneration) return;
  clearMemberSession();
  await refreshCsrfToken().catch(() => null);
}

export function clearMemberSession() {
  // 종료 전에 시작한 /me 응답이 로그인 상태를 되살리지 못하게 한다.
  sessionGeneration += 1;
  advanceAuthenticationGeneration();
  member.value = null;
  sessionResolved.value = true;
  sessionCheck = null;
  sessionClearedHandler?.();

  // 알림도 같이 비운다. 남겨 두면 로그아웃한 뒤에도 종에 숫자가 남고,
  // 같은 브라우저에서 다른 사람이 로그인하면 남의 숫자를 보게 된다
  clearNotifications();
}

async function applySession(session) {
  sessionGeneration += 1;
  // 인증 완료 전에 시작한 요청은 새 로그인 상태와 토큰을 바꾸지 못한다.
  advanceAuthenticationGeneration();
  member.value = session;
  sessionResolved.value = true;
  sessionCheck = Promise.resolve(session);

  // 로그인에 성공하면 서버가 CSRF 토큰을 새로 발급한다.
  // 옛 토큰을 들고 있으면 로그인 직후 첫 댓글이 403 으로 막힌다
  await refreshCsrfToken().catch(() => null);

  return session;
}
