<script setup>
import { dismissToast, useToasts } from '../toast/toastStore';

/**
 * 결과 알림을 쌓아 보여준다.
 *
 * <p>앱 뿌리에 <b>한 번만</b> 붙인다. 공개 화면에는 관리자처럼 공통 레이아웃이 없어서
 * 화면마다 달면 빠뜨리는 곳이 생긴다.</p>
 *
 * <p>화면을 가리지 않는 자리에 둔다 — 결과를 알리자고 방금 한 작업을 덮으면 곤란하다.</p>
 */
const { toasts } = useToasts();
</script>

<template>
  <div class="app-toast" aria-live="polite">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="app-toast__item"
      :class="`app-toast__item--${toast.kind}`"
      :role="toast.kind === 'error' ? 'alert' : 'status'"
    >
      <span class="app-toast__message">{{ toast.message }}</span>

      <!-- 실패는 저절로 사라지지 않는다. 닫는 길을 열어 둔다 -->
      <button
        v-if="toast.kind === 'error'"
        class="app-toast__close"
        type="button"
        aria-label="알림 닫기"
        @click="dismissToast(toast.id)"
      >×</button>
    </div>
  </div>
</template>
