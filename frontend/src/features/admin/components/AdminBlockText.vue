<script setup>
import { onMounted, ref, watch } from 'vue';

import { findCompletedMarker, htmlToInline, inlineToHtml } from '../data/inlineMarkdown';

/**
 * 글자를 쓰는 블록 한 칸. contenteditable 이다.
 *
 * textarea 를 쓰면 글자 일부만 굵게 만들 수 없다. 그래서 여기만 contenteditable 을 쓰고,
 * 저장할 때는 다시 마크다운 문자열로 되돌린다.
 *
 * 한글 조합 중에는 절대 다시 그리지 않는다. 조합 도중 innerHTML 을 건드리면
 * 입력기가 붙잡고 있던 글자가 날아가거나 커서가 튄다.
 */
const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  placeholder: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['update:modelValue', 'keydown', 'input']);

const rootRef = ref(null);
const composing = ref(false);

function render() {
  if (!rootRef.value || composing.value) {
    return;
  }

  const html = inlineToHtml(props.modelValue);

  if (rootRef.value.innerHTML !== html) {
    rootRef.value.innerHTML = html;
  }
}

onMounted(render);

// 밖에서 값이 바뀐 경우(불러오기·블록 변환)에만 다시 그린다.
// 내가 방금 내보낸 값이 돌아온 것이면 건드리지 않아야 커서가 유지된다
watch(() => props.modelValue, (next) => {
  if (!rootRef.value || composing.value) {
    return;
  }

  if (htmlToInline(rootRef.value) === next) {
    return;
  }

  render();
});

function readText() {
  return rootRef.value ? htmlToInline(rootRef.value) : '';
}

function onInput() {
  if (composing.value) {
    return;
  }

  // 닫는 기호를 친 순간 기호가 사라지고 서식이 들어간다 (노션과 같은 동작)
  applyCompletedMarker();

  emit('update:modelValue', readText());
  emit('input');
}

/** 커서 앞에서 막 완성된 **굵게** 같은 표기를 태그로 바꾼다 */
function applyCompletedMarker() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0 || !selection.isCollapsed) {
    return;
  }

  const range = selection.getRangeAt(0);
  const node = range.startContainer;

  if (node.nodeType !== Node.TEXT_NODE || !rootRef.value.contains(node)) {
    return;
  }

  const offset = range.startOffset;
  const marker = findCompletedMarker(node.textContent.slice(0, offset));

  if (!marker) {
    return;
  }

  const start = offset - marker.markerLength;

  if (start < 0) {
    return;
  }

  // 표기 구간만 잘라내고 그 자리에 태그를 넣는다
  const target = document.createRange();
  target.setStart(node, start);
  target.setEnd(node, offset);
  target.deleteContents();

  const element = document.createElement(marker.tag);
  element.textContent = marker.text;
  target.insertNode(element);

  // 커서를 태그 뒤로 옮긴다. 안 옮기면 다음 글자가 서식 안에 들어간다
  const spacer = document.createTextNode('​');
  element.after(spacer);

  const next = document.createRange();
  next.setStart(spacer, 1);
  next.collapse(true);
  selection.removeAllRanges();
  selection.addRange(next);
}

/** 선택한 글자를 태그로 감싼다 (Cmd+B 등) */
function wrapSelection(tag) {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    return;
  }

  const range = selection.getRangeAt(0);

  if (!rootRef.value.contains(range.commonAncestorContainer)) {
    return;
  }

  const element = document.createElement(tag);
  element.appendChild(range.extractContents());
  range.insertNode(element);

  selection.removeAllRanges();
  emit('update:modelValue', readText());
}

/** @param position 'start' · 'end' · 앞에서부터 센 글자 수 */
function focus(position = 'end') {
  const element = rootRef.value;

  if (!element) {
    return;
  }

  element.focus();

  const range = document.createRange();

  if (typeof position === 'number') {
    placeCaret(range, element, position);
  } else {
    range.selectNodeContents(element);
    range.collapse(position === 'start');
  }

  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
}

