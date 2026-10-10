<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue';

import {
  canNest,
  createBlock,
  isEmptyBlock,
  isMultiline,
  moveBlockRange,
  normalizeIndents,
  subtreeEnd,
  toEditorBlocks,
  toMarkdown,
} from '../data/postEditorBlocks';
import {
  applyMarkdownShortcut,
  bulletMarker,
  isListType,
  orderedLabel,
  orderedNumberAt,
} from '../data/postEditorShortcuts';
import { useBlockSelection } from '../composables/useBlockSelection';
import { useEditorImages } from '../composables/useEditorImages';
import { useEditorKeyboard } from '../composables/useEditorKeyboard';
import { useSlashMenu } from '../composables/useSlashMenu';
import { useTextToolbar } from '../composables/useTextToolbar';
import { POST_SLASH_COMMANDS } from '../data/postSlashCommands';
import { createEditorHistory } from '../data/postEditorHistory';
import { CODE_LANGUAGES, toCodeTokens } from '../../../shared/post/codeHighlight';
import AdminBlockText from './AdminBlockText.vue';
import AdminBlockImage from './AdminBlockImage.vue';
import AdminSlashMenu from './AdminSlashMenu.vue';
import AdminTextToolbar from './AdminTextToolbar.vue';
import AdminBlockTable from './AdminBlockTable.vue';
import AdminBlockMenu from './AdminBlockMenu.vue';

/**
 * 쓰는 자리가 곧 결과인 블록 에디터.
 *
 * 미리보기가 따로 있는 구조가 아니다. 글을 쓰면 그 자리에서 블록이 되고,
 * 보이는 모습이 발행 결과와 같다.
 *
 * 글자 블록은 contenteditable(AdminBlockText), 코드 블록만 textarea 다.
 * 코드는 서식이 없고 색칠 층을 겹쳐야 해서 입력 요소가 단순한 편이 낫다.
 */
const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['update:modelValue']);

const blocks = ref(toEditorBlocks(props.modelValue));
const editorRef = ref(null);
const textRefs = new Map();
const codeRefs = new Map();
const tableRefs = new Map();

const {
  uploadErrors,
  resizingId,
  isUploading,
  pickImageFor,
  onPaste,
  imageFilesOf,
  insertImage,
  setImageAlign,
  onResizeStart,
  onResizeKeydown,
} = useEditorImages({ blocks, sync });
const selection = useBlockSelection({
  blocks,
  editorRef,
  sync,
  isUploading,
  // 접힌 토글 안쪽은 화면에 없으므로 화살표·삽입선이 그 자리에 서지 않는다
  isHidden: (id) => hiddenIds.value.has(id),
});
const {
  draggingId,
  dropIndex,
  selectedIds,
  keyboardInsertIndex,
  toggleSelect,
  clearSelection,
  focusEditor,
  removeSelectedBlocks,
  moveSelectedBlocks,
  duplicateSelected,
  withDescendants,
  selectImageBlock,
  onBlockCopy,
  onBlockCut,
  onBlockPaste,
  onDragStart,
  onDragOver,
  onDrop,
  resetDrag,
} = selection;
const {
  menuOpenId,
  menuIndex,
  detectSlash,
  closeMenu,
  commandsFor,
  handleMenuKey,
} = useSlashMenu({ textRefs, runCommand });
const { onEditorKeydown } = useEditorKeyboard({ editorRef, selection, undo, redo, focusBlock });
const { toolbar, refreshToolbar, startLink, restoreSelection } = useTextToolbar({ editorRef });

// 서식 막대의 "블록 바꾸기" 목록. / 메뉴와 같은 이름·순서를 쓴다
const TURN_OPTIONS = [
  { id: 'paragraph', label: '텍스트' },
  ...POST_SLASH_COMMANDS
    .filter((command) => command.kind !== 'inline' && !['image', 'divider', 'table'].includes(command.id))
    .map((command) => ({ id: command.id, label: command.label })),
];

const CALLOUT_LABELS = { tip: '팁', warning: '주의', note: '참고' };

/**
 * 비어 있는 블록에 보이는 안내. 그 자리에 무엇을 쓰는지 알려 준다.
 * 문단 안내는 커서가 있는 줄과 글이 하나도 없을 때만 보인다(스타일에서 거른다)
 */
function placeholderFor(block) {
  if (block.type === 'heading') {
    return `제목 ${block.level}`;
  }

  if (block.type === 'bullet' || block.type === 'ordered') {
    return '목록';
  }

  if (block.type === 'todo') {
    return '할 일';
  }

  if (block.type === 'toggle') {
    return block.toggleLevel ? `제목 토글 ${block.toggleLevel}` : '토글';
  }

  if (block.type === 'quote') {
    return '인용';
  }

  if (block.type === 'callout') {
    return `${CALLOUT_LABELS[block.variant]} 내용`;
  }

  return '글을 쓰거나 / 를 눌러 블록을 고르세요';
}

/* ── 토글 ─────────────────────────────────── */

/** 접힌 토글 안에 있어 화면에서 숨은 블록들 */
const hiddenIds = computed(() => {
  const hidden = new Set();
  let closedIndent = null;

  blocks.value.forEach((block) => {
    const indent = block.indent ?? 0;

    if (closedIndent !== null && indent > closedIndent) {
      hidden.add(block.id);
      return;
    }

    closedIndent = block.type === 'toggle' && !block.open ? indent : null;
  });

  return hidden;
});

// 고른 블록에 딸린 안쪽 블록도 같이 고른 것으로 칠한다. 같이 옮겨지고 지워지기 때문이다
const selectedRowIds = computed(() => (selectedIds.value.length ? withDescendants() : new Set()));

/** 토글을 접고 편다. 접은 토글 안에 커서가 있었으면 커서를 토글 제목으로 옮긴다 */
function toggleOpen(block) {
  block.open = !block.open;

  if (!block.open && hiddenIds.value.has(activeId)) {
    focusBlock(block.id, 'end');
  }
}

function hasChildren(index) {
  return subtreeEnd(blocks.value, index) > index + 1;
}

/** 빈 토글의 안내 줄을 누르면 안쪽 첫 줄을 만들고 커서를 둔다 (노션과 같다) */
function addToggleChild(index) {
  const toggle = blocks.value[index];
  const child = createBlock('paragraph', { indent: (toggle.indent ?? 0) + 1 });

  blocks.value.splice(index + 1, 0, child);
  sync();
  focusBlock(child.id, 'start');
}

/* ── 되돌리기 ─────────────────────────────── */

const history = createEditorHistory();

