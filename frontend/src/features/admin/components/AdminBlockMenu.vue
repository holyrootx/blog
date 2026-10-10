<script setup>
import { computed, nextTick, ref, watch } from 'vue';

import { INLINE_COLORS } from '../../../shared/post/postInlineColors';

/**
 * 블록 손잡이(⋮⋮)를 누르면 뜨는 메뉴 (노션과 같은 구성).
 *
 * 바꾸기·색·복제·위로·아래로·삭제. 끌어서 옮기기가 어려운 사람도 여기서 순서를 바꿀 수 있다.
 * 색은 블록의 글 전체에 글자 색·배경 색을 칠한다(노션의 블록 색과 같은 자리).
 * 열리면 첫 항목에 커서가 가서 방향키·Enter 로 고를 수 있고, Esc 로 닫으면 블록 선택은 남는다.
 */
const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  // 손잡이의 화면 위치
  anchor: {
    type: Object,
    default: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
  },
  title: {
    type: String,
    default: '',
  },
  // 글자를 칠 수 있는 블록이어야 바꾸기를 보인다
  canTurn: {
    type: Boolean,
    default: true,
  },
  turnOptions: {
    type: Array,
    default: () => [],
  },
  currentType: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['turn', 'color', 'duplicate', 'move', 'remove', 'close']);

const rootRef = ref(null);
const position = ref({ top: 0, left: 0 });
// 열린 하위 메뉴: '' · 'turn' · 'color'
const submenu = ref('');

const COLOR_OPTIONS = [
  { id: '', label: '기본' },
  ...INLINE_COLORS.map((color) => ({ id: color.id, label: `${color.label} 글자` })),
  ...INLINE_COLORS.map((color) => ({ id: `${color.id}-bg`, label: `${color.label} 배경` })),
];

const EDGE_GAP = 8;

const actions = computed(() => [
  ...(props.canTurn ? [{ id: 'turn', label: '바꾸기', hint: '▸' }, { id: 'color', label: '색', hint: '▸' }] : []),
  { id: 'duplicate', label: '복제', hint: '⌘D' },
  { id: 'up', label: '위로 옮기기', hint: '⌘⇧↑' },
  { id: 'down', label: '아래로 옮기기', hint: '⌘⇧↓' },
  { id: 'remove', label: '삭제', hint: 'Del', danger: true },
]);

/**
 * 손잡이 왼쪽에, 손잡이와 위를 맞춰 둔다(노션과 같다). 아래 블록의 손잡이를 덮지 않아
 * 메뉴가 열린 채로도 다른 블록을 Shift 로 더 고를 수 있다. 왼쪽이 모자라면 오른쪽에 둔다.
 */
async function place() {
  await nextTick();

  const element = rootRef.value;

  if (!element) {
    return;
  }

  const width = element.offsetWidth;
  const height = element.offsetHeight;
  const leftSide = props.anchor.left - width - 6;
  const left = leftSide >= EDGE_GAP
    ? leftSide
    : Math.min(props.anchor.right + 6, window.innerWidth - width - EDGE_GAP);
  const top = Math.min(Math.max(props.anchor.top - 6, EDGE_GAP), window.innerHeight - height - EDGE_GAP);

  position.value = { top, left };
}

watch(() => props.open, async (open) => {
  submenu.value = '';

  if (!open) {
    return;
  }

  await place();
  // 키보드로도 바로 고를 수 있게 첫 항목에 커서를 둔다
  rootRef.value?.querySelector('[role="menuitem"]')?.focus();
});

function items() {
  return [...(rootRef.value?.querySelectorAll('[role="menuitem"]') ?? [])];
}

// 메뉴가 직접 받는 키. 나머지(⌘C·⌘V·Delete·⌘D 등)는 메뉴를 닫고 편집기의 블록 단축키로 넘긴다 —
// 손잡이로 고른 뒤 바로 복사·붙여넣기하던 흐름이 메뉴 때문에 막히면 안 된다
const MENU_KEYS = ['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight', 'Escape', 'Enter', ' ', 'Tab'];