/**
 * 앞에서부터 글자를 세어 커서를 놓는다.
 * 서식이 들어간 블록은 글자가 태그 여러 개에 나뉘어 있어 한 덩어리로 셀 수 없다.
 */
function placeCaret(range, element, offset) {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  let remaining = offset;

  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (remaining <= node.textContent.length) {
      range.setStart(node, remaining);
      range.collapse(true);
      return;
    }

    remaining -= node.textContent.length;
  }

  // 글자가 모자라면 맨 뒤에 둔다
  range.selectNodeContents(element);
  range.collapse(false);
}

/** 커서가 블록 맨 앞인지. Backspace 로 앞 블록과 합칠지 판단할 때 쓴다 */
function isCaretAtStart() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return false;
  }

  const range = selection.getRangeAt(0).cloneRange();
  range.selectNodeContents(rootRef.value);
  range.setEnd(selection.getRangeAt(0).startContainer, selection.getRangeAt(0).startOffset);

  return range.toString().length === 0;
}

function isCaretAtEnd() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return false;
  }

  const range = selection.getRangeAt(0).cloneRange();
  range.selectNodeContents(rootRef.value);
  range.setStart(selection.getRangeAt(0).endContainer, selection.getRangeAt(0).endOffset);

  return range.toString().length === 0;
}

/** 커서 앞 글자들. 슬래시 메뉴와 블록 단축키 판단에 쓴다 */
function textBeforeCaret() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return '';
  }

  const range = selection.getRangeAt(0).cloneRange();
  range.selectNodeContents(rootRef.value);
  range.setEnd(selection.getRangeAt(0).startContainer, selection.getRangeAt(0).startOffset);

  return range.toString();
}

/** 커서가 앞에서 몇 번째 글자인지. 되돌리기 때 커서를 제자리에 놓는 데 쓴다 */
function caretOffset() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return 0;
  }

  // 다른 블록에 커서가 있으면 여기 기준으로 잴 수 없다
  if (!rootRef.value.contains(selection.getRangeAt(0).startContainer)) {
    return 0;
  }

  return textBeforeCaret().length;
}

/**
 * 커서를 기준으로 앞뒤를 마크다운으로 갈라 돌려준다.
 * 화면에 보이는 글자 수로 자르면 서식 기호가 어긋나므로,
 * DOM 을 구간째 복사한 뒤 각각을 마크다운으로 되돌린다.
 */
function splitAtCaret() {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return null;
  }

  const caret = selection.getRangeAt(0);

  if (!rootRef.value.contains(caret.startContainer)) {
    return null;
  }

  const toText = (fragment) => {
    const holder = document.createElement('div');
    holder.appendChild(fragment);
    return htmlToInline(holder);
  };

  const head = document.createRange();
  head.selectNodeContents(rootRef.value);
  head.setEnd(caret.startContainer, caret.startOffset);

  const tail = document.createRange();
  tail.selectNodeContents(rootRef.value);
  tail.setStart(caret.endContainer, caret.endOffset);

  return { before: toText(head.cloneContents()), after: toText(tail.cloneContents()) };
}

defineExpose({
  // 값과 화면이 어긋난 것을 밖에서 알고 있을 때 쓴다.
  // 감시자는 값이 바뀌었다 제자리로 돌아온 경우를 보지 못한다
  redraw: render,
  focus,
  isCaretAtStart,
  isCaretAtEnd,
  textBeforeCaret,
  caretOffset,
  wrapSelection,
  readText,
  splitAtCaret,
});
</script>

<template>
  <div
    ref="rootRef"
    class="block-editor__text"
    contenteditable="true"
    role="textbox"
    :data-placeholder="placeholder"
    @input="onInput"
    @keydown="emit('keydown', $event)"
    @compositionstart="composing = true"
    @compositionend="composing = false; onInput()"
  ></div>
</template>
