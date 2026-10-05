<script setup>
import { ref } from 'vue';

import { uploadAdminImage } from '../api/adminApi';
import BaseModal from '../../../shared/components/BaseModal.vue';

/**
 * 글 편집 화면의 대표 이미지 칸. 미리보기, 교체 창, 업로드를 함께 맡는다.
 *
 * 값은 대표 이미지 주소 하나다. 비어 있으면 화면은 본문 첫 이미지나 기본 이미지를 보여 주므로
 * 미리보기 주소({@code previewUrl})는 편집 화면이 정해서 넘긴다.
 */
const props = defineProps({
  modelValue: {
    type: String,
    required: true,
  },
  previewUrl: {
    type: String,
    required: true,
  },
  postTitle: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update:modelValue']);

const fileInput = ref(null);
const editorOpen = ref(false);
const uploading = ref(false);
const uploadError = ref('');

function openEditor() {
  uploadError.value = '';
  editorOpen.value = true;
}

async function upload(event) {
  const file = event.target.files?.[0];

  if (!file || uploading.value) {
    return;
  }

  uploading.value = true;
  uploadError.value = '';

  try {
    const image = await uploadAdminImage(file);

    if (!image.url) {
      throw new Error('업로드한 이미지 주소를 받지 못했습니다.');
    }

    emit('update:modelValue', image.url);
    editorOpen.value = false;
  } catch (error) {
    console.error(error);
    uploadError.value = error.message || '대표 이미지를 업로드하지 못했습니다.';
  } finally {
    uploading.value = false;
    event.target.value = '';
  }
}

function reset() {
  emit('update:modelValue', '');
  uploadError.value = '';
  editorOpen.value = false;
}

const altText = (suffix) => `${props.postTitle || '게시글'} ${suffix}`;
</script>

<template>
  <div class="admin-editor__thumbnail">
    <span class="admin-field__label">대표 이미지</span>
    <div class="admin-editor__thumbnail-media">
      <img :src="previewUrl" :alt="altText('대표 이미지')" />
      <button
        class="admin-button admin-button--ghost admin-button--small"
        type="button"
        :disabled="disabled || uploading"
        @click="openEditor"
      >
        수정하기
      </button>
    </div>
  </div>

  <BaseModal
    variant="admin"
    :open="editorOpen"
    title="대표 이미지 수정"
    size="small"
    @close="editorOpen = false"
  >
    <div class="admin-editor__thumbnail-dialog">
      <img :src="previewUrl" :alt="altText('대표 이미지 미리보기')" />
      <p v-if="uploadError" class="admin-form-error">{{ uploadError }}</p>
    </div>

    <template #footer>
      <button
        class="admin-button admin-button--ghost"
        type="button"
        :disabled="uploading"
        @click="reset"
      >
        초기화
      </button>
      <button
        class="admin-button admin-button--solid"
        type="button"
        :disabled="uploading"
        @click="fileInput?.click()"
      >
        {{ uploading ? '업로드 중' : '이미지 교체' }}
      </button>
    </template>
  </BaseModal>

  <input
    ref="fileInput"
    class="admin-editor__thumbnail-input"
    type="file"
    accept="image/jpeg,image/png,image/webp"
    @change="upload"
  />
</template>
