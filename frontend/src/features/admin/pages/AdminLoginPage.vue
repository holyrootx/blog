<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { signInAdmin } from '../data/adminAuthStore';

/**
 * 관리자 로그인.
 *
 * 사이드바가 있는 관리자 레이아웃 밖에 둔다. 로그인하지 않은 사람에게
 * 메뉴 이름과 구조를 먼저 보여줄 이유가 없다.
 */
const route = useRoute();
const router = useRouter();

const username = ref('');
const password = ref('');
const submitting = ref(false);
const error = ref('');

const usernameRef = ref(null);

const canSubmit = computed(() => username.value.trim() !== '' && password.value !== '');

// 세션이 끊겨서 튕겨 온 경우 원래 가려던 곳을 기억해 둔다
const redirectTo = computed(() => {
  const target = route.query.redirect;

  // 외부 주소로 보내지지 않도록 같은 사이트 경로만 받는다
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : null;
});

onMounted(() => {
  usernameRef.value?.focus();
});

async function submit() {
  if (submitting.value || !canSubmit.value) {
    return;
  }

  submitting.value = true;
  error.value = '';

  try {
    await signInAdmin(username.value.trim(), password.value);

    // 비밀번호를 메모리에 남겨 둘 이유가 없다
    password.value = '';

    await router.replace(redirectTo.value ?? { name: 'admin-dashboard' });
  } catch (caught) {
    // 서버가 "아이디 또는 비밀번호가 올바르지 않습니다" 처럼 이미 사람이 읽을 문구를 준다
    error.value = caught.message;
    password.value = '';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="admin-login">
    <form class="admin-login__card" @submit.prevent="submit">
      <div class="admin-login__head">
        <span class="admin-login__brand">정성주의 기록</span>
        <h1 class="admin-login__title">관리자 로그인</h1>
      </div>

      <label class="admin-field">
        <span class="admin-field__label">아이디</span>
        <input
          ref="usernameRef"
          v-model="username"
          class="admin-field__input"
          type="text"
          autocomplete="username"
          :disabled="submitting"
        />
      </label>

      <label class="admin-field">
        <span class="admin-field__label">비밀번호</span>
        <input
          v-model="password"
          class="admin-field__input"
          type="password"
          autocomplete="current-password"
          :disabled="submitting"
        />
      </label>

      <!-- 실패 사유는 서버가 "아이디 또는 비밀번호"로 뭉뚱그려 준다.
           어느 쪽이 틀렸는지 알려주면 아이디가 있는지 확인해 주는 꼴이 된다 -->
      <p v-if="error" class="admin-login__error" role="alert">{{ error }}</p>

      <button
        class="admin-button admin-button--solid admin-login__submit"
        type="submit"
        :disabled="submitting || !canSubmit"
      >
        {{ submitting ? '확인 중…' : '로그인' }}
      </button>

      <RouterLink class="admin-login__back" :to="{ name: 'home' }">블로그로 돌아가기</RouterLink>
    </form>
  </div>
</template>
