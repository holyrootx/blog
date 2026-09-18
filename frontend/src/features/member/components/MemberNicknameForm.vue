<script setup>
import { ref } from 'vue';

/**
 * 닉네임을 정하는 칸. 신규 가입과 "새로 만들기" 가 같이 쓴다.
 *
 * 두 경우의 차이는 문구와 처음 채워 둘 값뿐이라 화면을 따로 만들지 않는다.
 */
const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  submitLabel: { type: String, default: '시작하기' },
  initialNickname: { type: String, default: '' },
  cancellable: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' },
});

const emit = defineEmits(['submit', 'cancel']);

const MAX_LENGTH = 50;

const nickname = ref(props.initialNickname);

function submit() {
  const trimmed = nickname.value.trim();

  // 서버도 막지만 여기서 먼저 걸러 준다. 빈 값으로 눌러 놓고 오류를 보는 것보다
  // 단추가 눌리지 않는 편이 무슨 일이 필요한지 분명하다
  if (trimmed === '' || props.submitting) {
    return;
  }

  emit('submit', trimmed);
}
</script>

<template>
  <form class="member-auth__panel" @submit.prevent="submit">
    <h1 class="member-auth__title">{{ title }}</h1>
    <p v-if="description" class="member-auth__description">{{ description }}</p>

    <label class="member-auth__label" for="member-nickname">닉네임</label>
    <input
      id="member-nickname"
      v-model="nickname"
      class="member-auth__input"
      type="text"
      autocomplete="nickname"
      :maxlength="MAX_LENGTH"
      placeholder="닉네임을 입력해 주세요"
    />
    <p class="member-auth__counter">{{ nickname.trim().length }} / {{ MAX_LENGTH }}</p>

    <p v-if="errorMessage" class="member-auth__error" role="alert">{{ errorMessage }}</p>

    <div class="member-auth__actions">
      <button
        v-if="cancellable"
        class="member-auth__button member-auth__button--ghost"
        type="button"
        :disabled="submitting"
        @click="emit('cancel')"
      >뒤로</button>

      <button
        class="member-auth__button member-auth__button--primary"
        type="submit"
        :disabled="submitting || nickname.trim() === ''"
      >{{ submitting ? '처리 중…' : submitLabel }}</button>
    </div>
  </form>
</template>
