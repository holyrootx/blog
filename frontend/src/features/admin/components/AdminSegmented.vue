<script setup>
// 이지선다 이상을 버튼으로 고르는 컨트롤.
// 조회조건에서는 보통 [전체 / 노출 / 숨김] 처럼 3개까지 쓴다.
defineProps({
  label: {
    type: String,
    required: true,
  },
  modelValue: {
    type: [String, Number, Boolean, null],
    default: '',
  },
  // [{ value, label }]
  options: {
    type: Array,
    required: true,
  },
});

defineEmits(['update:modelValue']);
</script>

<template>
  <div class="admin-field">
    <span class="admin-field__label">{{ label }}</span>
    <div class="admin-segmented" role="group" :aria-label="label">
      <button
        v-for="option in options"
        :key="String(option.value)"
        class="admin-segmented__button"
        :class="{ 'admin-segmented__button--active': modelValue === option.value }"
        type="button"
        :aria-pressed="modelValue === option.value"
        @click="$emit('update:modelValue', option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