function onKeydown(event) {
  if (!MENU_KEYS.includes(event.key)) {
    emit('close');
    return;
  }

  // 편집기 전체 단축키로 새지 않게 한다 (Enter 가 블록 편집으로 들어가는 등)
  event.stopPropagation();

  if (event.key === 'Tab') {
    return;
  }

  const list = items();
  const index = list.indexOf(document.activeElement);

  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    const step = event.key === 'ArrowDown' ? 1 : -1;
    list[(index + step + list.length) % list.length]?.focus();
  } else if (event.key === 'Escape') {
    event.preventDefault();
    emit('close');
  } else if (event.key === 'ArrowRight' && ['turn', 'color'].includes(document.activeElement?.dataset.action)) {
    event.preventDefault();
    openSubmenu(document.activeElement.dataset.action);
  } else if (event.key === 'ArrowLeft' && submenu.value) {
    event.preventDefault();
    const parent = submenu.value;
    submenu.value = '';
    rootRef.value?.querySelector(`[data-action="${parent}"]`)?.focus();
  }
}

async function openSubmenu(name) {
  submenu.value = name;
  await nextTick();
  rootRef.value?.querySelector('.admin-block-menu__submenu [role="menuitem"]')?.focus();
}

function run(action) {
  if (action.id === 'turn' || action.id === 'color') {
    openSubmenu(action.id);
  } else if (action.id === 'up' || action.id === 'down') {
    emit('move', action.id === 'up' ? -1 : 1);
  } else {
    emit(action.id);
  }
}
</script>

<template>
  <!-- div 가 아닌 것은 편집기의 줄(div) 수를 세는 스타일(:only-of-type)에 섞이지 않게 하려는 것이다 -->
  <aside
    v-if="open"
    ref="rootRef"
    class="admin-block-menu"
    :style="{ top: `${position.top}px`, left: `${position.left}px` }"
    role="menu"
    :aria-label="`${title} 블록 메뉴`"
    @keydown="onKeydown"
    @focusout="($event.relatedTarget && rootRef?.contains($event.relatedTarget)) || emit('close')"
  >
    <p class="admin-block-menu__title">{{ title }}</p>

    <button
      v-for="action in actions"
      :key="action.id"
      class="admin-block-menu__item"
      :class="{ 'admin-block-menu__item--danger': action.danger }"
      type="button"
      role="menuitem"
      :data-action="action.id"
      :aria-haspopup="action.hint === '▸' ? 'menu' : undefined"
      :aria-expanded="action.hint === '▸' ? submenu === action.id : undefined"
      @mousedown.prevent
      @click="run(action)"
    >
      <span>{{ action.label }}</span>
      <kbd class="admin-block-menu__hint">{{ action.hint }}</kbd>
    </button>

    <div v-if="submenu === 'turn'" class="admin-block-menu__submenu" role="menu" aria-label="바꾸기">
      <button
        v-for="option in turnOptions"
        :key="option.id"
        class="admin-block-menu__item"
        :class="{ 'admin-block-menu__item--current': option.id === currentType }"
        type="button"
        role="menuitem"
        @mousedown.prevent
        @click="emit('turn', option.id)"
      >
        {{ option.label }}
      </button>
    </div>

    <div
      v-if="submenu === 'color'"
      class="admin-block-menu__submenu admin-block-menu__submenu--color"
      role="menu"
      aria-label="색"
    >
      <button
        v-for="option in COLOR_OPTIONS"
        :key="option.label"
        class="admin-block-menu__item"
        type="button"
        role="menuitem"
        @mousedown.prevent
        @click="emit('color', option.id)"
      >
        <span
          class="admin-text-toolbar__swatch"
          :class="option.id ? `admin-text-toolbar__swatch--${option.id}` : ''"
          aria-hidden="true"
        >A</span>
        <span class="admin-block-menu__item-label">{{ option.label }}</span>
      </button>
    </div>
  </aside>
</template>
