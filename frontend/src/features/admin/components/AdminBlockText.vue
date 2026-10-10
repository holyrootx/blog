<script setup>
import { onMounted, ref, watch } from 'vue';

import {
  colorRuns,
  findCompletedMarker,
  htmlToInline,
  inlineToHtml,
  readRuns,
  runsToMarkdown,
  safeHref,
} from '../data/inlineMarkdown';

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

  if (htmlToInline(rootRef.value, { root: true }) === next) {
    return;
  }

  render();
});

function readText() {
  return rootRef.value ? htmlToInline(rootRef.value, { root: true }) : '';
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

// 글자 서식 태그. 저장 형식은 한 글자에 서식 하나만 담을 수 있다
const FORMAT_SELECTOR = 'strong, b, em, i, s, strike, del, u, code, a';
const FORMAT_NAMES = { strong: 'strong, b', em: 'em, i', s: 's, strike, del', u: 'u', code: 'code', a: 'a' };

/** 구간의 시작이 들어 있는 서식 태그. 이 블록 안의 것만 본다 */
function formatAt(range) {
  const start = range.startContainer.nodeType === Node.ELEMENT_NODE
    ? range.startContainer
    : range.startContainer.parentElement;
  const found = start?.closest(FORMAT_SELECTOR);

  return found && rootRef.value.contains(found) ? found : null;
}

/** 태그가 어느 서식인지 (b 는 strong, i 는 em) */
function formatKind(element) {
  return Object.keys(FORMAT_NAMES).find((kind) => element.matches(FORMAT_NAMES[kind])) ?? '';
}

/**
 * 감쌀 조각 안의 서식을 푼다. 서식 안의 서식은 저장되지 않으므로 화면에서도 미리 푼다 —
 * 보이는 것과 저장되는 것이 같아야 한다.
 */
function unwrapFormats(fragment) {
  fragment.querySelectorAll(FORMAT_SELECTOR).forEach((element) => {
    element.replaceWith(...element.childNodes);
  });

  return fragment;
}

/** 고른 구간을 새 서식 태그로 감싸고, 그 글자를 다시 골라 둔다 */
function wrapRange(range, element) {
  element.appendChild(unwrapFormats(range.extractContents()));
  range.insertNode(element);

  const next = document.createRange();
  next.selectNodeContents(element);
  return next;
}

/** 서식 태그를 풀고, 풀린 글자를 다시 골라 둔다 */
function unwrapElement(element) {
  const first = element.firstChild;
  const last = element.lastChild;
  const next = document.createRange();

  element.replaceWith(...element.childNodes);
  next.setStartBefore(first);
  next.setEndAfter(last);
  return next;
}

/**
 * 선택한 글자의 서식을 켜고 끈다 (Cmd+B 등).
 *
 * 이미 그 서식 안이면 풀고, 아니면 감싼다. 어느 쪽이든 같은 글자를 다시 골라 둔다 —
 * 선택이 풀리면 다음에 친 글자가 엉뚱한 자리(고른 글의 맨 앞)에 들어간다.
 * 다른 서식 안에서는 걸지 않는다(저장하면 하나가 사라진다). 그러면 false.
 */
function toggleFormat(tag) {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    return false;
  }

  const range = selection.getRangeAt(0);

  if (!rootRef.value.contains(range.commonAncestorContainer)) {
    return false;
  }

  const existing = formatAt(range);

  if (existing && formatKind(existing) !== tag) {
    return false;
  }

  const next = existing ? unwrapElement(existing) : wrapRange(range, document.createElement(tag));

  selection.removeAllRanges();
  selection.addRange(next);
  emit('update:modelValue', readText());
  return true;
}

/**
 * 고른 글자에 링크를 건다. 이미 링크 안이면 주소를 바꾸고, 주소가 비면 링크를 푼다.
 * 끝난 뒤에도 같은 글자를 골라 둔다. 다른 서식 안에서는 걸지 않는다(false).
 */
