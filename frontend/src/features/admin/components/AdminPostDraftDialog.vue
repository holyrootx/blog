<script setup>
import BaseModal from '../../../shared/components/BaseModal.vue';
import { formatEditorDateTime } from '../data/postEditorDates';

/** 브라우저에 남아 있던 작성 중인 내용을 불러올지 묻는다 */
defineProps({
  draft: {
    type: Object,
    default: null,
  },
  conflict: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['restore', 'discard', 'close']);
</script>

<template>
  <BaseModal
    variant="admin"
    :open="draft !== null"
    title="작성 중이던 내용이 있습니다"
    description="브라우저에 남아 있던 내용입니다. 불러올까요?"
    size="small"
    :close-on-backdrop="false"
    @close="emit('close')"
  >
    <p class="admin-post__confirm">
      {{ formatEditorDateTime(draft?.savedAt) }}에 보관됨
    </p>

    <p v-if="conflict" class="admin-category__confirm-note">
      보관한 뒤 서버에서 이 글이 따로 바뀌었습니다.
      불러오면 화면의 내용이 보관본으로 덮이고, 저장할 때 서버 내용을 덮어씁니다.
    </p>

    <template #footer>
      <button class="admin-button admin-button--ghost" type="button" @click="emit('discard')">
        버리기
      </button>
      <button class="admin-button admin-button--solid" type="button" @click="emit('restore')">
        불러오기
      </button>
    </template>
  </BaseModal>
</template>