// 커서가 어느 블록에 있는지. 되돌린 뒤 커서를 제자리에 놓는 데 쓴다
let activeId = '';

/** 되돌리기에 쌓을 한 장면. 블록은 평평한 객체라 한 겹 복사로 온전히 떠진다 */
function snapshot() {
  return {
    blocks: blocks.value.map((block) => ({ ...block })),
    focusId: activeId,
    caret: caretOffset(),
  };
}

function caretOffset() {
  const text = textRefs.get(activeId);

  if (text) {
    return text.caretOffset();
  }

  const code = codeRefs.get(activeId);

  return code ? code.selectionStart : 0;
}

// 바뀌기 직전 장면. sync() 는 이미 바뀐 뒤에 불리므로 직전 상태를 따로 들고 있어야 한다
let previous = snapshot();

function onFocusIn(event) {
  const row = event.target.closest('[data-block-id]');

  if (row) {
    activeId = row.dataset.blockId;
  }

  // 글자를 다시 누르면 블록 선택 모드를 끝낸다. 핸들은 click에서 선택을 다시 만든다
  if (row && !event.target.closest('.block-editor__handle')) {
    clearSelection();
    keyboardInsertIndex.value = -1;
  }
}

/**
 * @param mergeKey 같은 값이 이어지는 동안 되돌리기 한 번으로 묶는다.
 *                 글자 입력만 넘기고 구조가 바뀌는 일은 비워 둔다
 */
function sync(mergeKey = '') {
  normalizeIndents(blocks.value);
  history.record(previous, mergeKey);
  previous = snapshot();

  emit('update:modelValue', toMarkdown(blocks.value));
}

function undo() {
  apply(history.undo(snapshot()));
}

function redo() {
  apply(history.redo(snapshot()));
}

function apply(entry) {
  if (!entry) {
    return;
  }

  // 쌓아 둔 장면은 그대로 둔다. 되돌린 뒤 글을 고치면 기록까지 바뀌어 버린다
  blocks.value = entry.blocks.map((block) => ({ ...block }));
  previous = { ...entry, blocks: blocks.value.map((block) => ({ ...block })) };

  closeMenu();
  clearSelection();

  emit('update:modelValue', toMarkdown(blocks.value));

  // 사라졌던 블록이 돌아온 경우처럼, 그 블록이 지금 없으면 커서는 그대로 둔다
  if (blocks.value.some((block) => block.id === entry.focusId)) {
    focusBlock(entry.focusId, entry.caret);
  }
}

/* ── 바깥에서 들어온 값 ───────────────────── */

watch(() => props.modelValue, (next) => {
  if (next === toMarkdown(blocks.value)) {
    return;
  }

  blocks.value = toEditorBlocks(next);

  // 다른 글을 불러왔거나 보관본을 되살린 것이다.
  // 기록을 남겨 두면 Cmd+Z 가 앞 글의 내용을 끌어온다
  history.reset();
  previous = snapshot();
});

function setTextRef(id, element) {
  if (element) {
    textRefs.set(id, element);
  } else {
    textRefs.delete(id);
  }
}

function setTableRef(id, element) {
  if (element) {
    tableRefs.set(id, element);
  } else {
    tableRefs.delete(id);
  }
}

function setCodeRef(id, element) {
  if (element) {
    codeRefs.set(id, element);

    // 한 틱 뒤에 잰다. ref 콜백은 값이 칸에 들어가기 전에 불려서, 그 자리에서 재면
    // scrollHeight 가 0 이고 height: 0px 이 그대로 굳는다 — 코드는 들어 있는데
    // 언어 고르는 줄만 보이던 것이 이 때문이다
    nextTick(() => autoGrow(element));
  } else {
    codeRefs.delete(id);
  }
}

function autoGrow(element) {
  if (!element) {
    return;
  }

  element.style.height = 'auto';
  element.style.height = `${element.scrollHeight}px`;
}

/** @param position 'start' · 'end' · 앞에서부터 센 글자 수 */
async function focusBlock(id, position = 'end') {
  await nextTick();

  const text = textRefs.get(id);
  const table = tableRefs.get(id);

  if (text) {
    text.focus(position);
  } else if (table) {
    table.focus(position === 'start' ? 'start' : 'end');
  } else {
    const code = codeRefs.get(id);

    if (!code) {
      return;
    }

    code.focus();

    const caret = codeCaret(code, position);
    code.setSelectionRange(caret, caret);
  }

  // 커서를 옮긴 자리가 지금 장면의 커서 자리다.
  // 이걸 적어 둬야 되돌아왔을 때 쓰던 자리에 커서가 놓인다
  previous.focusId = id;
  previous.caret = caretOffset();
}

function codeCaret(code, position) {
  if (position === 'start') {
    return 0;
  }

  if (typeof position === 'number') {
    // 되돌린 뒤라 글이 짧아졌을 수 있다
    return Math.min(position, code.value.length);
  }

  return code.value.length;
}

/* ── 코드 블록 ────────────────────────────── */

function codeTokens(block) {
  return toCodeTokens(block.text, block.language);
}

function syncScroll(event) {
  const highlight = event.target.previousElementSibling;

  if (highlight) {
    highlight.scrollTop = event.target.scrollTop;
    highlight.scrollLeft = event.target.scrollLeft;
  }
}

function onCodeInput(block, event) {
  block.text = event.target.value;
  autoGrow(event.target);
  sync(`text:${block.id}`);
}

/* ── 글자 블록 ────────────────────────────── */

function onTextInput(block) {
  // 마크다운을 직접 쳐도 같은 결과가 되게 한다
  if (applyMarkdownShortcut(block)) {
    // 구분선은 커서가 들어갈 자리가 없다. 바로 아래 줄에서 이어 쓰게 한다
    if (block.type === 'divider') {
      continueAfter(blocks.value.indexOf(block));
      return;
    }

    sync();
    finishShortcut(block);
    return;
  }

  detectSlash(block);
  sync(`text:${block.id}`);
}

/**
 * 표기를 지우고 서식으로 바꾼 뒤 화면을 맞춘다.
 *
 * "## " 가 한 번에 들어오면(붙여넣기·자동 수정) 값이 "제목" → "## 제목" → "제목" 으로
 * 갔다 돌아온 꼴이 된다. 감시자는 끝 값만 보기 때문에 바뀐 줄 모르고,
 * 그러면 화면에만 "## " 가 남는다. 그 경우를 여기서 맞춰 준다.
 */
async function finishShortcut(block) {
  await nextTick();
  textRefs.get(block.id)?.redraw();

  focusBlock(block.id, 'start');
}

