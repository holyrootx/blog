<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  modelValue: {
    type: String,
    default: '',
  },
  placeholder: {
    type: String,
    default: '',
  },
});

defineEmits(['update:modelValue']);
</script>

<template>
  <div class="admin-field">
    <label class="admin-field__label">{{ label }}</label>
    <input
      class="admin-field__input"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  </div>
</template>
