<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

// 표 위에 두는 "10개씩 보기" 선택. 기본 10개.
const props = defineProps({
  modelValue: {
    type: Number,
    default: 10,
  },
  options: {
    type: Array,
    default: () => [10, 20, 50, 100],
  },
});

const emit = defineEmits(['update:modelValue']);

const open = ref(false);
const root = ref(null);
const activeIndex = ref(-1);
const selectedLabel = computed(() => `${props.modelValue}개씩`);

function close() {
  open.value = false;
  activeIndex.value = -1;
}

function toggle() {
  open.value = !open.value;
  activeIndex.value = open.value ? props.options.indexOf(props.modelValue) : -1;
}

function select(value) {
  emit('update:modelValue', value);
  close();
}

function move(step) {
  if (!open.value) {
    toggle();
    return;
  }

  const last = props.options.length - 1;
  const next = activeIndex.value + step;
  activeIndex.value = next < 0 ? last : next > last ? 0 : next;
}

function onKeydown(event) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    move(event.key === 'ArrowDown' ? 1 : -1);
    return;
  }

  if ((event.key === 'Enter' || event.key === ' ') && open.value && activeIndex.value >= 0) {
    event.preventDefault();
    select(props.options[activeIndex.value]);
    return;
  }

  if (event.key === 'Escape') {
    close();
  }
}

function closeOnOutside(event) {
  if (open.value && root.value && !root.value.contains(event.target)) {
    close();
  }
}

onMounted(() => document.addEventListener('pointerdown', closeOnOutside));
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeOnOutside));
</script>

<template>
  <div ref="root" class="admin-page-size">
    <button
      class="admin-page-size__trigger"
      type="button"
      role="combobox"
      aria-label="페이지당 표시 개수"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click="toggle"
      @keydown="onKeydown"
    >
      <span>{{ selectedLabel }}</span>
      <span class="admin-page-size__arrow" aria-hidden="true"></span>
    </button>

    <ul v-if="open" class="admin-page-size__list" role="listbox">
      <li v-for="(option, index) in options" :key="option">
        <button
          class="admin-page-size__option"
          :class="{
            'admin-page-size__option--active': index === activeIndex,
            'admin-page-size__option--selected': option === modelValue,
          }"
          type="button"
          role="option"
          :aria-selected="option === modelValue"
          @mouseenter="activeIndex = index"
          @click="select(option)"
        >
          {{ option }}개씩
        </button>
      </li>
    </ul>
  </div>
</template>
