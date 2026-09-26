<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';

import {
  createBlock,
  isEmptyBlock,
  isMultiline,
  toEditorBlocks,
  toMarkdown,
} from '../data/postEditorBlocks';
import {
  hasHeldBlocks,
  heldBlocks,
  holdBlocks,
  readClipboardBlocks,
} from '../data/postEditorClipboard';
import { createEditorHistory } from '../data/postEditorHistory';
import { filterSlashCommands } from '../data/postSlashCommands';
import { uploadAdminImage } from '../api/adminApi';
import { CODE_LANGUAGES, toCodeTokens } from '../../../shared/post/codeHighlight';
import {
  DEFAULT_IMAGE_ALIGN,
  MAX_IMAGE_WIDTH,
  MIN_IMAGE_WIDTH,
  clampImageWidth,
} from '../../../shared/post/postImageMarkdown';
import AdminBlockText from './AdminBlockText.vue';

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
const captionRefs = new Map();

const menuOpenId = ref('');
const menuQuery = ref('');
const menuIndex = ref(0);

// 드래그로 옮기는 중인 블록과 놓을 자리
const draggingId = ref('');
const dropIndex = ref(-1);
// 여러 블록 선택 (핸들 클릭, Shift+클릭)
const selectedIds = ref([]);
const selectionAnchorId = ref('');
const selectionCursorId = ref('');
// 잘라낸 뒤 화살표로 옮기는 삽입선. 0은 첫 블록 위, length는 마지막 블록 아래다
const keyboardInsertIndex = ref(-1);

// 폭을 끌고 있는 이미지 블록. 끄는 동안 글자가 선택되지 않게 하는 데 쓴다
const resizingId = ref('');
// 끌던 중에 화면을 벗어나면 창에 걸어 둔 이벤트를 걷어내야 한다
let stopResize = null;

const CALLOUT_LABELS = { tip: '팁', warning: '주의', note: '참고' };

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