/* ── 키보드 ───────────────────────────────── */

function onKeydown(block, index, event) {
  // 한글 조합을 확정하려고 누른 Enter 가 새 블록 생성으로 새지 않게 한다.
  // keyCode 229 는 조합 중임을 알리는 옛 브라우저의 표시다
  if (event.isComposing || event.keyCode === 229) {
    return;
  }

  if (menuOpenId.value === block.id && handleMenuKey(block, event)) {
    return;
  }

  // 선택한 글자의 굵게·기울임·코드를 켜고 끈다(노션과 같은 단축키). 고른 글자는 그대로 골라 둔다
  // ⌘B 굵게 · ⌘I 기울임 · ⌘U 밑줄 · ⌘E 코드 · ⌘⇧S 취소선
  const formatKeys = { b: 'strong', i: 'em', u: 'u', e: 'code' };
  const command = (event.metaKey || event.ctrlKey) && !event.altKey;
  const shortcut = command && !event.shiftKey ? event.key.toLowerCase() : '';
  const format = formatKeys[shortcut] ?? (command && event.shiftKey && event.key.toLowerCase() === 's' ? 's' : '');

  if (format) {
    event.preventDefault();
    textRefs.get(block.id)?.toggleFormat(format);
    sync();
    refreshToolbar();
    return;
  }

  // ⌘⇧H: 고른 글자에 마지막으로 쓴 색을 칠한다
  if (command && event.shiftKey && event.key.toLowerCase() === 'h') {
    event.preventDefault();

    if (textRefs.get(block.id)?.applyColor(lastColor)) {
      sync();
      refreshToolbar();
    }

    return;
  }

  // 링크는 주소를 받아야 해서 서식 막대의 입력칸을 연다
  if (shortcut === 'k') {
    event.preventDefault();
    startLink();
    return;
  }

  // 노션과 같은 블록 단축키: ⌘D 복제, ⌘⇧↑·↓ 옮기기
  if (shortcut === 'd') {
    event.preventDefault();
    duplicateBlockAt(index);
    return;
  }

  if ((event.metaKey || event.ctrlKey) && event.shiftKey && !event.altKey
    && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
    event.preventDefault();
    moveBlockAt(index, event.key === 'ArrowUp' ? -1 : 1);
    return;
  }

  // Tab 은 편집기 밖으로 나가지 않는다. 목록은 들여쓰기, 코드는 공백 두 칸이다 (노션과 같다)
  if (event.key === 'Tab' && !event.metaKey && !event.ctrlKey && !event.altKey) {
    event.preventDefault();

    if (block.type === 'code') {
      indentCode(block, event.target, event.shiftKey);
    } else {
      changeIndent(index, event.shiftKey ? -1 : 1);
    }

    return;
  }

  const isCode = block.type === 'code';
  const element = textRefs.get(block.id);

  const atStart = isCode ? event.target.selectionStart === 0 : element?.isCaretAtStart();
  const atEnd = isCode
    ? event.target.selectionStart === event.target.value.length
    : element?.isCaretAtEnd();

  if (event.key === 'Enter' && !isCode && onTextEnter(block, index, event, atEnd)) {
    return;
  }

  if (event.key === 'Backspace' && atStart) {
    // 들여 쓴 블록(목록 하위 항목·토글 안 블록)은 맨 앞 Backspace 로 한 단계 내민다
    if (!isCode && (block.indent ?? 0) > 0) {
      event.preventDefault();
      changeIndent(index, -1);
      return;
    }

    // 서식만 있는 빈 블록, 그리고 맨 바깥 목록 항목·토글은 한 번 눌러 문단으로 되돌린다 (글은 그대로)
    if (block.type !== 'paragraph'
      && (block.text === '' || (!isCode && (isListType(block.type) || block.type === 'toggle')))) {
      event.preventDefault();
      applyBlockType(block, 'paragraph');
      sync();
      return;
    }

    if (index > 0 && block.text === '') {
      event.preventDefault();
      removeBlock(index);
      return;
    }

    // 글이 있는 줄 맨 앞: 윗줄 끝에 붙인다 (노션과 같다). 코드·구분선·이미지와는 합치지 않는다
    if (!isCode && isMergeable(block) && isMergeable(blocks.value[index - 1])) {
      event.preventDefault();
      mergeInto(index - 1, index);
      return;
    }

    // 바로 위가 표면 합치지 않고 표의 마지막 줄로 커서만 옮긴다
    if (!isCode && blocks.value[index - 1]?.type === 'table') {
      event.preventDefault();
      focusBlock(blocks.value[index - 1].id, 'end');
      return;
    }
  }

  // 글이 있는 줄 끝에서 Delete: 아랫줄을 끌어와 붙인다
  if (event.key === 'Delete' && atEnd && !isCode && isMergeable(block) && isMergeable(blocks.value[index + 1])) {
    event.preventDefault();
    mergeInto(index, index + 1);
    return;
  }

  const plainArrow = (event.key === 'ArrowUp' || event.key === 'ArrowDown')
    && !event.shiftKey && !event.altKey && !event.metaKey && !event.ctrlKey;

  if (plainArrow) {
    moveAcrossBlocks(block, index, event, isCode, element);
  }
}

/**
 * 글자 블록에서 Enter. 처리했으면 true.
 *
 * - 한 줄 블록(문단·제목·목록·인용): 커서 자리에서 블록을 나눈다. Shift+Enter 는 같은 블록 안 줄바꿈이다.
 *   다만 제목·목록은 마크다운에서 한 줄이어야 해서 Shift+Enter 도 나눈다
 * - 여러 줄 블록(콜아웃): 줄을 바꾼다. 마지막 빈 줄에서 한 번 더 누르면 블록을 빠져나와 아래에 문단을 만든다
 */
function onTextEnter(block, index, event, atEnd) {
  const element = textRefs.get(block.id);
  const singleLine = block.type === 'heading' || block.type === 'bullet' || block.type === 'ordered';

  if (isMultiline(block)) {
    event.preventDefault();

    if (atEnd && (block.text === '' || block.text.endsWith('\n'))) {
      // 화면은 값이 바뀌면 블록이 다시 그린다
      block.text = block.text.replace(/\n$/, '');
      continueAfter(index);
      return true;
    }

    element?.insertLineBreak();
    return true;
  }

  if (event.shiftKey && !singleLine) {
    event.preventDefault();
    element?.insertLineBreak();
    return true;
  }

  event.preventDefault();
  splitBlock(block, index);
  return true;
}

