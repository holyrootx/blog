<script setup>
import { computed, nextTick, reactive, ref } from 'vue';

import AdminBlockText from './AdminBlockText.vue';

/**
 * 표 블록 (노션의 "단순 표" 와 같은 동작).
 *
 * 칸마다 글자 칸(AdminBlockText)이라 굵게·링크 같은 인라인 서식이 칸 안에서도 된다.
 * 칸 사이 이동: Tab·Shift+Tab 은 다음·앞 칸, Enter 는 아래 칸(마지막 줄이면 표 다음 줄),
 * 방향키는 칸 끝에서 옆·위·아래 칸으로 넘어간다. 마지막 칸에서 Tab 을 누르면 줄이 하나 늘어난다.
 *
 * 줄·열 손잡이: 마우스를 올리거나 커서를 둔 칸의 줄 왼쪽(⋮⋮)과 열 위(⋯)에 손잡이가 뜬다.
 * 누르면 추가·삭제·정렬 메뉴가 열린다. 표 오른쪽·아래 끝의 + 막대는 열·줄을 끝에 더한다.
 *
 * 값은 직접 고치지 않고 update 로 바뀐 칸 배열을 통째로 올려 보낸다. 되돌리기 장면과 복제본이
 * 같은 배열을 나눠 쓰고 있어서, 제자리에서 고치면 지난 장면까지 같이 바뀐다.
 */