function onEditorKeydown(event) {
  if (event.isComposing || event.keyCode === 229) {
    return;
  }

  const key = event.key.toLowerCase();
  const command = event.metaKey || event.ctrlKey;

  if (event.key === 'Escape') {
    event.preventDefault();

    const row = event.target.closest?.('[data-block-id]');

    // 글자 커서에서 한 번 누르면 그 블록을 고른다. 블록 모드에서 다시 누르면 빠져나온다
    if (row && selectedIds.value.length === 0 && keyboardInsertIndex.value < 0) {
      selectOnly(row.dataset.blockId);
      focusEditor();
    } else {
      clearSelection();
      keyboardInsertIndex.value = -1;
      editorRef.value?.blur();
    }

    return;
  }

  const inBlockMode = selectedIds.value.length > 0 || keyboardInsertIndex.value >= 0;

  if (inBlockMode) {
    if (command && key === 'a') {
      event.preventDefault();
      selectAllBlocks();
      return;
    }

    if (command && (key === 'c' || key === 'x')) {
      event.preventDefault();
      copySelected(key === 'x');
      return;
    }

    if (command && key === 'v' && hasHeldBlocks()) {
      event.preventDefault();
      pasteBlocks();
      return;
    }

    if ((event.key === 'Delete' || event.key === 'Backspace') && selectedIds.value.length > 0) {
      event.preventDefault();
      removeSelectedBlocks();
      return;
    }

    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      const direction = event.key === 'ArrowUp' ? -1 : 1;

      if (keyboardInsertIndex.value >= 0) {
        moveInsertCursor(direction);
      } else if (event.altKey) {
        moveSelectedBlocks(direction);
      } else if (event.shiftKey) {
        extendSelection(direction);
      } else {
        moveSelectionCursor(direction);
      }

      return;
    }

    if (event.key === 'Enter' && selectedIds.value.length === 1) {
      event.preventDefault();
      const [id] = selectedIds.value;

      clearSelection();
      focusBlock(id, 'start');
      return;
    }
  }

  if (!command) {
    return;
  }

  // 브라우저 기본 되돌리기는 글자만 되돌려 블록 배열과 어긋난다. 막고 우리가 받는다
  if (key === 'z') {
    event.preventDefault();

    if (event.shiftKey) {
      redo();
      return;
    }

    undo();
    return;
  }

  // 윈도우에서 앞으로 가기로 쓰는 조합
  if (key === 'y') {
    event.preventDefault();
    redo();
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

function setCaptionRef(id, element) {
  if (element) {
    captionRefs.set(id, element);
  } else {
    captionRefs.delete(id);
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

  if (text) {
    text.focus(position);
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
  if (applyBlockShortcut(block)) {
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

function applyBlockShortcut(block) {
  const heading = block.text.match(/^(#{1,6})\s(.*)$/);
  if (heading) {
    block.type = 'heading';
    block.level = heading[1].length;
    block.text = heading[2];
    return true;
  }

  const bullet = block.text.match(/^[-*+]\s(.*)$/);
  if (bullet && block.type !== 'bullet') {
    block.type = 'bullet';
    block.text = bullet[1];
    return true;
  }

  const ordered = block.text.match(/^\d+\.\s(.*)$/);
  if (ordered && block.type !== 'ordered') {
    block.type = 'ordered';
    block.text = ordered[1];
    return true;
  }

  const quote = block.text.match(/^>\s(.*)$/);
  if (quote && block.type !== 'quote') {
    block.type = 'quote';
    block.text = quote[1];
    return true;
  }

  if (block.text === '```') {
    block.type = 'code';
    block.text = '';
    return true;
  }

  if (block.text === '---') {
    block.type = 'divider';
    block.text = '';
    return true;
  }

  return false;
}

function detectSlash(block) {
  const element = textRefs.get(block.id);
  const before = element ? element.textBeforeCaret() : '';
  const match = before.match(/(?:^|\s)\/(\S*)$/);

  if (!match || filterSlashCommands(match[1]).length === 0) {
    closeMenu();
    return;
  }

  menuOpenId.value = block.id;
  menuQuery.value = match[1];
  menuIndex.value = 0;
}

function closeMenu() {
  menuOpenId.value = '';
  menuQuery.value = '';
  menuIndex.value = 0;
}

function commandsFor() {
  return filterSlashCommands(menuQuery.value);
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

  // 선택한 글자를 굵게·기울임
  if ((event.metaKey || event.ctrlKey) && ['b', 'i'].includes(event.key.toLowerCase())) {
    event.preventDefault();
    textRefs.get(block.id)?.wrapSelection(event.key.toLowerCase() === 'b' ? 'strong' : 'em');
    sync();
    return;
  }

  const isCode = block.type === 'code';
  const element = textRefs.get(block.id);

  if (event.key === 'Enter' && !event.shiftKey && !isMultiline(block)) {
    event.preventDefault();
    splitBlock(block, index);
    return;
  }

  const atStart = isCode ? event.target.selectionStart === 0 : element?.isCaretAtStart();
  const atEnd = isCode
    ? event.target.selectionStart === event.target.value.length
    : element?.isCaretAtEnd();

  if (event.key === 'Backspace' && atStart) {
    // 서식만 있는 블록은 한 번 눌러 문단으로 되돌린다
    if (block.type !== 'paragraph' && block.text === '') {
      event.preventDefault();
      block.type = 'paragraph';
      sync();
      return;
    }

    if (index > 0 && block.text === '') {
      event.preventDefault();
      removeBlock(index);
      return;
    }
  }

  if (event.key === 'ArrowUp' && atStart && index > 0) {
    event.preventDefault();
    focusBlock(blocks.value[index - 1].id);
    return;
  }

  if (event.key === 'ArrowDown' && atEnd && index < blocks.value.length - 1) {
    event.preventDefault();
    focusBlock(blocks.value[index + 1].id, 'start');
  }
}

function handleMenuKey(block, event) {
  const commands = commandsFor();

  if (event.key === 'ArrowDown') {
    event.preventDefault();
    menuIndex.value = (menuIndex.value + 1) % commands.length;
    return true;
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault();
    menuIndex.value = (menuIndex.value - 1 + commands.length) % commands.length;
    return true;
  }

  if (event.key === 'Enter' || event.key === 'Tab') {
    event.preventDefault();
    runCommand(block, commands[menuIndex.value]);
    return true;
  }

  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    closeMenu();
    return true;
  }

  return false;
}

/**
 * Enter 를 누른 자리에서 블록을 나눈다.
 * 문단 중간에서 눌러도 앞뒤가 갈라져야 한다 — 뒤에 빈 블록만 붙으면
 * 쓰던 문장을 손으로 잘라 옮겨야 한다.
 */
function splitBlock(block, index) {
  // 빈 목록 항목에서 Enter 는 목록을 끝낸다. 빈 항목을 남기지 않는다
  if ((block.type === 'bullet' || block.type === 'ordered') && block.text.trim() === '') {
    block.type = 'paragraph';
    sync();
    focusBlock(block.id);
    return;
  }

  const parts = textRefs.get(block.id)?.splitAtCaret();
  const before = parts ? parts.before : block.text;
  const after = parts ? parts.after : '';

  block.text = before;

  // 목록 안에서는 같은 종류가 이어진다
  const nextType = block.type === 'bullet' || block.type === 'ordered' ? block.type : 'paragraph';
  const next = createBlock(nextType, { text: after });

  blocks.value.splice(index + 1, 0, next);
  sync();
  focusBlock(next.id, 'start');
}

/** 이어진 번호 목록 안에서 몇 번째인지. 문서 전체 순번이 아니다 */
function orderedNumber(index) {
  let number = 1;

  for (let cursor = index - 1; cursor >= 0 && blocks.value[cursor].type === 'ordered'; cursor -= 1) {
    number += 1;
  }

  return number;
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

function runCommand(block, command) {
  if (!command) {
    return;
  }

  block.text = block.text.replace(/(?:^|\s)\/\S*$/, '');

  // 이미지는 블록만 만들어 두고 파일 고르기를 띄운다.
  // 주소를 손으로 적게 하면 올리는 일이 화면 밖으로 새어 나간다
  if (command.id === 'image') {
    block.type = 'image';
    block.text = '';
    closeMenu();
    sync();
    pickImageFor(block);
    return;
  }

  if (command.kind === 'inline') {
    block.text += command.template.replace('{}', command.placeholder);
  } else if (/^h[1-6]$/.test(command.id)) {
    block.type = 'heading';
    block.level = Number(command.id.slice(1));
  } else if (['bullet', 'ordered', 'quote', 'code', 'divider'].includes(command.id)) {
    block.type = command.id;

    if (command.id === 'divider') {
      block.text = '';
    }
  } else {
    block.type = 'callout';
    block.variant = command.id;
  }

  closeMenu();
  sync();
  focusBlock(block.id);
}

/* ── 이미지 ───────────────────────────────── */

// 올리는 중인 블록 id 와 실패 메시지. 블록마다 따로 둔다 — 여러 장을 한꺼번에 놓을 수 있다
const uploadingIds = ref([]);
const uploadErrors = ref({});

// 만들어 둔 임시 주소. 화면을 떠날 때 돌려주지 않으면 그림이 메모리에 계속 남는다
const objectUrls = [];

onBeforeUnmount(() => {
  objectUrls.forEach((url) => URL.revokeObjectURL(url));
  stopResize?.();
});

function isUploading(block) {
  return uploadingIds.value.includes(block.id);
}

/** 파일 고르기 창을 띄우고, 고른 파일을 그 블록에 올린다 */
function pickImageFor(block) {
  const picker = document.createElement('input');
  picker.type = 'file';
  picker.accept = 'image/*';

  picker.addEventListener('change', () => {
    const [file] = picker.files ?? [];

    if (file) {
      uploadInto(block, file);
    }
  });

  picker.click();
}

async function uploadInto(block, file) {
  if (isUploading(block)) {
    return;
  }

  // 고른 파일을 먼저 보여준다. 올리는 데 걸리는 시간만큼 빈 자리를 보고 있을 이유가 없다 —
  // 같은 그림이 이미 이 컴퓨터에 있는데 올렸다가 다시 받아오면 그만큼 더 기다린다
  block.previewUrl = URL.createObjectURL(file);
  objectUrls.push(block.previewUrl);

  // 원본 크기는 파일에만 달린 값이라 업로드 응답을 기다릴 이유가 없다.
  // 서버는 이 값을 모른다 — 알아내게 하려면 이미지를 통째로 메모리에 펼쳐야 한다
  readNaturalSize(block, block.previewUrl);

  uploadingIds.value = [...uploadingIds.value, block.id];
  delete uploadErrors.value[block.id];

  try {
    const image = await uploadAdminImage(file);

    block.url = image.url;
    // 대체 텍스트 기본값을 파일 이름으로 둔다. 비워 두면 화면 낭독기가 읽을 것이 없다
    block.alt = block.alt || image.originalName.replace(/\.[^.]+$/, '');

    sync();
  } catch (error) {
    // 올라가지 않은 그림을 올라간 것처럼 보여주면 안 된다
    block.previewUrl = '';

    // 실패한 블록은 지우지 않는다. 지우면 어디에 무엇을 넣으려 했는지 사라진다
    uploadErrors.value = { ...uploadErrors.value, [block.id]: error.message };
  } finally {
    uploadingIds.value = uploadingIds.value.filter((id) => id !== block.id);
  }
}

/**
 * 원본 픽셀 크기를 읽어 블록에 적어 둔다.
 *
 * 저장 형식에 실려 공개 화면이 비율을 미리 알게 되고, 그래야 이미지가 도착할 때
 * 아래 글이 밀리지 않는다. 못 읽으면 값을 비워 둔다 — 크기 정보가 없던
 * 예전 글과 같은 상태이고, 글이 밀릴 뿐 깨지지는 않는다.
 */
function readNaturalSize(block, source) {
  const probe = new Image();

  probe.addEventListener('load', () => {
    block.naturalWidth = probe.naturalWidth;
    block.naturalHeight = probe.naturalHeight;

    sync();
  });

  probe.src = source;
}

/** 이미지 파일 하나를 새 블록으로 만들어 올린다 */
function insertImage(file, afterIndex) {
  const block = createBlock('image');

  blocks.value.splice(afterIndex + 1, 0, block);
  sync();
  uploadInto(block, file);
}

function imageFilesOf(dataTransfer) {
  return [...(dataTransfer?.files ?? [])].filter((file) => file.type.startsWith('image/'));
}

/**
 * 붙여넣기로 들어온 이미지.
 *
 * 스크린샷은 대부분 이 경로로 들어온다. 글자 붙여넣기는 건드리지 않는다 —
 * 이미지가 들어 있을 때만 가로챈다.
 */
function onPaste(block, index, event) {
  const files = imageFilesOf(event.clipboardData);

  if (files.length === 0) {
    return;
  }

  event.preventDefault();
  files.forEach((file, offset) => insertImage(file, index + offset));
}

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

/* ── 이미지 폭 ────────────────────────────── */

// 이 자리들에는 손이 정확히 멈추지 않아도 붙는다. 눈으로 맞추기 어려운 값들이다
const SNAP_WIDTHS = [25, 50, 75, MAX_IMAGE_WIDTH];
const SNAP_RANGE = 3;
const KEY_STEP = 5;

// 사진 오른쪽 위에 뜨는 정렬 버튼
const ALIGN_OPTIONS = [
  { value: 'left', label: '왼쪽 정렬' },
  { value: 'center', label: '가운데 정렬' },
  { value: 'right', label: '오른쪽 정렬' },
];

/** 0(지정 없음)은 100% 로 그린다 */
function imageWidthOf(block) {
  return block.width || MAX_IMAGE_WIDTH;
}

function imageAlignOf(block) {
  return block.align || DEFAULT_IMAGE_ALIGN;
}

/**
 * 사진 오른쪽 위 캡션 단추.
 *
 * 캡션 칸은 평소에 보이지 않아서, 어디를 눌러야 쓸 수 있는지 알려면
 * 사진 아래로 마우스를 정확히 가져가 봐야 했다. 정렬 단추 옆에 두면
 * 손이 이미 가 있는 자리에서 바로 쓰기 시작할 수 있다.
 */
function focusCaption(block) {
  captionRefs.get(block.id)?.focus();
}

function setImageAlign(block, align) {
  if (imageAlignOf(block) === align) {
    return;
  }

  block.align = align;
  sync();
}

function setImageWidth(block, value) {
  const snapped = SNAP_WIDTHS.find((target) => Math.abs(target - value) <= SNAP_RANGE) ?? value;
  const next = clampImageWidth(snapped);

  if (next === block.width) {
    return;
  }

  block.width = next;

  // 끄는 동안 수십 번 불리지만 같은 키라 되돌리기 한 칸으로 묶인다
  sync(`width:${block.id}`);
}

/**
 * 손잡이를 잡고 끄는 동안 폭을 바꾼다.
 *
 * 가운데 정렬이라 한쪽을 당기면 반대쪽도 같이 좁아진다. 그래서 폭 변화는
 * 커서가 움직인 거리의 두 배다.
 *
 * pointer 이벤트로 처리하고 전파를 끊는다. 블록 순서 바꾸기가 같은 몸짓(누르고 끌기)을
 * 쓰기 때문에, 안 끊으면 손잡이를 당길 때 블록이 통째로 옮겨진다.
 */
function onResizeStart(block, side, event) {
  event.preventDefault();
  event.stopPropagation();

  const track = event.currentTarget.closest('.block-editor__image');

  if (!track) {
    return;
  }

  const trackWidth = track.getBoundingClientRect().width;

  if (trackWidth <= 0) {
    return;
  }

  const startX = event.clientX;
  const startWidth = imageWidthOf(block);
  const direction = side === 'left' ? -1 : 1;

  resizingId.value = block.id;

  const onMove = (moveEvent) => {
    const moved = (moveEvent.clientX - startX) * direction;

    setImageWidth(block, startWidth + (moved / trackWidth) * 200);
  };

  const onEnd = () => {
    resizingId.value = '';
    stopResize = null;

    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onEnd);
    window.removeEventListener('pointercancel', onEnd);
  };

  // 화면을 떠나는 중에 끌고 있었으면 붙은 채로 남는다
  stopResize = onEnd;

  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onEnd);
  window.addEventListener('pointercancel', onEnd);
}

/**
 * 마우스 없이도 폭을 바꿀 수 있어야 한다. 손잡이는 버튼이라 키가 바로 들어온다.
 *
 * 어느 쪽 손잡이를 잡았든 → 가 크게, ← 가 작게다. 왼쪽 손잡이에서 방향을
 * 뒤집으면 "물리적으로는" 맞지만 누르는 사람은 매번 헷갈린다.
 */
function onResizeKeydown(block, event) {
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault();

    const step = event.key === 'ArrowRight' ? KEY_STEP : -KEY_STEP;

    setImageWidth(block, imageWidthOf(block) + step);
    return;
  }

  if (event.key === 'Home') {
    event.preventDefault();
    setImageWidth(block, MIN_IMAGE_WIDTH);
    return;
  }

  if (event.key === 'End') {
    event.preventDefault();
    setImageWidth(block, MAX_IMAGE_WIDTH);
  }
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

/* ── 블록 선택·이동 ───────────────────────── */

function blockIndex(id) {
  return blocks.value.findIndex((block) => block.id === id);
}

function selectOnly(id) {
  if (blockIndex(id) < 0) {
    return;
  }

  selectedIds.value = [id];
  selectionAnchorId.value = id;
  selectionCursorId.value = id;
  keyboardInsertIndex.value = -1;
}

function selectAllBlocks() {
  selectedIds.value = blocks.value.map((block) => block.id);
  selectionAnchorId.value = blocks.value[0]?.id ?? '';
  selectionCursorId.value = blocks.value[blocks.value.length - 1]?.id ?? '';
  keyboardInsertIndex.value = -1;
}

function toggleSelect(block, event) {
  keyboardInsertIndex.value = -1;

  // Shift 로 범위 선택. 노션에서 여러 블록을 한꺼번에 옮길 때 쓰는 방식이다
  if (event.shiftKey && selectionAnchorId.value) {
    const anchor = blockIndex(selectionAnchorId.value);
    const target = blockIndex(block.id);
    const [from, to] = anchor < target ? [anchor, target] : [target, anchor];

    selectedIds.value = blocks.value.slice(from, to + 1).map((item) => item.id);
    selectionCursorId.value = block.id;
  } else if (event.metaKey || event.ctrlKey) {
    const selected = new Set(selectedIds.value);

    if (selected.has(block.id)) {
      selected.delete(block.id);
    } else {
      selected.add(block.id);
    }

    selectedIds.value = blocks.value.filter((item) => selected.has(item.id)).map((item) => item.id);
    selectionAnchorId.value = selectionAnchorId.value || block.id;
    selectionCursorId.value = block.id;
  } else {
    selectOnly(block.id);
  }

  event.currentTarget.blur();
  focusEditor();
}

function clearSelection() {
  selectedIds.value = [];
  selectionAnchorId.value = '';
  selectionCursorId.value = '';
}

async function focusEditor() {
  await nextTick();
  editorRef.value?.focus({ preventScroll: true });
}

function selectImageBlock(block, event) {
  event.preventDefault();
  selectOnly(block.id);
  focusEditor();
}

function moveSelectionCursor(direction) {
  const current = blockIndex(selectionCursorId.value || selectedIds.value[0]);
  const target = Math.min(Math.max(current + direction, 0), blocks.value.length - 1);

  if (target >= 0) {
    selectOnly(blocks.value[target].id);
  }
}

function extendSelection(direction) {
  const anchor = blockIndex(selectionAnchorId.value || selectedIds.value[0]);
  const cursor = blockIndex(selectionCursorId.value || selectedIds.value[selectedIds.value.length - 1]);
  const target = Math.min(Math.max(cursor + direction, 0), blocks.value.length - 1);

  if (anchor < 0 || target < 0) {
    return;
  }

  const [from, to] = anchor < target ? [anchor, target] : [target, anchor];
  selectedIds.value = blocks.value.slice(from, to + 1).map((block) => block.id);
  selectionCursorId.value = blocks.value[target].id;
}

function moveInsertCursor(direction) {
  keyboardInsertIndex.value = Math.min(
    Math.max(keyboardInsertIndex.value + direction, 0),
    blocks.value.length,
  );
}

function selectedBlocks() {
  const selected = new Set(selectedIds.value);

  return blocks.value.filter((block) => selected.has(block.id));
}

function cloneBlock(block) {
  const content = { ...block, previewUrl: '' };
  delete content.id;

  return createBlock(block.type, content);
}

function copySelected(cut = false, clipboardEvent = null) {
  const selected = selectedBlocks();

  if (selected.length === 0 || selected.some((block) => isUploading(block))) {
    return;
  }

  holdBlocks(selected);

  // 편집기 밖에 붙여 넣어도 최소한 마크다운 내용은 남는다
  const markdown = toMarkdown(heldBlocks());

  if (clipboardEvent?.clipboardData) {
    clipboardEvent.clipboardData.setData('text/plain', markdown);
  } else {
    navigator.clipboard?.writeText(markdown).catch(() => {});
  }

  if (cut) {
    removeSelectedBlocks(true);
  }
}

function onBlockCopy(event) {
  if (selectedIds.value.length === 0) {
    return;
  }

  event.preventDefault();
  copySelected(false, event);
}

function onBlockCut(event) {
  if (selectedIds.value.length === 0) {
    return;
  }

  event.preventDefault();
  copySelected(true, event);
}

/**
 * 붙여넣기.
 *
 * <p>담아 둔 블록이 있으면 그것을 쓴다. 없으면 시스템 클립보드의 글을 블록으로
 * 되돌려 본다 — 새로고침한 뒤나 편집기 밖에서 복사해 온 경우다. 둘 다 안 되면
 * 막지 않고 브라우저에 맡긴다. 글자를 붙여 넣으려던 것일 수 있어서다.</p>
 */
function onBlockPaste(event) {
  const pasting = hasHeldBlocks()
    ? heldBlocks()
    : readClipboardBlocks(event.clipboardData?.getData('text/plain'));

  // 블록으로 볼 것이 아니면 막지 않는다. 글자를 붙여 넣으려던 것일 수 있다
  if (pasting.length === 0) {
    return;
  }

  event.preventDefault();
  insertBlocks(pasting, caretBlockIndex(event.target));
}

/**
 * 커서가 놓인 블록의 다음 자리.
 *
 * <p>붙여넣을 자리를 <b>클릭하면</b> 블록 선택이 풀린다. 그것을 "붙여넣을 대상이 없다"
 * 로 보고 물러나면 브라우저 기본 동작이 일어나 마크다운이 글자로 박힌다 — 복사하고
 * 자리를 고른 다음 붙여넣는, 가장 흔한 순서가 그래서 망가졌다.</p>
 *
 * <p>글자 한가운데에 커서가 있어도 문단을 쪼개지 않고 그 블록 뒤에 넣는다. 쪼개는 쪽이
 * 더 똑똑해 보이지만, 어디서 끊길지 예측이 안 돼서 되돌리기가 잦아진다.</p>
 *
 * @returns 넣을 자리. 커서가 어느 블록에도 없으면 -1 (선택·삽입선을 따른다)
 */
function caretBlockIndex(target) {
  const row = target instanceof Element ? target.closest('[data-block-id]') : null;

  if (!row) {
    return -1;
  }

  const index = blockIndex(row.dataset.blockId);

  return index < 0 ? -1 : index + 1;
}

function removeSelectedBlocks(keepInsertCursor = false) {
  const selected = new Set(selectedIds.value);
  const indexes = blocks.value
    .map((block, index) => (selected.has(block.id) ? index : -1))
    .filter((index) => index >= 0);

  if (indexes.length === 0 || selectedBlocks().some((block) => isUploading(block))) {
    return;
  }

  const first = indexes[0];
  blocks.value = blocks.value.filter((block) => !selected.has(block.id));

  if (blocks.value.length === 0) {
    blocks.value = [createBlock('paragraph')];
  }

  clearSelection();
  sync();

  if (keepInsertCursor) {
    keyboardInsertIndex.value = Math.min(first, blocks.value.length);
  } else {
    selectOnly(blocks.value[Math.min(first, blocks.value.length - 1)].id);
  }

  focusEditor();
}

function pasteBlocks() {
  insertBlocks(heldBlocks());
}

function insertBlocks(source, preferredIndex = -1) {
  if (source.length === 0) {
    return;
  }

  let at = keyboardInsertIndex.value;

  if (at < 0) {
    const indexes = selectedIds.value.map(blockIndex).filter((index) => index >= 0);

    // 고른 블록도 삽입선도 없으면 커서가 있던 자리를 쓴다
    at = indexes.length > 0
      ? Math.max(...indexes) + 1
      : (preferredIndex >= 0 ? preferredIndex : blocks.value.length);
  }

  const pasted = source.map(cloneBlock);
  blocks.value.splice(at, 0, ...pasted);
  keyboardInsertIndex.value = -1;
  selectedIds.value = pasted.map((block) => block.id);
  selectionAnchorId.value = pasted[0]?.id ?? '';
  selectionCursorId.value = pasted[pasted.length - 1]?.id ?? '';

  sync();
  focusEditor();
}

function moveSelectedBlocks(direction) {
  const moving = selectedBlocks();

  if (moving.length === 0) {
    return;
  }

  const selected = new Set(moving.map((block) => block.id));
  const first = blocks.value.findIndex((block) => selected.has(block.id));
  const rest = blocks.value.filter((block) => !selected.has(block.id));
  const at = Math.min(Math.max(first + direction, 0), rest.length);
  const next = [...rest.slice(0, at), ...moving, ...rest.slice(at)];

  if (next.every((block, index) => block.id === blocks.value[index]?.id)) {
    return;
  }

  blocks.value = next;
  sync();
  focusEditor();
}

function onDragStart(block, event) {
  draggingId.value = block.id;
  keyboardInsertIndex.value = -1;

  // 고른 묶음을 잡으면 묶음째 옮긴다
  if (!selectedIds.value.includes(block.id)) {
    selectOnly(block.id);
  }

  event.dataTransfer.effectAllowed = 'move';
  // 데이터가 없으면 드래그를 시작하지 않는 브라우저가 있다
  event.dataTransfer.setData('text/plain', block.id);
}

function onDragOver(index, event) {
  event.preventDefault();
  const bounds = event.currentTarget.getBoundingClientRect();
  dropIndex.value = event.clientY < bounds.top + bounds.height / 2 ? index : index + 1;
}

function onDrop() {
  if (!draggingId.value || dropIndex.value < 0) {
    return;
  }

  const selected = new Set(selectedIds.value);
  const moving = blocks.value.filter((block) => selected.has(block.id));
  const selectedBefore = blocks.value
    .slice(0, dropIndex.value)
    .filter((block) => selected.has(block.id)).length;
  const rest = blocks.value.filter((block) => !selected.has(block.id));
  const at = Math.min(Math.max(dropIndex.value - selectedBefore, 0), rest.length);
  const next = [...rest.slice(0, at), ...moving, ...rest.slice(at)];

  if (!next.every((block, index) => block.id === blocks.value[index]?.id)) {
    blocks.value = next;
    sync();
  }

  resetDrag();
}

function resetDrag() {
  draggingId.value = '';
  dropIndex.value = -1;
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
      :class="[
        `block-editor__row--${block.type}`,
        { 'block-editor__row--selected': selectedIds.includes(block.id) },
        { 'block-editor__row--dropping': dropIndex === index && draggingId !== '' },
        { 'block-editor__row--keyboard-target': keyboardInsertIndex === index },
      ]"
      @dragover="onDragOver(index, $event)"
      @drop="onDropFiles(index, $event)"
    >
      <!-- 잡아서 순서를 바꾸고, 눌러서 블록을 고른다 -->
      <button
        class="block-editor__handle"
        type="button"
        draggable="true"
        aria-label="블록 옮기기"
        :aria-pressed="selectedIds.includes(block.id)"
        @dragstart="onDragStart(block, $event)"
        @click="toggleSelect(block, $event)"
      >⠿</button>

      <hr v-if="block.type === 'divider'" class="post-body__divider" />

      <!-- 이미지는 글자를 치는 블록이 아니라서 입력칸을 두지 않는다.
           대체 텍스트만 고칠 수 있게 하고, 주소는 업로드가 채운다 -->
      <div v-else-if="block.type === 'image'" class="block-editor__image">
        <template v-if="block.previewUrl || block.url">
          <!-- 폭을 가진 틀. 공개 화면의 figure 와 같은 자리를 차지해서
               여기서 보이는 크기가 곧 발행 결과다 -->
          <div
            class="block-editor__image-frame"
            :class="[
              `block-editor__image-frame--${imageAlignOf(block)}`,
              { 'block-editor__image-frame--resizing': resizingId === block.id },
            ]"
            :style="{ width: `${imageWidthOf(block)}%` }"
          >
            <!-- 손잡이를 이미지에만 맞춰 놓기 위한 칸. 캡션까지 묶으면
                 손잡이가 캡션 높이만큼 아래로 내려간다 -->
            <div class="block-editor__image-canvas">
              <img
                class="block-editor__image-preview"
                :class="{ 'block-editor__image-preview--uploading': isUploading(block) }"
                :src="block.previewUrl || block.url"
                :alt="block.alt"
                draggable="false"
                @click="selectImageBlock(block, $event)"
              />

              <!-- 좌우 손잡이. draggable=false 가 없으면 블록 순서 바꾸기가 먼저 물린다 -->
              <button
                v-for="side in ['left', 'right']"
                :key="side"
                class="block-editor__image-grip"
                :class="`block-editor__image-grip--${side}`"
                type="button"
                draggable="false"
                :aria-label="`이미지 폭 조절, 현재 ${imageWidthOf(block)}%`"
                @pointerdown="onResizeStart(block, side, $event)"
                @keydown="onResizeKeydown(block, $event)"
              ></button>

              <!-- 사진 오른쪽 위 단추들. 정렬 셋과 캡션 하나 -->
              <div class="block-editor__image-tools">
                <div class="block-editor__image-toolgroup" role="group" aria-label="이미지 정렬">
                  <button
                    v-for="option in ALIGN_OPTIONS"
                    :key="option.value"
                    class="block-editor__image-align"
                    :class="`block-editor__image-align--${option.value}`"
                    type="button"
                    draggable="false"
                    :title="option.label"
                    :aria-label="option.label"
                    :aria-pressed="imageAlignOf(block) === option.value"
                    @click="setImageAlign(block, option.value)"
                  ></button>
                </div>

                <span class="block-editor__image-tooldivider" aria-hidden="true"></span>

                <button
                  class="block-editor__image-captionbutton"
                  type="button"
                  draggable="false"
                  :title="block.alt ? '캡션 고치기' : '캡션 쓰기'"
                  :aria-label="block.alt ? '캡션 고치기' : '캡션 쓰기'"
                  @click="focusCaption(block)"
                ></button>
              </div>

              <!-- 끄는 동안에만 숫자를 띄운다. 항상 떠 있으면 사진을 가린다.
                   정렬 버튼이 오른쪽 위에 있어서 왼쪽으로 비켜 둔다 -->
              <span v-if="resizingId === block.id" class="block-editor__image-size">
                {{ imageWidthOf(block) }}%
              </span>
            </div>

            <!-- 캡션은 평소에 숨어 있다가 이미지에 마우스를 올리면 나타난다.
                 항상 떠 있으면 사진마다 빈 입력칸이 한 줄씩 따라다닌다.
                 여기 적은 값이 공개 화면의 캡션이자 대체 텍스트가 된다 -->
            <input
              :ref="(element) => setCaptionRef(block.id, element)"
              class="block-editor__image-caption"
              :class="{ 'block-editor__image-caption--filled': block.alt }"
              type="text"
              :value="block.alt"
              placeholder="캡션 추가"
              aria-label="이미지 캡션"
              @input="block.alt = $event.target.value; sync(`alt:${block.id}`)"
            />
          </div>
        </template>

        <p v-if="isUploading(block)" class="block-editor__image-status">올리는 중…</p>

        <button
          v-if="!block.previewUrl && !block.url && !isUploading(block)"
          class="block-editor__image-placeholder block-editor__image-placeholder--button"
          type="button"
          @click="pickImageFor(block)"
        >
          <span class="block-editor__image-icon" aria-hidden="true"></span>
          이미지 추가
        </button>

        <p v-if="uploadErrors[block.id]" class="block-editor__image-error" role="alert">
          {{ uploadErrors[block.id] }}
          <button class="block-editor__image-retry" type="button" @click="pickImageFor(block)">
            다시 고르기
          </button>
        </p>
      </div>

      <template v-else>
        <span v-if="block.type === 'bullet'" class="block-editor__marker">•</span>
        <span v-else-if="block.type === 'ordered'" class="block-editor__marker">
          {{ orderedNumber(index) }}.
        </span>
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
              >{{ token.text }}</span>{{ '​' }}</pre>

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
            ]"
            :placeholder="index === 0 && blocks.length === 1
              ? '글을 쓰거나 / 를 눌러 블록을 고르세요'
              : ''"
            @input="onTextInput(block)"
            @keydown="onKeydown(block, index, $event)"
            @paste="onPaste(block, index, $event)"
          />
        </div>
      </template>

      <ul v-if="menuOpenId === block.id" class="admin-slash block-editor__menu">
        <li
          v-for="(command, commandIndex) in commandsFor()"
          :key="command.id"
          class="admin-slash__item"
          :class="{ 'admin-slash__item--active': commandIndex === menuIndex }"
          @mousedown.prevent="runCommand(block, command)"
          @mouseenter="menuIndex = commandIndex"
        >
          <span class="admin-slash__main">
            <span class="admin-slash__label">{{ command.label }}</span>
            <kbd class="admin-slash__key">/{{ command.shortcut }}</kbd>
          </span>
          <span class="admin-slash__hint">{{ command.hint }}</span>
        </li>
      </ul>
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
  </div>
</template>
