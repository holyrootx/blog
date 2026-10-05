<script setup>
/** 슬래시 메뉴 목록. 무엇을 고를지와 고른 뒤의 일은 편집기가 정한다 */
defineProps({
  commands: {
    type: Array,
    required: true,
  },
  activeIndex: {
    type: Number,
    default: 0,
  },
});

const emit = defineEmits(['run', 'hover']);
</script>

<template>
  <ul class="admin-slash block-editor__menu">
    <li
      v-for="(command, commandIndex) in commands"
      :key="command.id"
      class="admin-slash__item"
      :class="{ 'admin-slash__item--active': commandIndex === activeIndex }"
      @mousedown.prevent="emit('run', command)"
      @mouseenter="emit('hover', commandIndex)"
    >
      <span class="admin-slash__main">
        <span class="admin-slash__label">{{ command.label }}</span>
        <kbd class="admin-slash__key">/{{ command.shortcut }}</kbd>
      </span>
      <span class="admin-slash__hint">{{ command.hint }}</span>
    </li>
  </ul>
</template>