const props = defineProps({
  block: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['update', 'leave', 'continue', 'command']);

const ALIGN_LABELS = { left: '왼쪽', center: '가운데', right: '오른쪽' };

const rootRef = ref(null);
const cellRefs = new Map();
const hover = reactive({ row: -1, col: -1 });
const focused = reactive({ row: -1, col: -1 });
const menu = reactive({ kind: '', index: -1, top: 0, left: 0, active: 0 });

const columnCount = computed(() => Math.max(1, ...props.block.rows.map((cells) => cells.length)));
const lastRow = computed(() => props.block.rows.length - 1);
const activeRow = computed(() => (hover.row >= 0 ? hover.row : focused.row));
const activeCol = computed(() => (hover.col >= 0 ? hover.col : focused.col));

const menuItems = computed(() => {
  if (menu.kind === 'row') {
    return [
      { id: 'row-above', label: '위에 줄 추가' },
      { id: 'row-below', label: '아래에 줄 추가' },
      ...(menu.index === 0
        ? [{ id: 'header', label: '제목 행', checked: props.block.header }]
        : []),
      { id: 'row-remove', label: '줄 삭제', danger: true, disabled: props.block.rows.length <= 1 },
    ];
  }

  if (menu.kind === 'col') {
    return [
      { id: 'col-left', label: '왼쪽에 열 추가' },
      { id: 'col-right', label: '오른쪽에 열 추가' },
      ...Object.entries(ALIGN_LABELS).map(([value, label]) => ({
        id: `align-${value}`,
        label: `${label} 정렬`,
        checked: (props.block.columnAlign[menu.index] || 'left') === value,
      })),
      { id: 'col-remove', label: '열 삭제', danger: true, disabled: columnCount.value <= 1 },
    ];
  }

  return [];
});

function cellKey(row, col) {
  return `${row}:${col}`;
}

function setCellRef(row, col, element) {
  if (element) {
    cellRefs.set(cellKey(row, col), element);
  } else {
    cellRefs.delete(cellKey(row, col));
  }
}

function cellAt(row, col) {
  return cellRefs.get(cellKey(row, col)) ?? null;
}

/** @param position 'start' · 'end' · 글자 수 */
async function focusCell(row, col, position = 'end') {
  await nextTick();
  cellAt(row, col)?.focus(position);
}

/** 바깥(편집기)에서 표로 들어올 때. 위에서 오면 첫 칸, 아래에서 오면 마지막 줄 첫 칸 */
function focus(position = 'end') {
  return position === 'start' ? focusCell(0, 0, 'start') : focusCell(lastRow.value, 0, 'end');
}

/** 칸 배열을 새로 만든다. 모자란 칸은 빈 칸으로 채워 모든 줄의 칸 수를 맞춘다 */
function copyRows() {
  return props.block.rows.map((cells) => Array.from({ length: columnCount.value }, (_, col) => cells[col] ?? ''));
}

function update(patch, mergeKey = '') {
  emit('update', patch, mergeKey);
}

function setCell(row, col, value) {
  const rows = copyRows();
  rows[row][col] = value;
  update({ rows }, `table:${props.block.id}:${row}:${col}`);
}

/* ── 줄·열 바꾸기 ───────────────────────── */

function addRow(at) {
  const rows = copyRows();
  rows.splice(at, 0, Array(columnCount.value).fill(''));
  update({ rows });
  return at;
}

/** 끝에 줄·열을 더하고 그 칸으로 간다 (+ 막대, 마지막 칸 Tab) */
function appendRow(col) {
  focusCell(addRow(props.block.rows.length), col, 'start');
}

function appendColumn(row) {
  const at = columnCount.value;
  addColumn(at);
  focusCell(row, at, 'start');
}

function removeRow(at) {
  if (props.block.rows.length <= 1) {
    return;
  }

  const rows = copyRows();
  rows.splice(at, 1);
  update({ rows });
}

function addColumn(at) {
  const rows = copyRows().map((cells) => {
    cells.splice(at, 0, '');
    return cells;
  });
  const align = Array.from({ length: columnCount.value }, (_, col) => props.block.columnAlign[col] ?? '');
  align.splice(at, 0, '');
  update({ rows, columnAlign: align });
}

function removeColumn(at) {
  if (columnCount.value <= 1) {
    return;
  }

  const rows = copyRows().map((cells) => cells.filter((_, col) => col !== at));
  const align = Array.from({ length: columnCount.value }, (_, col) => props.block.columnAlign[col] ?? '')
    .filter((_, col) => col !== at);
  update({ rows, columnAlign: align });
}

function setAlign(col, value) {
  const align = Array.from({ length: columnCount.value }, (_, index) => props.block.columnAlign[index] ?? '');
  // 왼쪽은 기본값이라 저장 표기(:---)를 따로 붙이지 않는다
  align[col] = value === 'left' ? '' : value;
  update({ columnAlign: align });
}

/* ── 키보드 ─────────────────────────────── */

function nextCell(row, col, step) {
  const flat = row * columnCount.value + col + step;
  const total = props.block.rows.length * columnCount.value;

  if (flat < 0 || flat >= total) {
    return null;
  }

  return { row: Math.floor(flat / columnCount.value), col: flat % columnCount.value };
}

function onCellKeydown(row, col, event) {
  // 한글 조합을 확정하려고 누른 Enter·Tab 이 칸 이동으로 새지 않게 한다
  if (event.isComposing || event.keyCode === 229) {
    return;
  }

  const cell = cellAt(row, col);
  const command = (event.metaKey || event.ctrlKey) && !event.altKey;
  const plain = !event.metaKey && !event.ctrlKey && !event.altKey;

  // 굵게·기울임·밑줄·코드·취소선은 칸 안에서 그대로 쓴다
  const formatKeys = { b: 'strong', i: 'em', u: 'u', e: 'code' };
  const key = event.key.toLowerCase();
  const format = command && !event.shiftKey ? formatKeys[key] : (command && event.shiftKey && key === 's' ? 's' : '');

  if (format) {
    event.preventDefault();
    cell?.toggleFormat(format);
    // 서식만 바뀌면 입력 이벤트가 오지 않는다. 바뀐 글을 직접 올린다
    setCell(row, col, cell?.readText() ?? props.block.rows[row][col]);
    emit('command', 'format', event);
    return;
  }

  // 블록 단위 단축키(⌘D 복제, ⌘⇧↑↓ 옮기기)와 편집기가 들고 있는 것(⌘K 링크, ⌘⇧H 마지막 색)은 편집기가 한다
  const editorKey = (!event.shiftKey && (key === 'd' || key === 'k')) || (event.shiftKey && key === 'h');

  if (command && (editorKey || (event.shiftKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown')))) {
    event.preventDefault();
    emit('command', editorKey ? key : event.key, event);
    return;
  }

  if (event.key === 'Tab' && plain) {
    event.preventDefault();
    const target = nextCell(row, col, event.shiftKey ? -1 : 1);

    if (target) {
      focusCell(target.row, target.col, 'end');
    } else if (!event.shiftKey) {
      // 마지막 칸에서 Tab: 줄을 하나 더한다 (노션과 같다)
      appendRow(0);
    }

    return;
  }

  if (event.key === 'Enter' && !event.metaKey && !event.ctrlKey) {
    // 칸 안 줄바꿈은 저장 형식(GitHub 표)이 담지 못한다. Enter 는 아래 칸으로 간다
    event.preventDefault();

    if (event.shiftKey) {
      return;
    }

    if (row < lastRow.value) {
      focusCell(row + 1, col, 'end');
    } else {
      emit('continue');
    }

    return;
  }

  if (!plain || event.shiftKey || !cell) {
    return;
  }

  if (event.key === 'ArrowUp' && cell.caretLine().first) {
    event.preventDefault();

    if (row > 0) {
      focusCell(row - 1, col, 'end');
    } else {
      emit('leave', -1);
    }

    return;
  }

  if (event.key === 'ArrowDown' && cell.caretLine().last) {
    event.preventDefault();

    if (row < lastRow.value) {
      focusCell(row + 1, col, 'end');
    } else {
      emit('leave', 1);
    }

    return;
  }

  if (event.key === 'ArrowLeft' && cell.isCaretAtStart() && window.getSelection()?.isCollapsed) {
    const target = nextCell(row, col, -1);

    if (target) {
      event.preventDefault();
      focusCell(target.row, target.col, 'end');
    }

    return;
  }

  if (event.key === 'ArrowRight' && cell.isCaretAtEnd() && window.getSelection()?.isCollapsed) {
    const target = nextCell(row, col, 1);

    if (target) {
      event.preventDefault();
      focusCell(target.row, target.col, 'start');
    }
  }
}

/**
 * 칸에 붙여넣기는 글자만 받는다. 줄바꿈은 띄어쓰기로 바꾼다 — 칸은 한 줄이다.
 * 편집기가 여러 줄 글을 블록으로 풀어 표 아래에 넣지 않게 여기서 멈춘다.
 */
function onCellPaste(event) {
  const text = event.clipboardData?.getData('text/plain') ?? '';
  event.preventDefault();
  event.stopPropagation();
  document.execCommand('insertText', false, text.replace(/\s*\n+\s*/g, ' '));
}

/* ── 손잡이 메뉴 ─────────────────────────── */

async function openMenu(kind, index, event) {
  const rect = event.currentTarget.getBoundingClientRect();

  menu.kind = kind;
  menu.index = index;
  menu.top = kind === 'row' ? rect.top : rect.bottom + 4;
  menu.left = kind === 'row' ? rect.right + 4 : rect.left;
  menu.active = 0;

  if (kind === 'row') {
    focused.row = index;
  } else {
    focused.col = index;
  }

  await nextTick();
  rootRef.value?.querySelector('.block-table__menu [role^="menuitem"]')?.focus();
}

function closeMenu(refocus = true) {
  const { kind, index } = menu;
  menu.kind = '';

  if (refocus && kind) {
    focusCell(kind === 'row' ? index : Math.max(focused.row, 0), kind === 'col' ? index : Math.max(focused.col, 0));
  }
}

function runMenu(item) {
  if (item.disabled) {
    return;
  }

  const { kind, index } = menu;
  const row = kind === 'row' ? index : Math.max(focused.row, 0);
  const col = kind === 'col' ? index : Math.max(focused.col, 0);

  menu.kind = '';

  const actions = {
    'row-above': () => focusCell(addRow(index), col, 'start'),
    'row-below': () => focusCell(addRow(index + 1), col, 'start'),
    'row-remove': () => {
      removeRow(index);
      focusCell(Math.min(index, props.block.rows.length - 2), col);
    },
    header: () => {
      update({ header: !props.block.header });
      focusCell(row, col);
    },
    'col-left': () => {
      addColumn(index);
      focusCell(row, index, 'start');
    },
    'col-right': () => {
      addColumn(index + 1);
      focusCell(row, index + 1, 'start');
    },
    'col-remove': () => {
      removeColumn(index);
      focusCell(row, Math.min(index, columnCount.value - 2));
    },
  };

  if (item.id.startsWith('align-')) {
    setAlign(index, item.id.slice('align-'.length));
    focusCell(row, col);
    return;
  }

  actions[item.id]?.();
}

function onMenuKeydown(event) {
  const items = menuItems.value;

  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    closeMenu();
    return;
  }

  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    menu.active = (menu.active + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    rootRef.value?.querySelectorAll('.block-table__menu [role^="menuitem"]')[menu.active]?.focus();
    return;
  }

  if (event.key === 'Tab') {
    event.preventDefault();
    closeMenu();
  }
}

function onMenuFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) {
    closeMenu(false);
  }
}