/**
 * 위·아래 키. 커서가 화면에서 그 블록의 첫 줄·끝 줄에 있으면 옆 블록으로 넘어간다.
 * 글자 끝까지 가야 넘어가게 하면, 한 줄짜리 문단에서도 아래 키를 두 번 눌러야 한다.
 */
function moveAcrossBlocks(block, index, event, isCode, element) {
  const down = event.key === 'ArrowDown';
  let leave = false;
  let x = null;

  if (isCode) {
    const { value, selectionStart } = event.target;
    leave = down ? !value.slice(selectionStart).includes('\n') : !value.slice(0, selectionStart).includes('\n');
  } else if (element) {
    const line = element.caretLine();
    leave = down ? line.last : line.first;
    x = line.x;
  }

  if (!leave) {
    return;
  }

  const target = writableIndexFrom(index, down ? 1 : -1);

  if (target < 0) {
    return;
  }

  event.preventDefault();
  focusNeighbor(blocks.value[target], x, down ? 'first' : 'last');
}

/**
 * Enter 를 누른 자리에서 블록을 나눈다.
 * 문단 중간에서 눌러도 앞뒤가 갈라져야 한다 — 뒤에 빈 블록만 붙으면
 * 쓰던 문장을 손으로 잘라 옮겨야 한다.
 */
function splitBlock(block, index) {
  // 빈 목록 항목에서 Enter: 들여 쓴 항목은 한 단계 내밀고, 맨 바깥이면 목록을 끝낸다 (노션과 같다)
  if (isListType(block.type) && block.text.trim() === '') {
    if ((block.indent ?? 0) > 0) {
      changeIndent(index, -1);
    } else {
      applyBlockType(block, 'paragraph');
      sync();
    }

    focusBlock(block.id);
    return;
  }

  // 토글·안쪽 블록의 빈 줄에서 Enter: 한 단계 내민다 (빠져나가는 길)
  if (!isListType(block.type) && block.type !== 'toggle' && block.text.trim() === '' && (block.indent ?? 0) > 0) {
    changeIndent(index, -1);
    focusBlock(block.id);
    return;
  }

  const parts = textRefs.get(block.id)?.splitAtCaret();
  const before = parts ? parts.before : block.text;
  const after = parts ? parts.after : '';

  block.text = before;

  // 목록 안에서는 같은 종류·같은 깊이가 이어진다. 새 할 일은 완료 전이다.
  // 토글 제목에서는: 펼쳐져 있으면 안쪽 첫 줄, 접혀 있으면 토글 다음 줄이 된다 (노션과 같다)
  const list = isListType(block.type);
  const depth = block.indent ?? 0;
  const intoToggle = block.type === 'toggle' && block.open;
  const next = createBlock(list ? block.type : 'paragraph', { text: after, indent: intoToggle ? depth + 1 : depth });
  const at = block.type === 'toggle' && !block.open ? subtreeEnd(blocks.value, index) : index + 1;

  blocks.value.splice(at, 0, next);
  sync();
  focusBlock(next.id, 'start');
}

function insertAfter(index, block) {
  blocks.value.splice(index + 1, 0, block);
  sync();
  focusBlock(block.id);
}

function removeBlock(index) {
  const above = blocks.value[index - 1];
  blocks.value.splice(index, 1);
  sync();

  if (above) {
    focusBlock(above.id);
  }
}

// / 메뉴의 글자 서식 명령이 넣는 태그
const INLINE_COMMAND_TAGS = {
  bold: { tag: 'strong' },
  italic: { tag: 'em' },
  'inline-code': { tag: 'code' },
  link: { tag: 'a', href: 'https://' },
};

/**
 * / 메뉴에서 고른 명령을 적용한다.
 *
 * 지우는 것은 커서 앞에 친 "/명령" 뿐이다. 그 앞뒤에 쓰던 글은 그대로 둔다 —
 * 문장 중간에서 /h1 을 고르면 문장 전체가 제목이 되고, 뒤 글이 사라지면 안 된다.
 */
function runCommand(block, command) {
  if (!command) {
    return;
  }

  const index = blocks.value.indexOf(block);
  const element = textRefs.get(block.id);
  const typed = element?.textBeforeCaret().match(/\/(\S*)$/);

  if (element && typed) {
    element.removeBeforeCaret(typed[0].length);
  }

  // "/명령" 이 줄 끝에 있었으면 그 앞 띄어쓰기도 정리한다. 문장 중간이면 앞뒤 말이 붙지 않게 둔다
  const commandAtEnd = element ? element.isCaretAtEnd() : true;

  closeMenu();

  const caret = element ? element.caretOffset() : 'end';
  const hasText = block.text.trim() !== '';

  // 이미지·구분선을 아래에 따로 만들 때 "앞 문장 /hr" 의 띄어쓰기가 문단 끝에 남지 않게 한다
  const trimEnd = () => {
    block.text = block.text.replace(/[ \t]+$/, '');
  };

  if (command.kind === 'inline') {
    const { tag, href } = INLINE_COMMAND_TAGS[command.id];

    // 자리 글자를 골라 둔 채로 둔다. 바로 치면 그 글자가 바뀐다
    element?.insertFormatted(tag, command.placeholder, href);
    sync();
    return;
  }

  // 이미지·구분선은 글자를 담지 못한다. 쓰던 글이 있으면 그 블록은 두고 아래에 새로 만든다
  if (command.id === 'image') {
    const target = hasText ? createBlock('image') : block;

    if (hasText) {
      trimEnd();
      blocks.value.splice(index + 1, 0, target);
    } else {
      block.type = 'image';
      block.text = '';
    }

    sync();
    // 주소를 손으로 적게 하면 올리는 일이 화면 밖으로 새어 나간다. 파일 고르기를 바로 띄운다
    pickImageFor(target);
    return;
  }

  // 표: 빈 줄이면 그 자리가 표가 되고, 쓰던 글이 있으면 아래에 새로 만든다. 첫 칸에서 바로 쓴다
  if (command.id === 'table') {
    const table = createBlock('table', { rows: newTableRows(), indent: block.indent ?? 0 });

    if (hasText) {
      trimEnd();
      blocks.value.splice(index + 1, 0, table);
    } else {
      blocks.value.splice(index, 1, table);
    }

    sync();
    focusBlock(table.id, 'start');
    return;
  }

  if (command.id === 'divider') {
    let dividerIndex = index;

    if (hasText) {
      trimEnd();
      dividerIndex = index + 1;
      blocks.value.splice(dividerIndex, 0, createBlock('divider'));
    } else {
      block.type = 'divider';
      block.text = '';
    }

    continueAfter(dividerIndex);
    return;
  }

  if (commandAtEnd) {
    trimEnd();
  }

  applyBlockType(block, command.id);
  sync();
  // 블록 종류만 바뀌었다. 커서는 고르기 전 자리에 그대로 둔다
  focusBlock(block.id, caret);
}

