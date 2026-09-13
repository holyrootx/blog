<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  // 체크박스 옆에 붙는 문구. 비우면 label만 보인다
  text: {
    type: String,
    default: '',
  },
  modelValue: {
    type: Boolean,
    default: false,
  },
});

defineEmits(['update:modelValue']);
</script>

<template>
  <div class="admin-field">
    <span class="admin-field__label">{{ label }}</span>
    <label class="admin-field__checkbox">
      <input
        type="checkbox"
        :checked="modelValue"
        @change="$emit('update:modelValue', $event.target.checked)"
      />
      <span>{{ text || label }}</span>
    </label>
  </div>
</template>
