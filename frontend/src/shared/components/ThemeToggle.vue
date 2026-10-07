<script setup>
import { computed } from 'vue';

import { currentTheme, toggleTheme } from '../theme/themeStore';

/**
 * 밝은 화면 ↔ 어두운 화면. 공개 헤더와 관리자 윗줄이 같이 쓴다.
 *
 * 그림은 누르면 갈 쪽을 보여 준다(밝을 때 달, 어두울 때 해). 이름도 같은 뜻으로 바꾼다 —
 * 화면 읽기 프로그램은 그림을 못 보니 누르면 무엇이 되는지를 말로 알려야 한다.
 */
const isDark = computed(() => currentTheme.value === 'dark');
const label = computed(() => (isDark.value ? '밝은 화면으로 보기' : '어두운 화면으로 보기'));
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="label"
    :title="label"
    @click="toggleTheme"
  >
    <svg v-if="isDark" viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="4.5" fill="none" stroke="currentColor" stroke-width="2" />
      <path
        d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
      />
    </svg>
    <svg v-else viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" focusable="false">
      <path
        d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linejoin="round"
      />
    </svg>
  </button>
</template>

<style scoped>
/* 옆에 서는 알림 종(.notification-bell__trigger)과 크기·모양을 맞춘다 */
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: none;
  color: inherit;
  cursor: pointer;
}

.theme-toggle:hover,
.theme-toggle:focus-visible {
  border-color: var(--line);
  background: var(--surface);
  outline: none;
}
</style>