function applyLink(href) {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return false;
  }

  const range = selection.getRangeAt(0);

  if (!rootRef.value.contains(range.commonAncestorContainer)) {
    return false;
  }

  const existing = formatAt(range);
  let next;

  if (existing && formatKind(existing) !== 'a') {
    return false;
  }

  if (existing) {
    if (!href) {
      next = unwrapElement(existing);
    } else {
      existing.setAttribute('href', safeHref(href));
      existing.setAttribute('data-href', href);
      next = document.createRange();
      next.selectNodeContents(existing);
    }
  } else {
    if (!href || selection.isCollapsed) {
      return false;
    }

    const link = document.createElement('a');
    link.setAttribute('href', safeHref(href));
    link.setAttribute('data-href', href);
    next = wrapRange(range, link);
  }

  selection.removeAllRanges();
  selection.addRange(next);
  emit('update:modelValue', readText());
  return true;
}

/**
 * 커서 바로 앞의 글자 count 개를 지운다. 커서 뒤 글자는 건드리지 않는다.
 * / 메뉴로 고른 명령에서 "/명령" 만 지울 때 쓴다 — 줄 끝까지 지우면 뒤에 쓰던 글이 사라진다.
 */
function removeBeforeCaret(count) {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0 || count <= 0) {
    return;
  }

  const caret = selection.getRangeAt(0);

  if (!rootRef.value.contains(caret.startContainer)) {
    return;
  }

  // 커서에서 뒤로 글자를 세며 지울 구간의 시작을 찾는다. 서식 태그를 넘어갈 수 있다
  const walker = document.createTreeWalker(rootRef.value, NodeFilter.SHOW_TEXT);
  const nodes = [];

  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    nodes.push(node);
  }

  const head = document.createRange();
  head.selectNodeContents(rootRef.value);
  head.setEnd(caret.startContainer, caret.startOffset);
  let remaining = head.toString().length - count;

  if (remaining < 0) {
    return;
  }

  const target = document.createRange();

  for (const node of nodes) {
    if (remaining <= node.textContent.length) {
      target.setStart(node, remaining);
      break;
    }

    remaining -= node.textContent.length;
  }

  target.setEnd(caret.startContainer, caret.startOffset);
  target.deleteContents();
  target.collapse(true);

  selection.removeAllRanges();
  selection.addRange(target);
  emit('update:modelValue', readText());
}

/**
 * 커서 자리에 서식 글자를 넣고 그 글자를 골라 둔다. 바로 치면 자리 글자가 바뀐다.
 * @param href 링크일 때 주소
 */
function insertFormatted(tag, text, href = '') {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return;
  }

  const range = selection.getRangeAt(0);

  if (!rootRef.value.contains(range.startContainer)) {
    return;
  }

  const element = document.createElement(tag);
  element.textContent = text;

  if (tag === 'a') {
    element.setAttribute('href', href);
    element.setAttribute('data-href', href);
  }

  range.deleteContents();

  // 다른 서식 안이면 그 바깥 바로 뒤에 넣는다. 서식 안의 서식은 저장되지 않는다
  const existing = formatAt(range);

  if (existing) {
    range.setStartAfter(existing);
    range.collapse(true);
  }

  range.insertNode(element);

  const next = document.createRange();
  next.selectNodeContents(element);
  selection.removeAllRanges();
  selection.addRange(next);
  emit('update:modelValue', readText());
}

/**
 * 블록 안에서 줄을 바꾼다 (여러 줄 블록의 Enter, Shift+Enter).
 * 브라우저 기본 Enter 는 줄을 <div> 로 나눠서 저장 형식과 어긋나기 쉽다. <br> 로 통일한다
 */
function insertLineBreak() {
  document.execCommand('insertLineBreak');
}

/** 화면에서 한 줄의 높이. 커서가 첫 줄·끝 줄인지 가늠할 때 쓴다 */
function lineHeight() {
  const value = Number.parseFloat(getComputedStyle(rootRef.value).lineHeight);

  return Number.isFinite(value) ? value : 24;
}

/**
 * 커서가 블록의 첫 줄·끝 줄에 있는지와 가로 위치.
 * 방향키로 위아래 블록에 넘어갈지 정한다 — 글자 끝까지 가야 넘어가면, 한 줄짜리 문단에서도
 * 아래 키를 두 번 눌러야 다음 문단에 간다.
 */
