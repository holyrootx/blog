<script setup>
import { dismissToast, useAdminToasts } from '../data/adminToastStore';

/**
 * 결과 알림을 쌓아 보여준다. 관리자 레이아웃에 한 번만 붙인다.
 *
 * 화면을 가리지 않는 자리에 둔다 — 결과를 알리자고 방금 한 작업을 덮으면 곤란하다.
 */
const { toasts } = useAdminToasts();
</script>

<template>
  <div class="admin-toast" aria-live="polite">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="admin-toast__item"
      :class="`admin-toast__item--${toast.kind}`"
      :role="toast.kind === 'error' ? 'alert' : 'status'"
    >
      <span class="admin-toast__message">{{ toast.message }}</span>

      <!-- 실패는 저절로 사라지지 않는다. 닫는 길을 열어 둔다 -->
      <button
        v-if="toast.kind === 'error'"
        class="admin-toast__close"
        type="button"
        aria-label="알림 닫기"
        @click="dismissToast(toast.id)"
      >×</button>
    </div>
  </div>
</template>
