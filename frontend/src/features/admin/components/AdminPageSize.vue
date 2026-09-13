<script setup>
// 표 위에 두는 "10개씩 보기" 선택. 기본 10개.
defineProps({
  modelValue: {
    type: Number,
    default: 10,
  },
  options: {
    type: Array,
    default: () => [10, 20, 50, 100],
  },
});

defineEmits(['update:modelValue']);
</script>

<template>
  <div class="admin-page-size">
    <label class="admin-page-size__control">
      <select
        class="admin-page-size__select"
        :value="modelValue"
        @change="$emit('update:modelValue', Number($event.target.value))"
      >
        <option v-for="option in options" :key="option" :value="option">{{ option }}개씩</option>
      </select>
    </label>
  </div>
</template>
