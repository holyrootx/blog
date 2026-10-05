<script setup>
import { computed } from 'vue';

import BaseModal from '../../../shared/components/BaseModal.vue';

/**
 * 발행·내리기·예약 취소·삭제 확인 창.
 *
 * 발행 시각은 여기서만 정한다. 글의 내용이 아니라 발행이라는 행동에 딸린 값이다.
 */
const props = defineProps({
  action: {
    type: String,
    default: '',
  },
  postTitle: {
    type: String,
    default: '',
  },
  saving: {
    type: Boolean,
    default: false,
  },
  earliestPublishAt: {
    type: String,
    required: true,
  },
  // 비우면 지금 발행이다
  scheduledAt: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['confirm', 'cancel', 'update:scheduledAt']);

const CONFIRM_TEXTS = {
  publish: {
    title: '이 글을 발행할까요?',
    description: '공개 화면과 검색엔진이 이 글을 보게 됩니다.',
  },
  unpublish: {
    title: '이 글을 내릴까요?',
    description: '공개 화면에서 사라집니다. 발행일은 그대로 남습니다.',
  },
  cancelSchedule: {
    title: '발행 예약을 취소할까요?',
    description: '임시저장 상태로 돌아가고 예약 시각을 지웁니다. 작성 중인 내용은 유지됩니다.',
  },
  delete: {
    title: '이 글을 삭제할까요?',
    description: '휴지통으로 이동합니다. 댓글과 이미지는 보관하며 30일 이내에 복구할 수 있습니다.',
  },
};

const confirmText = computed(() => CONFIRM_TEXTS[props.action] ?? { title: '', description: '' });
</script>

<template>
  <BaseModal
    variant="admin"
    :open="action !== ''"
    :title="confirmText.title"
    :description="confirmText.description"
    size="small"
    @close="emit('cancel')"
  >
    <p class="admin-post__confirm">{{ postTitle }}</p>

    <!-- 과거는 고를 수 없다 — 지나간 시각에 발행할 일이 없다 -->
    <label v-if="action === 'publish'" class="admin-field admin-post__schedule">
      <span class="admin-field__label">발행 시각</span>
      <input
        :value="scheduledAt"
        class="admin-field__input"
        type="datetime-local"
        :min="earliestPublishAt"
        @input="emit('update:scheduledAt', $event.target.value)"
      />
      <small class="admin-post__schedule-hint">
        {{ scheduledAt ? '그때까지 공개 화면에 나오지 않습니다.' : '비워 두면 지금 발행합니다.' }}
      </small>
    </label>

    <template #footer>
      <button
        class="admin-button"
        :class="action === 'delete' ? 'admin-button--danger' : 'admin-button--solid'"
        type="button"
        :disabled="saving"
        @click="emit('confirm')"
      >
        {{ saving ? '처리 중…' : action === 'delete' ? '삭제' : '확인' }}
      </button>

      <button class="admin-button admin-button--ghost" type="button" @click="emit('cancel')">
        취소
      </button>
    </template>
  </BaseModal>
</template>
