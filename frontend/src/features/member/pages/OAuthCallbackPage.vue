<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import MemberNicknameForm from '../components/MemberNicknameForm.vue';
import OAuthReactivateChoice from '../components/OAuthReactivateChoice.vue';
import OAuthResultNotice from '../components/OAuthResultNotice.vue';
import { getOAuthPending } from '../api/memberAuthApi';
import { takeReturnPath } from '../data/memberReturnPath';
import { refreshCsrfToken } from '../../../shared/api/csrfApi';
import {
  clearMemberSession,
  completeReactivation,
  completeRejoin,
  completeSignUp,
  ensureMemberSession,
} from '../data/memberAuthStore';

/**
 * 소셜 인증을 마친 브라우저가 돌아오는 자리.
 *
 * <p>서버가 결과를 주소의 result 값으로만 알려 준다. 제공자 식별자는 내려오지 않고
 * 서버 세션에만 있다 — 화면이 들고 있다가 되돌려 주는 구조였다면 값을 바꿔 남의 계정으로
 * 가입하거나 남의 탈퇴 계정을 되살릴 수 있다.</p>
 *
 * <p>가입·재가입 단계가 남은 경우에만 화면이 뜬다. 이미 회원이면 볼 것이 없으므로
 * 바로 블로그로 보낸다.</p>
 */
const route = useRoute();
const router = useRouter();

const PHASE = {
  LOADING: 'loading',
  SIGNUP: 'signup',
  CHOICE: 'choice',
  REJOIN: 'rejoin',
  NOTICE: 'notice',
};

const phase = ref(PHASE.LOADING);
const submitting = ref(false);
const errorMessage = ref('');
const suggestedNickname = ref('');
const notice = ref({ title: '', message: '', retryable: true });

onMounted(start);

async function start() {
  switch (route.query.result) {
    case 'success':
      await finishExistingLogin();
      return;

    case 'signup-required':
      await loadPending(PHASE.SIGNUP);
      return;

    case 'reactivation-required':
      await loadPending(PHASE.CHOICE);
      return;

    case 'account-suspended':
      showNotice(
        '로그인할 수 없는 계정입니다',
        '이용이 제한된 계정입니다. 문의가 필요하시면 블로그 주인에게 연락해 주세요.',
        false,
      );
      return;

    case 'login-failed':
      showNotice('로그인하지 못했습니다', '잠시 뒤에 다시 시도해 주세요.');
      return;

    default:
      // 주소를 직접 치고 들어왔거나 result 가 빠진 경우
      showNotice('잘못된 접근입니다', '로그인 화면에서 다시 시작해 주세요.');
  }
}

async function finishExistingLogin() {
  try {
    // 서버 세션은 이미 로그인 상태다. 앱이 뜰 때 확인한 값은 로그인 전 결과일 수 있으므로
    // 비운 뒤 실제 회원 정보를 다시 받는다
    clearMemberSession();
    const session = await ensureMemberSession();

    if (!session) {
      throw new Error('로그인된 회원 정보를 확인하지 못했습니다.');
    }

    // 제공자 화면을 다녀오는 동안 SPA 메모리는 초기화된다. 로그인된 세션의 토큰을
    // 다시 받아 두어야 돌아간 화면에서 첫 댓글 등록이 403으로 막히지 않는다
    await refreshCsrfToken();
    await goHome();
  } catch (error) {
    clearMemberSession();
    showNotice(
      '로그인 정보를 확인하지 못했습니다',
      error?.message ?? '소셜 로그인을 다시 진행해 주세요.',
    );
  }
}

async function loadPending(nextPhase) {
  try {
    // 토큰을 먼저 받는다. 가입·복구는 POST 라 CSRF 토큰이 있어야 하는데,
    // 제공자에 다녀오는 사이 페이지가 통째로 바뀌어 로그인 화면에서 받아 둔 값은 사라졌다.
    // 세션 쿠키는 남아 있으므로 여기서 다시 받으면 같은 세션의 토큰이 온다
    await refreshCsrfToken();

    const pending = await getOAuthPending();

    suggestedNickname.value = pending.suggestedNickname;
    phase.value = nextPhase;
  } catch (error) {
    // 대기 정보는 세션에만 있고 짧게 산다. 새로고침을 오래 미뤘거나 세션이 끊기면 여기로 온다
    showNotice(
      '로그인 정보가 만료되었습니다',
      error?.message ?? '소셜 로그인을 다시 진행해 주세요.',
    );
  }
}

async function submitSignUp(nickname) {
  await runSubmit(() => completeSignUp(nickname));
}

async function submitReactivate() {
  await runSubmit(completeReactivation);
}

async function submitRejoin(nickname) {
  await runSubmit(() => completeRejoin(nickname));
}

/** 가입·복구는 전부 "성공하면 로그인된 채로 블로그로" 라서 처리 방식이 같다 */
async function runSubmit(action) {
  if (submitting.value) {
    return;
  }

  submitting.value = true;
  errorMessage.value = '';

  try {
    await action();
    await goHome();
  } catch (error) {
    errorMessage.value = error?.message ?? '처리하지 못했습니다. 다시 시도해 주세요.';
  } finally {
    submitting.value = false;
  }
}

function showNotice(title, message, retryable = true) {
  notice.value = { title, message, retryable };
  phase.value = PHASE.NOTICE;
}

function startRejoin() {
  errorMessage.value = '';
  phase.value = PHASE.REJOIN;
}

function backToChoice() {
  errorMessage.value = '';
  phase.value = PHASE.CHOICE;
}

/**
 * 로그인하러 떠나기 전에 보던 자리로 돌려보낸다. 없으면 홈이다.
 *
 * 뒤로 가기로 이 화면에 다시 오지 않게 replace 로 나간다 — 대기 정보는 이미 지워져서
 * 다시 들어와도 "만료되었습니다" 만 보게 된다.
 */
function goHome() {
  const returnPath = takeReturnPath();

  return returnPath ? router.replace(returnPath) : router.replace({ name: 'home' });
}

function retryLogin() {
  return router.replace({ name: 'member-login' });
}
</script>

<template>
  <main class="member-auth">
    <p v-if="phase === PHASE.LOADING" class="member-auth__loading">로그인 정보를 확인하고 있습니다…</p>

    <MemberNicknameForm
      v-else-if="phase === PHASE.SIGNUP"
      title="닉네임을 정해 주세요"
      description="댓글에 이 이름으로 표시됩니다. 나중에 바꿀 수 있습니다."
      submit-label="시작하기"
      :initial-nickname="suggestedNickname"
      :submitting="submitting"
      :error-message="errorMessage"
      @submit="submitSignUp"
    />

    <OAuthReactivateChoice
      v-else-if="phase === PHASE.CHOICE"
      :previous-nickname="suggestedNickname"
      :submitting="submitting"
      :error-message="errorMessage"
      @reactivate="submitReactivate"
      @rejoin="startRejoin"
    />

    <MemberNicknameForm
      v-else-if="phase === PHASE.REJOIN"
      title="새 계정으로 시작합니다"
      description="예전 댓글은 그대로 남지만 내 것이 아니게 됩니다. 되돌릴 수 없습니다."
      submit-label="새로 만들기"
      cancellable
      :submitting="submitting"
      :error-message="errorMessage"
      @submit="submitRejoin"
      @cancel="backToChoice"
    />

    <OAuthResultNotice
      v-else
      :title="notice.title"
      :message="notice.message"
      :retryable="notice.retryable"
      @retry="retryLogin"
      @home="goHome"
    />
  </main>
</template>
