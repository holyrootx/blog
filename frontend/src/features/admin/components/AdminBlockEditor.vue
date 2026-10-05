<script setup>
import { nextTick, ref, watch } from 'vue';

import {
  createBlock,
  isEmptyBlock,
  isMultiline,
  toEditorBlocks,
  toMarkdown,
} from '../data/postEditorBlocks';
import { applyMarkdownShortcut, orderedNumberAt } from '../data/postEditorShortcuts';
import { useBlockSelection } from '../composables/useBlockSelection';
import { useEditorImages } from '../composables/useEditorImages';
import { useEditorKeyboard } from '../composables/useEditorKeyboard';
import { useSlashMenu } from '../composables/useSlashMenu';
import { createEditorHistory } from '../data/postEditorHistory';
import { CODE_LANGUAGES, toCodeTokens } from '../../../shared/post/codeHighlight';
import AdminBlockText from './AdminBlockText.vue';
import AdminBlockImage from './AdminBlockImage.vue';
import AdminSlashMenu from './AdminSlashMenu.vue';

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
const selection = useBlockSelection({ blocks, editorRef, sync, isUploading });
const {
  draggingId,
  dropIndex,
  selectedIds,
  keyboardInsertIndex,
  toggleSelect,
  clearSelection,
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
  if (applyMarkdownShortcut(block)) {
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
        <span v-if="block.type === 'bullet'" class="block-editor__marker">•</span>
        <span v-else-if="block.type === 'ordered'" class="block-editor__marker">
          {{ orderedNumberAt(blocks, index) }}.
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
  </div>
</template>