function onFocusIn(row, col) {
  focused.row = row;
  focused.col = col;
}

function onTableLeave() {
  hover.row = -1;
  hover.col = -1;
}

function onCellEnter(row, col) {
  hover.row = row;
  hover.col = col;
}

defineExpose({
  focus,
  focusCell,
  /** 서식 막대가 고른 칸. data-cell 의 "줄:열" 로 찾는다 */
  cell: (key) => cellRefs.get(key) ?? null,
  /** 서식 막대로 칸 서식을 바꾼 뒤 바뀐 글을 값에 올린다 */
  commit(key) {
    const [row, col] = key.split(':').map(Number);
    const cell = cellAt(row, col);

    if (cell) {
      setCell(row, col, cell.readText());
    }
  },
});
</script>

<template>
  <div ref="rootRef" class="block-table" @mouseleave="onTableLeave">
    <div class="block-table__scroll">
      <div class="block-table__frame">
        <table class="block-table__grid">
          <tbody>
            <tr
              v-for="(cells, row) in block.rows"
              :key="row"
              :class="{
                'block-table__row--header': block.header && row === 0,
                'block-table__row--active': activeRow === row,
              }"
            >
              <td
                v-for="col in columnCount"
                :key="col"
                class="block-table__cell"
                :class="{ 'block-table__cell--active-col': activeCol === col - 1 }"
                :data-cell="cellKey(row, col - 1)"
                :style="block.columnAlign[col - 1] ? { textAlign: block.columnAlign[col - 1] } : null"
                @mouseenter="onCellEnter(row, col - 1)"
                @focusin="onFocusIn(row, col - 1)"
              >
                <button
                  v-if="col === 1"
                  class="block-table__row-handle"
                  type="button"
                  tabindex="-1"
                  :aria-label="`${row + 1}번째 줄 메뉴`"
                  aria-haspopup="menu"
                  :aria-expanded="menu.kind === 'row' && menu.index === row"
                  @mousedown.prevent
                  @click="openMenu('row', row, $event)"
                >⋮⋮</button>
                <button
                  v-if="row === 0"
                  class="block-table__col-handle"
                  type="button"
                  tabindex="-1"
                  :aria-label="`${col}번째 열 메뉴`"
                  aria-haspopup="menu"
                  :aria-expanded="menu.kind === 'col' && menu.index === col - 1"
                  @mousedown.prevent
                  @click="openMenu('col', col - 1, $event)"
                >⋯</button>
                <AdminBlockText
                  :ref="(element) => setCellRef(row, col - 1, element)"
                  class="block-table__text"
                  :model-value="cells[col - 1] ?? ''"
                  @update:model-value="setCell(row, col - 1, $event)"
                  @keydown="onCellKeydown(row, col - 1, $event)"
                  @paste="onCellPaste"
                />
              </td>
            </tr>
          </tbody>
        </table>

        <button
          class="block-table__add block-table__add--col"
          type="button"
          tabindex="-1"
          aria-label="표에 열 추가"
          title="열 추가"
          @mousedown.prevent
          @click="appendColumn(Math.max(focused.row, 0))"
        >+</button>
        <button
          class="block-table__add block-table__add--row"
          type="button"
          tabindex="-1"
          aria-label="표에 줄 추가"
          title="줄 추가"
          @mousedown.prevent
          @click="appendRow(Math.max(focused.col, 0))"
        >+</button>
      </div>
    </div>

    <div
      v-if="menu.kind"
      class="block-table__menu"
      role="menu"
      :aria-label="menu.kind === 'row' ? '줄 메뉴' : '열 메뉴'"
      :style="{ top: `${menu.top}px`, left: `${menu.left}px` }"
      @keydown="onMenuKeydown"
      @focusout="onMenuFocusOut"
    >
      <button
        v-for="item in menuItems"
        :key="item.id"
        class="block-table__menu-item"
        :class="{ 'block-table__menu-item--danger': item.danger }"
        type="button"
        :role="item.checked === undefined ? 'menuitem' : 'menuitemcheckbox'"
        :aria-checked="item.checked === undefined ? undefined : item.checked"
        :aria-disabled="item.disabled ? 'true' : undefined"
        :disabled="item.disabled"
        @mousedown.prevent
        @click="runMenu(item)"
      >
        <span>{{ item.label }}</span>
        <span v-if="item.checked" class="block-table__menu-check" aria-hidden="true">✓</span>
      </button>
    </div>
  </div>
</template>