/** 블록 종류만 바꾼다. 글은 그대로 둔다 (/ 메뉴와 서식 막대의 "블록 바꾸기" 가 같이 쓴다) */
function applyBlockType(block, id) {
  const index = blocks.value.indexOf(block);
  const wasToggle = block.type === 'toggle';

  if (id === 'paragraph') {
    block.type = 'paragraph';
  } else if (/^h[1-6]$/.test(id)) {
    block.type = 'heading';
    block.level = Number(id.slice(1));
  } else if (/^toggle(-h[1-3])?$/.test(id)) {
    block.type = 'toggle';
    block.toggleLevel = id === 'toggle' ? 0 : Number(id.slice(-1));
  } else if (['bullet', 'ordered', 'todo', 'quote', 'code'].includes(id)) {
    block.type = id;
  } else {
    block.type = 'callout';
    block.variant = id;
  }

  // 안쪽 블록을 품을 수 없는 종류가 됐으면 딸린 블록을 한 단계 꺼낸다. 글은 그대로 남는다
  // (토글은 무엇이든, 목록 항목은 목록만 품는다)
  const keepsChildren = block.type === 'toggle' || (isListType(block.type) && !wasToggle);

  if (index >= 0 && !keepsChildren) {
    const end = subtreeEnd(blocks.value, index);

    for (let cursor = index + 1; cursor < end; cursor += 1) {
      blocks.value[cursor].indent = Math.max((blocks.value[cursor].indent ?? 0) - 1, 0);
    }
  }

  if (block.type === 'toggle') {
    block.open = true;
  }
}

/** 서식 막대가 "지금 종류" 로 보여 줄 값. TURN_OPTIONS 의 id 와 같은 꼴이다 */
function blockTypeId(block) {
  if (!block) {
    return 'paragraph';
  }

  if (block.type === 'heading') {
    return `h${block.level}`;
  }

  if (block.type === 'toggle' && block.toggleLevel) {
    return `toggle-h${block.toggleLevel}`;
  }

  return block.type === 'callout' ? block.variant : block.type;
}

/* ── 서식 막대 ────────────────────────────── */

function toolbarBlock() {
  return blocks.value.find((block) => block.id === toolbar.blockId) ?? null;
}

/** 서식 막대가 서식을 걸 글자 칸. 표 안이면 고른 칸이다 */
function toolbarText() {
  return toolbar.cellKey
    ? tableRefs.get(toolbar.blockId)?.cell(toolbar.cellKey)
    : textRefs.get(toolbar.blockId);
}

/** 서식을 바꾼 뒤 값을 맞춘다. 표 칸은 표가 바뀐 칸 배열을 올려 보낸다 */
function commitToolbarText() {
  if (toolbar.cellKey) {
    tableRefs.get(toolbar.blockId)?.commit(toolbar.cellKey);
  } else {
    sync();
  }
}

function onToolbarFormat(tag) {
  toolbarText()?.toggleFormat(tag);
  commitToolbarText();
  refreshToolbar();
}

function onToolbarTurn(id) {
  const block = toolbarBlock();

  if (!block) {
    return;
  }

  applyBlockType(block, id);
  sync();

  // 코드는 입력 요소가 바뀌어 고른 구간을 이어 갈 수 없다. 끝에 커서를 둔다
  if (block.type === 'code') {
    focusBlock(block.id, 'end');
  }

  refreshToolbar();
}

function onLinkApply(href) {
  if (!restoreSelection()) {
    return;
  }

  toolbarText()?.applyLink(href);
  commitToolbarText();
  refreshToolbar();
}

// ⌘⇧H 가 칠할 색. 마지막으로 고른 색이고, 처음에는 노란 형광펜이다 (노션과 같다)
let lastColor = 'yellow-bg';

function onToolbarColor(color) {
  toolbarText()?.applyColor(color);
  commitToolbarText();

  if (color) {
    lastColor = color;
  }

  refreshToolbar();
}

function onLinkCancel() {
  if (!toolbar.linkEditing) {
    return;
  }

  restoreSelection();
  refreshToolbar();
}

/* ── 블록 손잡이 메뉴·+ 버튼 ──────────────── */

const blockMenu = reactive({ open: false, anchor: { top: 0, bottom: 0, left: 0, right: 0 } });

const BLOCK_NAMES = { image: '이미지', divider: '구분선', table: '표' };

/** 메뉴가 다룰 블록들 — 지금 고른 블록 전부다 (손잡이를 누르면 그 블록이 골라진다) */
function menuBlocks() {
  const selected = new Set(selectedIds.value);
  return blocks.value.filter((block) => selected.has(block.id));
}

function blockMenuTitle() {
  const targets = menuBlocks();

  if (targets.length !== 1) {
    return `${targets.length}개 블록`;
  }

  const [block] = targets;
  return BLOCK_NAMES[block.type] ?? TURN_OPTIONS.find((option) => option.id === blockTypeId(block))?.label ?? '';
}

/**
 * 손잡이를 누르면 그 블록을 고르고 메뉴를 연다(노션과 같다).
 * Shift·⌘ 를 누른 채면 여러 블록을 고르기만 한다.
 */
function onHandleClick(block, event) {
  const rect = event.currentTarget.getBoundingClientRect();
  const extending = event.shiftKey || event.metaKey || event.ctrlKey;

  toggleSelect(block, event);

  if (extending) {
    blockMenu.open = false;
    return;
  }

  blockMenu.anchor = { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right };
  blockMenu.open = true;
}

function closeBlockMenu() {
  blockMenu.open = false;
  focusEditor();
}

function onMenuTurn(id) {
  menuBlocks().filter(isWritable).forEach((block) => applyBlockType(block, id));
  sync();
  closeBlockMenu();
}

/** 고른 블록들의 글 전체에 색을 칠한다. 코드·표·이미지처럼 글자 칸이 아닌 블록은 건너뛴다 */
function onMenuColor(color) {
  menuBlocks().forEach((block) => textRefs.get(block.id)?.colorAll(color));

  if (color) {
    lastColor = color;
  }

  sync();
  closeBlockMenu();
}

function onMenuDuplicate() {
  blockMenu.open = false;
  duplicateSelected();
}

