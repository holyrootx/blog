<script setup>
import { computed, onBeforeUnmount, onMounted, nextTick, ref, watch } from 'vue';

// 데스크톱에서는 커스텀 드롭다운(항상 아래로 펼쳐짐)을,
// 모바일에서는 네이티브 select(하단 휠 피커)를 쓴다.
const props = defineProps({
  label: {
    type: String,
    required: true,
  },
  modelValue: {
    type: [String, Number, null],
    default: '',
  },
  // [{ value, label }]
  options: {
    type: Array,
    required: true,
  },
  placeholder: {
    type: String,
    default: '전체',
  },
});

const emit = defineEmits(['update:modelValue']);

const MOBILE_QUERY = '(max-width: 768px)';

const isMobile = ref(false);
const open = ref(false);
const dropUp = ref(false);
const activeIndex = ref(-1);

const rootRef = ref(null);
const listRef = ref(null);

let mediaQuery = null;

// placeholder를 첫 항목으로 둔 실제 목록
const allOptions = computed(() => [
  { value: '', label: props.placeholder },
  ...props.options,
]);

const selectedLabel = computed(() => {
  const found = allOptions.value.find((option) => option.value === props.modelValue);
  return found ? found.label : props.placeholder;
});

function syncMobile() {
  isMobile.value = mediaQuery?.matches ?? false;
}

function onResize() {
  // 창 크기가 바뀌면 데스크톱/모바일 판정을 다시 하고 열린 목록은 닫는다
  syncMobile();
  close();
}

function select(value) {
  emit('update:modelValue', value);
  close();
}

function close() {
  open.value = false;
  activeIndex.value = -1;
}

async function toggle() {
  if (open.value) {
    close();
    return;
  }

  open.value = true;
  activeIndex.value = allOptions.value.findIndex((option) => option.value === props.modelValue);

  await nextTick();
  decideDirection();
  scrollActiveIntoView();
}

// 아래 공간이 부족하면 위로 펼친다
function decideDirection() {
  const trigger = rootRef.value?.querySelector('.admin-combo__trigger');
  const list = listRef.value;

  if (!trigger || !list) {
    return;
  }

  const triggerRect = trigger.getBoundingClientRect();
  const spaceBelow = window.innerHeight - triggerRect.bottom;

  dropUp.value = spaceBelow < list.offsetHeight + 8 && triggerRect.top > spaceBelow;
}

function scrollActiveIntoView() {
  const list = listRef.value;

  if (!list || activeIndex.value < 0) {
    return;
  }

  list.children[activeIndex.value]?.scrollIntoView({ block: 'nearest' });
}

async function move(step) {
  if (!open.value) {
    await toggle();
    return;
  }

  const last = allOptions.value.length - 1;
  const next = activeIndex.value + step;

  activeIndex.value = next < 0 ? last : next > last ? 0 : next;
  scrollActiveIntoView();
}

function onKeydown(event) {
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault();
      move(1);
      break;
    case 'ArrowUp':
      event.preventDefault();
      move(-1);
      break;
    case 'Home':
      if (open.value) {
        event.preventDefault();
        activeIndex.value = 0;
        scrollActiveIntoView();
      }
      break;
    case 'End':
      if (open.value) {
        event.preventDefault();
        activeIndex.value = allOptions.value.length - 1;
        scrollActiveIntoView();
      }
      break;
    case 'Enter':
    case ' ':
      event.preventDefault();
      if (open.value && activeIndex.value >= 0) {
        select(allOptions.value[activeIndex.value].value);
      } else {
        toggle();
      }
      break;
    case 'Escape':
      if (open.value) {
        event.preventDefault();
        close();
      }
      break;
    case 'Tab':
      close();
      break;
    default:
      break;
  }
}

function onDocumentPointerDown(event) {
  if (open.value && rootRef.value && !rootRef.value.contains(event.target)) {
    close();
  }
}

watch(isMobile, (value) => {
  if (value) {
    close();
  }
});

onMounted(() => {
  mediaQuery = window.matchMedia(MOBILE_QUERY);
  isMobile.value = mediaQuery.matches;
  mediaQuery.addEventListener('change', syncMobile);

  document.addEventListener('pointerdown', onDocumentPointerDown);
  window.addEventListener('resize', onResize);
  window.addEventListener('scroll', close, true);
});

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', syncMobile);
  document.removeEventListener('pointerdown', onDocumentPointerDown);
  window.removeEventListener('resize', onResize);
  window.removeEventListener('scroll', close, true);
});
</script>

<template>
  <div class="admin-field">
    <span class="admin-field__label">{{ label }}</span>

    <!-- 모바일: 네이티브 피커 -->
    <select
      v-if="isMobile"
      class="admin-field__select"
      :value="modelValue"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option v-for="option in allOptions" :key="String(option.value)" :value="option.value">
        {{ option.label }}
      </option>
    </select>

    <!-- 데스크톱: 커스텀 드롭다운 -->
    <div v-else ref="rootRef" class="admin-combo">
      <button
        class="admin-combo__trigger"
        :class="{ 'admin-combo__trigger--open': open }"
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        :aria-expanded="open"
        @click="toggle"
        @keydown="onKeydown"
      >
        <span
          class="admin-combo__value"
          :class="{ 'admin-combo__value--placeholder': modelValue === '' }"
        >
          {{ selectedLabel }}
        </span>
        <span class="admin-combo__arrow" aria-hidden="true"></span>
      </button>

      <ul
        v-if="open"
        ref="listRef"
        class="admin-combo__list"
        :class="{ 'admin-combo__list--up': dropUp }"
        role="listbox"
      >
        <li
          v-for="(option, index) in allOptions"
          :key="String(option.value)"
          class="admin-combo__option"
          :class="{
            'admin-combo__option--active': index === activeIndex,
            'admin-combo__option--selected': option.value === modelValue,
          }"
          role="option"
          :aria-selected="option.value === modelValue"
          @mouseenter="activeIndex = index"
          @click="select(option.value)"
        >
          {{ option.label }}
        </li>
      </ul>
    </div>
  </div>
</template>
