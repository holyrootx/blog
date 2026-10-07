<script setup>
import { ref, watch } from 'vue';

import { replyAdminComment } from '../api/adminApi';
import BaseModal from '../../../shared/components/BaseModal.vue';

/** 관리자 답글 작성 창. 등록이 끝나면 알리고, 목록을 다시 읽는 일은 부른 쪽이 한다 */
const props = defineProps({
  target: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['close', 'replied']);

const content = ref('');
const error = ref('');
const submitting = ref(false);

// 다른 댓글로 창을 다시 열면 앞에서 쓰던 답글을 들고 가지 않는다
watch(() => props.target, () => {
  content.value = '';
  error.value = '';
});

function close() {
  if (submitting.value) {
    return;
  }

  emit('close');
}

async function submit() {
  const trimmed = content.value.trim();
  if (!trimmed) {
    error.value = '답글 내용을 입력해 주세요.';
    return;
  }
  if (submitting.value) {
    return;
  }

  submitting.value = true;
  error.value = '';

  try {
    await replyAdminComment(props.target.id, trimmed);
    content.value = '';
    emit('replied');
  } catch (requestError) {
    error.value = requestError?.message ?? '답글을 등록하지 못했습니다.';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <BaseModal
    variant="admin"
    :open="Boolean(target)"
    title="답글 작성"
    :description="target ? `${target.nickname} 님의 댓글에 답글을 남깁니다.` : ''"
    :close-on-backdrop="false"
    @close="close"
  >
    <div class="admin-comment-reply-form">
      <blockquote v-if="target">{{ target.content }}</blockquote>
      <label for="admin-comment-reply">답글 내용</label>
      <textarea
        id="admin-comment-reply"
        v-model="content"
        maxlength="1000"
        rows="6"
        placeholder="답글을 입력하세요"
      ></textarea>
      <div class="admin-comment-reply-form__meta">
        <span>{{ content.length }} / 1000</span>
        <span v-if="error" class="admin-comment-reply-form__error" role="alert">
          {{ error }}
        </span>
      </div>
    </div>
    <template #footer>
      <button
        class="admin-button admin-button--solid"
        type="button"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitting ? '등록 중' : '답글 등록' }}
      </button>

      <button class="admin-button" type="button" :disabled="submitting" @click="close">
        취소
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.admin-comment-reply-form blockquote {
  margin: 0 0 18px;
  padding: 12px 14px;
  border-left: 3px solid var(--admin-accent-line);
  background: var(--admin-surface-subtle);
  color: var(--admin-text);
  font-size: 13px;
  line-height: 1.65;
}

.admin-comment-reply-form label {
  display: block;
  margin-bottom: 7px;
  color: var(--admin-text-strong);
  font-size: 12px;
  font-weight: 700;
}

.admin-comment-reply-form textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--admin-card-line);
  border-radius: 7px;
  padding: 12px;
  background: var(--admin-card);
  color: var(--admin-text-strong);
  font: inherit;
  font-size: 14px;
  line-height: 1.65;
}

.admin-comment-reply-form textarea:focus {
  border-color: var(--admin-accent);
  outline: none;
}

.admin-comment-reply-form__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 7px;
  color: var(--admin-meta);
  font-size: 11.5px;
}

.admin-comment-reply-form__error {
  color: var(--admin-error-text);
}
</style>