function onMenuMove(direction) {
  blockMenu.open = false;
  moveSelectedBlocks(direction);
}

function onMenuRemove() {
  blockMenu.open = false;
  removeSelectedBlocks();
}

/** + 를 누르면 그 블록 아래에, Option(Alt) 을 누른 채면 위에 빈 줄을 만든다 (노션과 같다) */
function addBlockNear(index, event) {
  const block = createBlock('paragraph');

  blocks.value.splice(event.altKey ? index : index + 1, 0, block);
  clearSelection();
  sync();
  focusBlock(block.id, 'start');
}

/** 글자를 쓰다가 ⌘D 로 지금 블록을 복제한다. 커서는 원래 블록에 그대로 둔다 */
function duplicateBlockAt(index) {
  const block = blocks.value[index];
  const end = subtreeEnd(blocks.value, index);
  // 안쪽 블록(토글 내용·하위 항목)까지 같이 복제한다
  const copies = blocks.value.slice(index, end).map((source) => {
    const copy = { ...source, previewUrl: '' };
    delete copy.id;
    return createBlock(source.type, copy);
  });
  const caret = caretOffset();

  blocks.value.splice(end, 0, ...copies);
  sync();
  focusBlock(block.id, caret);
}

/* ── 줄 합치기 ───────────────────────────── */

/** 글자를 이어 붙일 수 있는 블록. 코드(원문 그대로)·구분선·이미지는 아니다 */
function isMergeable(block) {
  // 토글 제목은 안쪽 블록을 품고 있어 다른 줄과 합치면 구조가 꼬인다
  return Boolean(block) && isWritable(block) && !['code', 'toggle', 'table'].includes(block.type);
}

/**
 * 아래 블록(from)의 글을 위 블록(into) 끝에 붙이고 아래 블록을 지운다.
 * 커서는 이어 붙인 자리에 둔다 — 노션에서 줄 맨 앞 Backspace 가 하는 일이다.
 */
function mergeInto(intoIndex, fromIndex) {
  const into = blocks.value[intoIndex];
  const from = blocks.value[fromIndex];
  // 커서 자리는 화면 글자 수로 센다(서식 기호 제외). 붙이기 전 위 블록의 글자 수가 이음매다
  const joinAt = (textRefs.get(into.id)?.$el?.textContent ?? '').replace(/\u200b/g, '').length;

  into.text = `${into.text}${from.text}`;
  blocks.value.splice(fromIndex, 1);
  sync();
  focusBlock(into.id, joinAt);
}

/* ── 목록 들여쓰기·할 일 ─────────────────── */

/**
 * 목록 항목을 한 단계 들이거나 내민다(Tab · Shift+Tab). 바로 위 항목보다 두 단계 이상 깊어지지 않는다.
 * 하위 항목도 함께 옮긴다 — 노션처럼 부모를 옮기면 딸린 항목이 따라간다.
 */
function changeIndent(index, step) {
  const block = blocks.value[index];
  const current = block.indent ?? 0;

  if (step > 0 && !canNest(previousSibling(index), block)) {
    return false;
  }

  const next = step > 0 ? current + 1 : Math.max(current - 1, 0);

  if (next === current) {
    return false;
  }

  const end = subtreeEnd(blocks.value, index);

  for (let cursor = index + 1; cursor < end; cursor += 1) {
    blocks.value[cursor].indent = Math.max((blocks.value[cursor].indent ?? 0) + (next - current), 0);
  }

  block.indent = next;
  sync();
  return true;
}

/** 같은 깊이의 바로 앞 블록. 사이에 낀 더 깊은 블록(그 앞 블록의 안쪽)은 건너뛴다 */
function previousSibling(index) {
  const indent = blocks.value[index].indent ?? 0;

  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const depth = blocks.value[cursor].indent ?? 0;

    if (depth === indent) {
      return blocks.value[cursor];
    }

    if (depth < indent) {
      return null;
    }
  }

  return null;
}


function toggleTodo(block) {
  block.checked = !block.checked;
  sync();
}

/** 목록 표시 글자. 깊이마다 모양이 바뀐다 (노션과 같다) */
function listMarker(block, index) {
  const depth = listDepth(index);

  return block.type === 'ordered' ? orderedLabel(orderedNumberAt(blocks.value, index), depth) : bulletMarker(depth);
}

/**
 * 목록 안에서의 깊이. 토글 안에 든 목록도 첫 단계는 0 이다 — 글머리 모양(•◦▪)은 목록끼리의
 * 깊이로 정한다 (노션·공개 화면과 같다)
 */
function listDepth(index) {
  let want = (blocks.value[index].indent ?? 0) - 1;
  let depth = 0;

  for (let cursor = index - 1; cursor >= 0 && want >= 0; cursor -= 1) {
    const block = blocks.value[cursor];
    const indent = block.indent ?? 0;

    if (indent > want) {
      continue;
    }

    if (indent < want || !isListType(block.type)) {
      break;
    }

    depth += 1;
    want -= 1;
  }

  return depth;
}

/** 코드 칸의 Tab: 커서 자리에 공백 두 칸. 여러 줄을 골랐으면 그 줄들 앞에 넣는다. Shift+Tab 은 뺀다 */
function indentCode(block, textarea, outdent) {
  const { value, selectionStart, selectionEnd } = textarea;

  if (!outdent && selectionStart === selectionEnd) {
    block.text = `${value.slice(0, selectionStart)}  ${value.slice(selectionStart)}`;
    textarea.value = block.text;
    textarea.setSelectionRange(selectionStart + 2, selectionStart + 2);
  } else {
    const lineStart = value.lastIndexOf('\n', selectionStart - 1) + 1;
    const lineEnd = value.indexOf('\n', selectionEnd);
    const end = lineEnd < 0 ? value.length : lineEnd;
    const lines = value.slice(lineStart, end).split('\n');
    const changed = lines.map((line) => (outdent ? line.replace(/^ {1,2}/, '') : `  ${line}`));
    const firstShift = changed[0].length - lines[0].length;
    const totalShift = changed.join('\n').length - lines.join('\n').length;

    block.text = value.slice(0, lineStart) + changed.join('\n') + value.slice(end);
    textarea.value = block.text;

    const start = Math.max(selectionStart + firstShift, lineStart);
    textarea.setSelectionRange(start, Math.max(selectionEnd + totalShift, start));
  }

  autoGrow(textarea);
  sync();
}

