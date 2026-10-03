<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  maxLength: { type: Number, default: 1000 },
  rows: { type: Number, default: 3 },
  label: { type: String, default: '댓글 고치기' },
  pending: Boolean,
  error: { type: String, default: '' },
});
const emit = defineEmits(['update:modelValue', 'save', 'cancel']);
</script>

<template>
  <form class="comment-edit" @submit.prevent="emit('save')">
    <textarea
      :value="modelValue"
      class="comment-edit__input"
      :rows="rows"
      :maxlength="maxLength"
      :aria-label="label"
      @input="emit('update:modelValue', $event.target.value)"
    ></textarea>
    <p v-if="error" class="comment-edit__error" role="alert">{{ error }}</p>
    <div class="comment-edit__actions">
      <button class="comment__action" type="button" @click="emit('cancel')">취소</button>
      <button
        class="comment__action comment__action--strong"
        type="submit"
        :disabled="pending || modelValue.trim() === ''"
      >{{ pending ? '저장 중…' : '저장' }}</button>
    </div>
  </form>
</template>