function caretLine() {
  const selection = window.getSelection();
  const box = rootRef.value.getBoundingClientRect();

  if (!selection || selection.rangeCount === 0 || !rootRef.value.contains(selection.focusNode)) {
    return { first: true, last: true, x: box.left };
  }

  // 선택이 있어도 커서가 움직이는 끝(focus)을 기준으로 잰다
  const range = document.createRange();
  range.setStart(selection.focusNode, selection.focusOffset);
  range.collapse(true);

  // 빈 줄이나 줄 맨 앞에서는 사각형이 비어 나온다. 그러면 블록 기준으로 본다
  const rect = [...range.getClientRects()].pop() ?? range.getBoundingClientRect();
  const height = lineHeight();

  if (!rect || (rect.width === 0 && rect.height === 0)) {
    // 한 줄짜리 칸이면 어디에 있든 첫 줄이자 끝 줄이다 (커서를 맨 앞에 놓은 직후 흔히 이렇게 잰다)
    const single = readText() === '' || box.height < height * 1.5;
    return { first: single || isCaretAtStart(), last: single || isCaretAtEnd(), x: box.left };
  }

  return {
    first: rect.top - box.top < height * 0.75,
    last: box.bottom - rect.bottom < height * 0.75,
    x: rect.left,
  };
}

/**
 * 가로 위치 x 에 가장 가까운 자리에 커서를 놓는다.
 * @param edge 'first' 면 첫 줄, 'last' 면 끝 줄
 */
function focusAt(x, edge) {
  const element = rootRef.value;

  if (!element) {
    return;
  }

  element.focus();

  const box = element.getBoundingClientRect();
  const height = lineHeight();
  const y = edge === 'first' ? box.top + height / 2 : box.bottom - height / 2;
  const range = document.caretRangeFromPoint?.(Math.min(Math.max(x, box.left), box.right - 1), y);

  if (range && element.contains(range.startContainer)) {
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    return;
  }

  focus(edge === 'first' ? 'start' : 'end');
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

/** 입력칸 맨 앞부터 (node, offset) 까지 보이는 글자 수. 줄바꿈과 보이지 않는 글자는 세지 않는다 */
function visibleOffset(node, offset) {
  const range = document.createRange();
  range.selectNodeContents(rootRef.value);
  range.setEnd(node, offset);

  return range.toString().replace(/\u200b/g, '').length;
}

/** 보이는 글자 수로 센 [start, end) 를 고른다. 앞 끝은 다음 조각 맨 앞, 뒤 끝은 앞 조각 맨 끝에 둔다 */
function selectVisible(start, end) {
  const walker = document.createTreeWalker(rootRef.value, NodeFilter.SHOW_TEXT);
  const range = document.createRange();
  let counted = 0;
  let started = false;

  range.selectNodeContents(rootRef.value);

  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const length = node.textContent.length;

    if (!started && start < counted + length) {
      range.setStart(node, start - counted);
      started = true;
    }

    if (started && end <= counted + length) {
      range.setEnd(node, end - counted);
      break;
    }

    counted += length;
  }

  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
}

/**
 * 고른 글자에 글자 색·배경 색을 칠한다. '' 이면 색을 지운다.
 * 서식(굵게 등)은 그대로 두고 색만 바꾼다 — 색은 서식 바깥을 감싼다. 고른 구간은 그대로 남긴다.
 */
function applyColor(color) {
  const root = rootRef.value;
  const selection = window.getSelection();

  if (!root || !selection || selection.rangeCount === 0) {
    return false;
  }

  const range = selection.getRangeAt(0);

  if (range.collapsed || !root.contains(range.startContainer) || !root.contains(range.endContainer)) {
    return false;
  }

  const start = visibleOffset(range.startContainer, range.startOffset);
  const end = visibleOffset(range.endContainer, range.endOffset);
  const markdown = runsToMarkdown(colorRuns(readRuns(root, { root: true }), start, end, color));

  root.innerHTML = inlineToHtml(markdown);
  emit('update:modelValue', markdown);
  selectVisible(start, end);

  return true;
}

/** 블록의 글 전체에 색을 칠한다(블록 메뉴의 "색") */
function colorAll(color) {
  if (!rootRef.value || readText() === '') {
    return false;
  }

  const range = document.createRange();
  range.selectNodeContents(rootRef.value);
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);

  return applyColor(color);
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
  toggleFormat,
  applyLink,
  applyColor,
  colorAll,
  removeBeforeCaret,
  insertFormatted,
  insertLineBreak,
  caretLine,
  focusAt,
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
