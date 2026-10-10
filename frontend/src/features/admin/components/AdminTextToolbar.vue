<script setup>
import { computed, nextTick, ref, watch } from 'vue';

import { INLINE_COLORS } from '../../../shared/post/postInlineColors';

/**
 * 글자를 고르면 그 위에 뜨는 서식 막대. 무엇을 할지는 편집기가 정한다.
 *
 * 단추는 모두 mousedown 을 막는다. 누르는 순간 고른 글자의 선택이 풀리면 서식을 걸 대상이 사라진다.
 * 공개 화면이 그릴 수 있는 서식(굵게·기울임·밑줄·취소선·코드·링크)과 글자 색·배경 색을 둔다.
 */
const props = defineProps({
  // useTextToolbar 의 상태
  toolbar: {
    type: Object,
    required: true,
  },
  // [{ id, label }] 지금 블록을 바꿀 수 있는 종류
  turnOptions: {
    type: Array,
    default: () => [],
  },
  currentType: {
    type: String,
    default: 'paragraph',
  },
});

const emit = defineEmits(['format', 'turn', 'color', 'link-start', 'link-apply', 'link-cancel']);

const rootRef = ref(null);
const linkInputRef = ref(null);
const position = ref({ top: 0, left: 0 });
const turnOpen = ref(false);
const colorOpen = ref(false);
const linkValue = ref('');
const linkError = ref('');

const EDGE_GAP = 8;
const SELECTION_GAP = 8;

// 공개 글은 한 글자에 서식을 하나만 그린다. 겹쳐 걸면 저장 뒤 하나가 사라진다
const BLOCKED_HINT = '이미 다른 서식이 있는 글자입니다. 한 글자에는 서식을 하나만 쓸 수 있어요';

const currentLabel = computed(() => props.turnOptions.find((option) => option.id === props.currentType)?.label ?? '텍스트');

const FORMATS = [
  { tag: 'strong', label: '굵게', shortcut: '⌘B', text: 'B', className: 'admin-text-toolbar__bold' },
  { tag: 'em', label: '기울임', shortcut: '⌘I', text: 'I', className: 'admin-text-toolbar__italic' },
  { tag: 'u', label: '밑줄', shortcut: '⌘U', text: 'U', className: 'admin-text-toolbar__underline' },
  { tag: 's', label: '취소선', shortcut: '⌘⇧S', text: 'S', className: 'admin-text-toolbar__strike' },
  { tag: 'code', label: '코드', shortcut: '⌘E', text: '</>', className: 'admin-text-toolbar__code' },
];

/** 고른 글자 바로 위에 둔다. 위가 모자라면 아래로, 좌우는 화면 안으로 */
async function place() {
  await nextTick();

  const element = rootRef.value;

  if (!element) {
    return;
  }

  const { anchor } = props.toolbar;
  const width = element.offsetWidth;
  const height = element.offsetHeight;
  const center = (anchor.left + anchor.right) / 2;
  const above = anchor.top - SELECTION_GAP - height;
  const top = above >= EDGE_GAP ? above : anchor.bottom + SELECTION_GAP;
  const left = Math.min(Math.max(center - width / 2, EDGE_GAP), window.innerWidth - width - EDGE_GAP);

  position.value = { top, left };
}

// 노션처럼 한 구간에는 글자 색이나 배경 색 하나만 둔다. "기본" 은 색을 지운다
const COLOR_GROUPS = [
  { label: '글자 색', items: [{ id: '', label: '기본' }, ...INLINE_COLORS.map((color) => ({ id: color.id, label: color.label }))] },
  {
    label: '배경 색',
    items: [{ id: '', label: '기본 배경' }, ...INLINE_COLORS.map((color) => ({ id: `${color.id}-bg`, label: `${color.label} 배경` }))],
  },
];

watch(() => [props.toolbar.visible, props.toolbar.anchor, props.toolbar.linkEditing], () => {
  if (!props.toolbar.visible) {
    turnOpen.value = false;
    colorOpen.value = false;
    return;
  }

  place();
}, { deep: true });

watch(() => props.toolbar.linkEditing, async (editing) => {
  if (!editing) {
    return;
  }

  linkValue.value = props.toolbar.href;
  linkError.value = '';
  await nextTick();
  linkInputRef.value?.focus();
  linkInputRef.value?.select();
});

function openLink() {
  turnOpen.value = false;
  colorOpen.value = false;
  emit('link-start');
}

function toggleColors() {
  turnOpen.value = false;
  colorOpen.value = !colorOpen.value;
}

function chooseColor(id) {
  colorOpen.value = false;
  emit('color', id);
}

function isCurrentColor(id) {
  return (props.toolbar.color ?? '') === id;
}

/**
 * 주소를 마크다운 링크에 넣을 수 있는 꼴로 다듬는다.
 * 마크다운 링크 주소에는 띄어쓰기와 괄호가 들어갈 수 없다(넣으면 저장 뒤 링크가 깨진다).
 * "example.com" 처럼 앞부분을 빼고 쓰면 https:// 를 붙인다(노션과 같다).
 */
