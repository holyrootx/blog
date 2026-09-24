<script setup>
import { computed, nextTick, ref, watch } from 'vue';

import BaseModal from '../../../shared/components/BaseModal.vue';

/**
 * 댓글 신고 창.
 *
 * <p>사유를 고르게 하는 이유는 신고를 받는 쪽 때문이다. 자유롭게 적게 하면 무엇이
 * 문제라는 것인지 다 읽어 봐야 알고, 급한 것과 아닌 것을 가릴 수 없다.</p>
 *
 * <p>사유 이름은 서버의 {@code CommentReportReason} 과 같은 말이어야 한다.</p>
 */
const REASONS = [
  { value: 'SPAM', label: '광고·도배' },
  { value: 'ABUSE', label: '욕설·비방' },
  { value: 'ADULT', label: '음란물' },
  { value: 'OTHER', label: '기타' },
];

/** 서버의 CommentReport.MAX_DETAIL_LENGTH 와 같은 값 */
const MAX_DETAIL_LENGTH = 200;

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  /** 신고 대상 댓글의 글쓴이. 무엇을 신고하는지 보이게 한다 */
  target: {
    type: String,
    default: '',
  },
  pending: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['submit', 'close']);

const reason = ref('');
const detail = ref('');
const firstOption = ref(null);

const needsDetail = computed(() => reason.value === 'OTHER');
const canSubmit = computed(() => reason.value !== '' && !props.pending);

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) {
      return;
    }

    // 열 때마다 비운다. 안 비우면 앞서 신고한 사유가 남아서, 다른 댓글을 신고할 때
    // 고르지도 않은 사유가 미리 찍혀 있다
    reason.value = '';
    detail.value = '';

    nextTick(() => firstOption.value?.focus());
  },
);

function submit() {
  if (!canSubmit.value) {
    return;
  }

  emit('submit', {
    reason: reason.value,
    detail: needsDetail.value ? detail.value.trim() : null,
  });
}
</script>

<template>
  <!-- 덮개·제목·× 는 공용 모달이 만든다. 여기는 안에 들어갈 것만 안다 -->
  <BaseModal
    :open="open"
    title="댓글 신고"
    :description="target ? `${target} 님의 댓글` : ''"
    size="small"
    @close="emit('close')"
  >
    <fieldset class="report-dialog__reasons">
      <legend class="report-dialog__legend">사유를 골라 주세요</legend>
      <label
        v-for="(item, index) in REASONS"
        :key="item.value"
        class="report-dialog__reason"
        :class="{ 'report-dialog__reason--picked': reason === item.value }"
      >
        <input
          :ref="index === 0 ? (el) => (firstOption = el) : undefined"
          v-model="reason"
          type="radio"
          name="comment-report-reason"
          :value="item.value"
        />
        <span>{{ item.label }}</span>
      </label>
    </fieldset>

    <textarea
      v-if="needsDetail"
      v-model="detail"
      class="report-dialog__detail"
      rows="3"
      :maxlength="MAX_DETAIL_LENGTH"
      placeholder="무엇이 문제인지 한 줄로 적어 주세요 (선택)"
      aria-label="신고 사유 설명"
    ></textarea>

    <p class="report-dialog__note">
      신고해도 댓글이 바로 숨겨지지는 않습니다. 확인 후 조치하며 개별 회신은 드리지 않습니다.
    </p>

    <p v-if="error" class="report-dialog__error" role="alert">{{ error }}</p>

    <!-- 닫기·취소는 가장 오른쪽. 하려던 일이 먼저 오고 그만두는 길이 끝에 온다 -->
    <template #footer>
      <button
        class="post-button post-button--accent"
        type="button"
        :disabled="!canSubmit"
        @click="submit"
      >{{ pending ? '보내는 중…' : '신고' }}</button>
      <button class="post-button" type="button" :disabled="pending" @click="emit('close')">
        취소
      </button>
    </template>
  </BaseModal>
</template>