/** 글자를 쓰다가 ⌘⇧↑·↓ 로 지금 블록을 옮긴다. 커서는 따라간다 */
function moveBlockAt(index, direction) {
  const block = blocks.value[index];
  // 안쪽 블록까지 한 덩어리로, 옆 블록 묶음을 통째로 건너뛰어 옮긴다
  const next = moveBlockRange(blocks.value, index, subtreeEnd(blocks.value, index), direction);

  if (!next) {
    return;
  }

  const caret = caretOffset();
  blocks.value = next;
  sync();
  focusBlock(block.id, caret);
}

/** 글자를 칠 수 있는 블록인지. 구분선·이미지는 커서가 들어갈 자리가 없다 */
function isWritable(block) {
  return block && block.type !== 'divider' && block.type !== 'image';
}

/**
 * index 블록 바로 아래에서 이어 쓰게 한다.
 * 구분선처럼 커서가 못 들어가는 블록을 만든 뒤 커서가 사라지면, 이어 친 글자가 전부 날아간다.
 * 바로 아래가 빈 문단이면 그 자리를, 아니면 새 문단을 만들어 커서를 둔다.
 */
function continueAfter(index) {
  const below = blocks.value[index + 1];
  const target = below?.type === 'paragraph' && below.text === '' ? below : createBlock('paragraph');

  if (target !== below) {
    blocks.value.splice(index + 1, 0, target);
  }

  sync();
  focusBlock(target.id, 'start');
}

/** step 방향으로 가장 가까운, 글자를 칠 수 있는 블록의 자리. 없으면 -1 */
function writableIndexFrom(index, step) {
  for (let cursor = index + step; cursor >= 0 && cursor < blocks.value.length; cursor += step) {
    if (isWritable(blocks.value[cursor]) && !hiddenIds.value.has(blocks.value[cursor].id)) {
      return cursor;
    }
  }

  return -1;
}

/**
 * 위·아래 블록으로 커서를 옮긴다. 가로 위치는 되도록 그대로 둔다.
 * @param edge 'first' 면 그 블록의 첫 줄, 'last' 면 끝 줄
 */
function focusNeighbor(block, x, edge) {
  const text = textRefs.get(block.id);

  if (text && x !== null) {
    text.focusAt(x, edge);
    previous.focusId = block.id;
    previous.caret = caretOffset();
    return;
  }

  focusBlock(block.id, edge === 'first' ? 'start' : 'end');
}

/* ── 표 ───────────────────────────────────── */

/** 새 표: 3열 3줄, 첫 줄은 제목 행 */
function newTableRows() {
  return Array.from({ length: 3 }, () => ['', '', '']);
}

function onTableUpdate(block, patch, mergeKey = '') {
  Object.assign(block, patch);
  sync(mergeKey);
}

/** 표 맨 위·아래 줄에서 방향키로 표를 벗어난다 */
function onTableLeave(index, direction) {
  const target = writableIndexFrom(index, direction);

  if (target >= 0) {
    focusNeighbor(blocks.value[target], null, direction > 0 ? 'first' : 'last');
  }
}

/** 표 칸에서 누른 블록 단축키. 끝나면 쓰던 칸으로 커서를 돌려놓는다 */
function onTableCommand(index, name) {
  const block = blocks.value[index];
  const key = document.activeElement?.closest?.('[data-cell]')?.dataset.cell;

  if (name === 'format') {
    refreshToolbar();
    return;
  }

  if (name === 'k') {
    startLink();
    return;
  }

  if (name === 'h') {
    if (key && tableRefs.get(block.id)?.cell(key)?.applyColor(lastColor)) {
      tableRefs.get(block.id).commit(key);
      refreshToolbar();
    }

    return;
  }

  if (name === 'd') {
    duplicateBlockAt(index);
  } else if (name === 'ArrowUp' || name === 'ArrowDown') {
    moveBlockAt(index, name === 'ArrowUp' ? -1 : 1);
  }

  if (key) {
    const [row, col] = key.split(':').map(Number);
    tableRefs.get(block.id)?.focusCell(row, col);
  }
}

/* ── 이미지 ───────────────────────────────── */

function onDropFiles(index, event) {
  event.preventDefault();
  const files = imageFilesOf(event.dataTransfer);

  if (files.length === 0) {
    // 파일이 아니면 블록 순서 바꾸기다
    onDrop();
    return;
  }

  files.forEach((file, offset) => insertImage(file, index + offset));
  resetDrag();
}

function addBlockAtEnd() {
  keyboardInsertIndex.value = -1;
  const last = blocks.value[blocks.value.length - 1];

  // 비어 있는 블록이 이미 끝에 있으면 거기로 커서만 옮긴다.
  // 단 이미지는 글자를 칠 수 없어서 재사용하면 안 된다 — 끝이 빈 이미지일 때
  // 여기로 보내면 커서가 갈 곳이 없어 아무 일도 일어나지 않는다
  if (last && isEmptyBlock(last) && last.type !== 'image') {
    focusBlock(last.id);
    return;
  }

  insertAfter(blocks.value.length - 1, createBlock('paragraph'));
}

</script>

