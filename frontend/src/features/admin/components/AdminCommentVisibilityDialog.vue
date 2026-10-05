<script setup>
import { computed, ref, watch } from 'vue';

import { updateAdminCommentVisibility } from '../api/adminApi';
import BaseModal from '../../../shared/components/BaseModal.vue';
import AdminTextInput from './AdminTextInput.vue';
import { notifyError } from '../../../shared/toast/toastStore';

/** 댓글 숨김·복구 확인 창. 바꾼 뒤에는 숨겼는지 되살렸는지를 알리고, 목록은 부른 쪽이 다시 읽는다 */
const props = defineProps({
  target: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['close', 'changed']);

/** 가릴 때 남기는 메모. 조치 이력에 함께 쌓인다 */
const reason = ref('');
const error = ref('');
const submitting = ref(false);

watch(() => props.target, () => {
  reason.value = '';
  error.value = '';
});

const text = computed(() => {
  if (!props.target) {
    return { title: '', description: '', action: '' };
  }

  return props.target.hidden
    ? {
      title: '이 댓글을 다시 공개할까요?',
      description: '블로그 댓글 영역에 내용과 작성자가 다시 표시됩니다.',
      action: '복구',
    }
    : {
      title: '이 댓글을 숨길까요?',
      description: '답글 구조는 유지하고 공개 화면에서는 내용을 감춥니다.',
      action: '숨김',
    };
});

function close() {
  if (submitting.value) {
    return;
  }

  error.value = '';
  emit('close');
}

async function submit() {
  if (!props.target || submitting.value) {
    return;
  }

  submitting.value = true;
  error.value = '';

  try {
    const hidden = !props.target.hidden;
    await updateAdminCommentVisibility(props.target.id, hidden, reason.value || null);
    reason.value = '';
    emit('changed', hidden);
  } catch (requestError) {
    error.value = requestError?.message ?? '댓글 상태를 변경하지 못했습니다.';
    notifyError(error.value);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <BaseModal
    variant="admin"
    :open="Boolean(target)"
    :title="text.title"
    :description="text.description"
    size="small"
    @close="close"
  >
    <!-- 나중에 글쓴이가 "왜 사라졌냐" 물었을 때 가리킬 것은 여기 적은 말뿐이다 -->
    <AdminTextInput
      v-model="reason"
      label="사유 (선택)"
      placeholder="조치 이력에 남습니다"
      :maxlength="200"
    />
    <p v-if="error" class="admin-comment-visibility__error" role="alert">
      {{ error }}
    </p>
    <template #footer>
      <button
        class="admin-button"
        :class="target?.hidden ? 'admin-button--solid' : 'admin-button--danger'"
        type="button"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitting ? '처리 중' : text.action }}
      </button>

      <button class="admin-button" type="button" :disabled="submitting" @click="close">
        취소
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.admin-comment-visibility__error {
  color: #9b3d2d;
}
</style>
