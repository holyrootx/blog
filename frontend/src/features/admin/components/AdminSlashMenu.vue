<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

/**
 * 슬래시 메뉴 목록. 무엇을 고를지와 고른 뒤의 일은 편집기가 정한다.
 *
 * 화면 안에 들어오게 둔다. 아래 공간이 모자라면 위로 펼치고(노션과 같다),
 * 방향키로 고른 항목이 목록 밖으로 나가면 따라 스크롤한다 — 안 보이는 항목을 고르고 있으면
 * 무엇을 고르는지 알 수 없다.
 */
const props = defineProps({
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

const listRef = ref(null);
const up = ref(false);

// 메뉴와 화면 끝 사이에 남길 틈
const EDGE_GAP = 8;
// 이보다 좁아지면 항목을 고르기 어렵다
const MIN_HEIGHT = 120;

/**
 * 아래로 펼쳐서 잘리면 위로 펼친다. 위아래 다 모자라면 넓은 쪽에 펼치고 높이를 그만큼 줄인다.
 * 열려 있는 동안 화면이 스크롤되면 다시 잰다 — 처음 잰 자리에 두면 메뉴가 화면 밖으로 밀려난다.
 */
async function place() {
  await nextTick();

  const list = listRef.value;
  const row = list?.parentElement;

  if (!list || !row) {
    return;
  }

  // 관리자 화면 틀은 넘친 부분을 잘라 낸다(스크롤도 안 생긴다). 그 틀과 창 중 좁은 쪽이 한계다
  const frame = list.closest('.admin-shell')?.getBoundingClientRect();
  const bottomLimit = Math.min(window.innerHeight, frame?.bottom ?? window.innerHeight) - EDGE_GAP;
  const topLimit = Math.max(0, frame?.top ?? 0) + EDGE_GAP;
  const rowBox = row.getBoundingClientRect();

  // 제 높이(스타일의 최대 높이까지)를 먼저 잰다
  list.style.maxHeight = '';
  const height = list.offsetHeight;
  const below = bottomLimit - rowBox.bottom;
  const above = rowBox.top - topLimit;

  up.value = below < height && above > below;

  const room = up.value ? above : below;
  list.style.maxHeight = room < height ? `${Math.max(MIN_HEIGHT, Math.floor(room))}px` : '';
}

let frameRequest = 0;

function schedulePlace(event) {
  // 메뉴 자신의 목록 스크롤(방향키 따라가기)로는 자리가 바뀌지 않는다
  if (event?.target === listRef.value) {
    return;
  }

  cancelAnimationFrame(frameRequest);
  frameRequest = requestAnimationFrame(place);
}

/** 고른 항목이 목록 안에 보이게 한다. 페이지는 움직이지 않고 목록만 스크롤한다 */
function revealActive() {
  const list = listRef.value;
  const item = list?.children[props.activeIndex];

  if (!list || !item) {
    return;
  }

  if (item.offsetTop < list.scrollTop) {
    list.scrollTop = item.offsetTop;
  } else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
    list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
  }
}

onMounted(() => {
  place();
  // 편집기를 담은 어느 상자가 스크롤돼도 잡도록 capture 로 받는다
  window.addEventListener('scroll', schedulePlace, { capture: true, passive: true });
  window.addEventListener('resize', schedulePlace, { passive: true });
});

onBeforeUnmount(() => {
  cancelAnimationFrame(frameRequest);
  window.removeEventListener('scroll', schedulePlace, { capture: true });
  window.removeEventListener('resize', schedulePlace);
});

// 글자를 쳐서 항목 수가 바뀌면 높이도 바뀐다
watch(() => props.commands.length, place);
watch(() => props.activeIndex, () => nextTick(revealActive));
</script>

<template>
  <ul
    ref="listRef"
    class="admin-slash block-editor__menu"
    :class="{ 'admin-slash--up': up }"
    role="listbox"
    aria-label="블록 고르기"
  >
    <li
      v-for="(command, commandIndex) in commands"
      :key="command.id"
      class="admin-slash__item"
      :class="{ 'admin-slash__item--active': commandIndex === activeIndex }"
      role="option"
      :aria-selected="commandIndex === activeIndex"
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
