<script setup>
/**
 * 더 진행할 게 없는 결과를 알리는 화면. 정지된 계정, 로그인 실패, 만료된 대기 정보.
 *
 * 왜 막혔는지는 서버가 결과 코드로만 알려 준다. 사유를 자세히 적으면 남의 계정 상태를
 * 알아내는 통로가 되므로, 화면도 사용자가 다음에 할 수 있는 일만 말한다.
 */
defineProps({
  title: { type: String, required: true },
  message: { type: String, required: true },
  retryable: { type: Boolean, default: true },
});

const emit = defineEmits(['retry', 'home']);
</script>

<template>
  <div class="member-auth__panel">
    <h1 class="member-auth__title">{{ title }}</h1>
    <p class="member-auth__description" role="alert">{{ message }}</p>

    <div class="member-auth__actions">
      <button
        class="member-auth__button member-auth__button--ghost"
        type="button"
        @click="emit('home')"
      >블로그로 돌아가기</button>

      <button
        v-if="retryable"
        class="member-auth__button member-auth__button--primary"
        type="button"
        @click="emit('retry')"
      >다시 로그인</button>
    </div>
  </div>
</template>