<template>
  <div
    ref="editorRef"
    class="block-editor"
    :class="{ 'block-editor--resizing': resizingId !== '' }"
    tabindex="-1"
    role="region"
    aria-label="게시글 본문 블록"
    @dragend="resetDrag"
    @focusin="onFocusIn"
    @keydown="onEditorKeydown"
    @copy="onBlockCopy"
    @cut="onBlockCut"
    @paste="onBlockPaste"
  >
    <div
      v-for="(block, index) in blocks"
      :key="block.id"
      class="block-editor__row"
      :data-block-id="block.id"
      :style="block.indent ? { marginLeft: `${block.indent * 26}px` } : null"
      :class="[
        `block-editor__row--${block.type}`,
        { 'block-editor__row--selected': selectedRowIds.has(block.id) },
        { 'block-editor__row--hidden': hiddenIds.has(block.id) },
        { 'block-editor__row--heading': block.type === 'toggle' && block.toggleLevel },
        { 'block-editor__row--dropping': dropIndex === index && draggingId !== '' },
        { 'block-editor__row--keyboard-target': keyboardInsertIndex === index },
      ]"
      @dragover="onDragOver(index, $event)"
      @drop="onDropFiles(index, $event)"
    >
      <!-- 누르면 아래에 빈 줄. Option(Alt) 을 누른 채면 위에 -->
      <button
        class="block-editor__add"
        type="button"
        aria-label="아래에 줄 추가"
        title="클릭: 아래에 추가 · Option 클릭: 위에 추가"
        @click="addBlockNear(index, $event)"
      >+</button>

      <!-- 잡아서 순서를 바꾸고, 누르면 블록을 고르며 메뉴를 연다 -->
      <button
        class="block-editor__handle"
        type="button"
        draggable="true"
        aria-label="블록 옮기기"
        aria-haspopup="menu"
        :aria-pressed="selectedIds.includes(block.id)"
        @dragstart="onDragStart(block, $event)"
        @click="onHandleClick(block, $event)"
      >⠿</button>

      <hr v-if="block.type === 'divider'" class="post-body__divider" />

      <AdminBlockTable
        v-else-if="block.type === 'table'"
        :ref="(element) => setTableRef(block.id, element)"
        :block="block"
        @update="(patch, mergeKey) => onTableUpdate(block, patch, mergeKey)"
        @leave="onTableLeave(index, $event)"
        @continue="continueAfter(index)"
        @command="(name) => onTableCommand(index, name)"
      />

      <!-- 이미지는 글자를 치는 블록이 아니라서 입력칸을 두지 않는다.
           대체 텍스트만 고칠 수 있게 하고, 주소는 업로드가 채운다 -->
      <AdminBlockImage
        v-else-if="block.type === 'image'"
        :block="block"
        :uploading="isUploading(block)"
        :resizing="resizingId === block.id"
        :error="uploadErrors[block.id]"
        @select="selectImageBlock(block, $event)"
        @resize-start="(side, event) => onResizeStart(block, side, event)"
        @resize-keydown="onResizeKeydown(block, $event)"
        @align="setImageAlign(block, $event)"
        @caption="block.alt = $event; sync(`alt:${block.id}`)"
        @pick="pickImageFor(block)"
      />

      <template v-else>
        <span v-if="block.type === 'bullet' || block.type === 'ordered'" class="block-editor__marker">
          {{ listMarker(block, index) }}
        </span>
        <button
          v-else-if="block.type === 'toggle'"
          class="block-editor__toggle"
          :class="{ 'block-editor__toggle--open': block.open }"
          type="button"
          :aria-expanded="block.open"
          :aria-label="block.open ? '토글 접기' : '토글 펼치기'"
          @mousedown.prevent
          @click="toggleOpen(block)"
        >▸</button>
        <button
          v-else-if="block.type === 'todo'"
          class="block-editor__todo"
          :class="{ 'block-editor__todo--done': block.checked }"
          type="button"
          role="checkbox"
          :aria-checked="block.checked"
          aria-label="할 일 완료"
          @mousedown.prevent
          @click="toggleTodo(block)"
        ><span v-if="block.checked" aria-hidden="true">✓</span></button>
        <span v-else-if="block.type === 'callout'" class="post-callout__label">
          {{ CALLOUT_LABELS[block.variant] }}
        </span>

        <div class="block-editor__field">
          <template v-if="block.type === 'code'">
            <select
              class="block-editor__language"
              :value="block.language"
              @change="block.language = $event.target.value; sync()"
            >
              <option v-for="option in CODE_LANGUAGES" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>

            <!-- 색칠 층과 입력칸만 겹친다. 끝 줄바꿈이 잘리지 않게 보이지 않는 글자를 덧댄다 -->
            <div class="block-editor__code-area">
              <pre class="block-editor__highlight" aria-hidden="true"><span
                v-for="(token, tokenIndex) in codeTokens(block)"
                :key="tokenIndex"
                :class="`post-code__${token.kind}`"
              >{{ token.text }}</span>{{ '\u200b' }}</pre>

              <textarea
                :ref="(element) => setCodeRef(block.id, element)"
                class="block-editor__input block-editor__input--code"
                rows="1"
                :value="block.text"
                @input="onCodeInput(block, $event)"
                @keydown="onKeydown(block, index, $event)"
                @scroll="syncScroll"
              ></textarea>
            </div>
          </template>

          <AdminBlockText
            v-else
            :ref="(element) => setTextRef(block.id, element)"
            v-model="block.text"
            :class="[
              `block-editor__text--${block.type}`,
              block.type === 'heading' ? `block-editor__text--h${block.level}` : '',
              block.type === 'toggle' && block.toggleLevel ? `block-editor__text--h${block.toggleLevel}` : '',
              { 'block-editor__text--done': block.type === 'todo' && block.checked },
            ]"
            :placeholder="placeholderFor(block)"
            @input="onTextInput(block)"
            @keydown="onKeydown(block, index, $event)"
            @paste="onPaste(block, index, $event)"
          />
          <button
            v-if="block.type === 'toggle' && block.open && !hasChildren(index)"
            class="block-editor__toggle-empty"
            type="button"
            @mousedown.prevent
            @click="addToggleChild(index)"
          >빈 토글입니다. 눌러서 안에 내용을 쓰세요.</button>
        </div>
      </template>

      <AdminSlashMenu
        v-if="menuOpenId === block.id"
        :commands="commandsFor()"
        :active-index="menuIndex"
        @run="runCommand(block, $event)"
        @hover="menuIndex = $event"
      />
    </div>

    <button
      class="block-editor__tail"
      :class="{
        'block-editor__tail--dropping': dropIndex === blocks.length && draggingId !== '',
        'block-editor__tail--keyboard-target': keyboardInsertIndex === blocks.length,
      }"
      type="button"
      @dragover.prevent="dropIndex = blocks.length"
      @drop="onDropFiles(blocks.length - 1, $event)"
      @click="addBlockAtEnd(); clearSelection()"
    >
      여기를 눌러 이어 쓰기
    </button>

    <AdminBlockMenu
      :open="blockMenu.open"
      :anchor="blockMenu.anchor"
      :title="blockMenuTitle()"
      :can-turn="menuBlocks().length > 0 && menuBlocks().every((item) => isWritable(item) && item.type !== 'table')"
      :turn-options="TURN_OPTIONS"
      :current-type="menuBlocks().length === 1 ? blockTypeId(menuBlocks()[0]) : ''"
      @turn="onMenuTurn"
      @color="onMenuColor"
      @duplicate="onMenuDuplicate"
      @move="onMenuMove"
      @remove="onMenuRemove"
      @close="closeBlockMenu"
    />

    <AdminTextToolbar
      :toolbar="toolbar"
      :turn-options="toolbar.cellKey ? [] : TURN_OPTIONS"
      :current-type="blockTypeId(toolbarBlock())"
      @format="onToolbarFormat"
      @turn="onToolbarTurn"
      @color="onToolbarColor"
      @link-start="startLink"
      @link-apply="onLinkApply"
      @link-cancel="onLinkCancel"
    />
  </div>
</template>