function normalizeHref(value) {
  const href = value.trim();

  if (!href) {
    return { href: '' };
  }

  if (/[\s()]/.test(href)) {
    return { error: '주소에 띄어쓰기나 괄호는 넣을 수 없습니다.' };
  }

  if (/^(https?:|mailto:|\/|#|\.\/|\.\.\/)/i.test(href)) {
    return { href };
  }

  if (/^[\w-]+(\.[\w-]+)+/.test(href)) {
    return { href: `https://${href}` };
  }

  return { error: 'http(s):// 로 시작하는 주소를 넣어 주세요.' };
}

function applyLink() {
  const { href, error } = normalizeHref(linkValue.value);

  if (error) {
    linkError.value = error;
    return;
  }

  emit('link-apply', href);
}

function onLinkKeydown(event) {
  // 편집기 전체 단축키(Esc 로 블록 고르기 등)로 새지 않게 한다
  event.stopPropagation();

  if (event.key === 'Enter') {
    event.preventDefault();
    applyLink();
  } else if (event.key === 'Escape') {
    event.preventDefault();
    emit('link-cancel');
  }
}

function chooseTurn(option) {
  turnOpen.value = false;
  emit('turn', option.id);
}
</script>

<template>
  <!-- div 가 아닌 것은 편집기의 줄(div) 수를 세는 스타일(:only-of-type)에 섞이지 않게 하려는 것이다 -->
  <aside
    v-if="toolbar.visible"
    ref="rootRef"
    class="admin-text-toolbar"
    :style="{ top: `${position.top}px`, left: `${position.left}px` }"
    role="toolbar"
    aria-label="글자 서식"
  >
    <template v-if="!toolbar.linkEditing">
      <!-- 표 칸처럼 블록을 바꿀 수 없는 자리에서는 바꾸기 단추를 두지 않는다 -->
      <div v-if="turnOptions.length > 0" class="admin-text-toolbar__turn">
        <button
          class="admin-text-toolbar__button admin-text-toolbar__turn-button"
          type="button"
          aria-haspopup="listbox"
          :aria-expanded="turnOpen"
          title="블록 바꾸기"
          @mousedown.prevent
          @click="turnOpen = !turnOpen; colorOpen = false"
        >
          {{ currentLabel }}
          <span class="admin-text-toolbar__caret" aria-hidden="true">▾</span>
        </button>

        <ul v-if="turnOpen" class="admin-text-toolbar__menu" role="listbox" aria-label="블록 바꾸기">
          <li
            v-for="option in turnOptions"
            :key="option.id"
            class="admin-text-toolbar__option"
            :class="{ 'admin-text-toolbar__option--current': option.id === currentType }"
            role="option"
            :aria-selected="option.id === currentType"
            @mousedown.prevent
            @click="chooseTurn(option)"
          >
            {{ option.label }}
          </li>
        </ul>
      </div>

      <span v-if="turnOptions.length > 0" class="admin-text-toolbar__divider" aria-hidden="true"></span>

      <button
        v-for="format in FORMATS"
        :key="format.tag"
        class="admin-text-toolbar__button"
        :class="[format.className, { 'admin-text-toolbar__button--active': toolbar.active[format.tag] }]"
        type="button"
        :aria-label="format.label"
        :aria-pressed="toolbar.active[format.tag]"
        :disabled="toolbar.blocked[format.tag]"
        :title="toolbar.blocked[format.tag] ? BLOCKED_HINT : `${format.label} ${format.shortcut}`"
        @mousedown.prevent
        @click="emit('format', format.tag)"
      >
        {{ format.text }}
      </button>

      <button
        class="admin-text-toolbar__button admin-text-toolbar__link"
        :class="{ 'admin-text-toolbar__button--active': toolbar.active.a }"
        type="button"
        aria-label="링크"
        :aria-pressed="toolbar.active.a"
        :disabled="toolbar.blocked.a"
        :title="toolbar.blocked.a ? BLOCKED_HINT : '링크 ⌘K'"
        @mousedown.prevent
        @click="openLink"
      >
        링크
      </button>

      <div class="admin-text-toolbar__color">
        <button
          class="admin-text-toolbar__button admin-text-toolbar__color-button"
          :class="toolbar.color ? `admin-text-toolbar__swatch--${toolbar.color}` : ''"
          type="button"
          aria-label="색"
          aria-haspopup="menu"
          :aria-expanded="colorOpen"
          title="글자 색·배경 색 ⌘⇧H"
          @mousedown.prevent
          @click="toggleColors"
        >
          A
          <span class="admin-text-toolbar__caret" aria-hidden="true">▾</span>
        </button>

        <div v-if="colorOpen" class="admin-text-toolbar__colors" role="menu" aria-label="색">
          <div v-for="group in COLOR_GROUPS" :key="group.label" class="admin-text-toolbar__color-group" role="group" :aria-label="group.label">
            <p class="admin-text-toolbar__color-title" aria-hidden="true">{{ group.label }}</p>
            <button
              v-for="item in group.items"
              :key="item.label"
              class="admin-text-toolbar__color-option"
              type="button"
              role="menuitemradio"
              :aria-checked="isCurrentColor(item.id)"
              :aria-label="item.label"
              :title="item.label"
              @mousedown.prevent
              @click="chooseColor(item.id)"
            >
              <span
                class="admin-text-toolbar__swatch"
                :class="item.id ? `admin-text-toolbar__swatch--${item.id}` : ''"
                aria-hidden="true"
              >A</span>
            </button>
          </div>
        </div>
      </div>
    </template>

    <form v-else class="admin-text-toolbar__link-form" @submit.prevent="applyLink">
      <input
        ref="linkInputRef"
        v-model="linkValue"
        class="admin-text-toolbar__link-input"
        type="text"
        aria-label="링크 주소"
        placeholder="링크 주소를 붙여넣으세요"
        @keydown="onLinkKeydown"
        @blur="emit('link-cancel')"
      />
      <button class="admin-text-toolbar__button" type="submit" @mousedown.prevent>적용</button>
      <button
        v-if="toolbar.href"
        class="admin-text-toolbar__button"
        type="button"
        @mousedown.prevent
        @click="emit('link-apply', '')"
      >
        링크 제거
      </button>
      <p v-if="linkError" class="admin-text-toolbar__error" role="alert">{{ linkError }}</p>
    </form>
  </aside>
</template>
