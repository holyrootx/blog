import { onBeforeUnmount, onMounted, reactive } from 'vue';

/**
 * 글자를 고르면 뜨는 서식 막대의 상태(노션과 같은 동작).
 *
 * 마우스로 끌었으면 손을 뗀 뒤에, Shift+방향키로 골랐으면 바로 뜬다. 한 블록 안에서 고른 경우만이다 —
 * 여러 블록에 걸친 선택에 굵게를 걸면 어느 블록에 무엇이 들어갈지 예측이 안 된다.
 *
 * 막대의 단추를 누르는 순간 선택이 풀리면 안 된다(누를 대상이 사라진다). 단추는 mousedown 을 막고,
 * 링크 입력칸처럼 커서를 가져가야 하는 동안에는 상태를 얼려 두고 고른 구간을 따로 들고 있는다.
 */
export function useTextToolbar({ editorRef }) {
  const toolbar = reactive({
    visible: false,
    // 고른 글자의 화면 위치. 막대가 스스로 위·아래와 좌우를 맞춘다
    anchor: { top: 0, bottom: 0, left: 0, right: 0 },
    blockId: '',
    // 표 칸 안을 골랐으면 그 칸("줄:열"). 블록 전체가 아니라 그 칸에 서식을 건다
    cellKey: '',
    active: { strong: false, em: false, s: false, u: false, code: false, a: false },
    // 다른 서식 안이라 걸 수 없는 서식. 저장 형식은 한 글자에 서식 하나만 담는다
    blocked: { strong: false, em: false, s: false, u: false, code: false, a: false },
    href: '',
    // 고른 글자의 색('red' · 'red-bg'). 색 없으면 ''
    color: '',
    linkEditing: false,
  });

  let pointerDown = false;
  let savedRange = null;
  let frame = 0;

  function textRootOf(node) {
    const element = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement;
    return element?.closest('.block-editor__text') ?? null;
  }

  function hide() {
    toolbar.visible = false;
    toolbar.linkEditing = false;
  }

  function update() {
    // 링크 주소를 치는 동안에는 선택이 입력칸으로 옮겨 가도 막대를 그대로 둔다
    if (toolbar.linkEditing) {
      return;
    }

    const selection = window.getSelection();

    if (!selection || selection.rangeCount === 0 || selection.isCollapsed || pointerDown) {
      hide();
      return;
    }

    const root = textRootOf(selection.anchorNode);

    if (!root || root !== textRootOf(selection.focusNode) || !editorRef.value?.contains(root)) {
      hide();
      return;
    }

    const range = selection.getRangeAt(0);
    const rect = range.getBoundingClientRect();

    // 화면에 그려지지 않는 선택(예: 접힌 요소)에는 띄우지 않는다
    if (rect.width === 0 && rect.height === 0) {
      hide();
      return;
    }

    savedRange = range.cloneRange();
    toolbar.blockId = root.closest('[data-block-id]')?.dataset.blockId ?? '';
    toolbar.cellKey = root.closest('[data-cell]')?.dataset.cell ?? '';
    toolbar.anchor = { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right };

    const start = range.startContainer.nodeType === Node.ELEMENT_NODE
      ? range.startContainer
      : range.startContainer.parentElement;
    const found = start?.closest('strong, b, em, i, s, strike, del, u, code, a');
    const format = found && root.contains(found) ? found : null;
    const KIND_SELECTORS = { strong: 'strong, b', em: 'em, i', s: 's, strike, del', u: 'u', code: 'code', a: 'a' };
    const kinds = Object.keys(KIND_SELECTORS);
    const kind = format ? kinds.find((name) => format.matches(KIND_SELECTORS[name])) : '';

    toolbar.active = Object.fromEntries(kinds.map((name) => [name, kind === name]));
    toolbar.blocked = Object.fromEntries(kinds.map((name) => [name, kind !== '' && kind !== name]));
    toolbar.href = kind === 'a' ? format.getAttribute('data-href') ?? format.getAttribute('href') ?? '' : '';
    const colored = start?.closest('span[data-color]');
    toolbar.color = colored && root.contains(colored) ? colored.getAttribute('data-color') : '';
    toolbar.visible = true;
  }

  function scheduleUpdate() {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(update);
  }

  function onPointerDown(event) {
    // 막대 단추를 누르는 것은 고르기가 아니다
    if (event.target.closest?.('.admin-text-toolbar')) {
      return;
    }

    pointerDown = true;
    hide();
  }

  function onPointerUp() {
    if (!pointerDown) {
      return;
    }

    pointerDown = false;
    scheduleUpdate();
  }

  /** 링크 입력을 시작한다. 고른 구간을 들고 있다가 끝낼 때 되돌린다 */
  function startLink() {
    // 막 서식을 바꾼 직후일 수 있다. 다음 프레임을 기다리지 않고 지금 상태(주소 등)를 읽는다
    cancelAnimationFrame(frame);
    update();

    if (!toolbar.visible || toolbar.blocked.a) {
      return;
    }

    toolbar.linkEditing = true;
  }

  /** 링크 입력을 끝내고 고른 구간을 되살린다. 되살린 구간이 있으면 true */
  function restoreSelection() {
    toolbar.linkEditing = false;

    if (!savedRange) {
      return false;
    }

    const root = textRootOf(savedRange.startContainer);

    if (!root || !root.isConnected) {
      return false;
    }

    root.focus();
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(savedRange);
    return true;
  }

  onMounted(() => {
    document.addEventListener('selectionchange', scheduleUpdate);
    editorRef.value?.addEventListener('mousedown', onPointerDown);
    document.addEventListener('mouseup', onPointerUp);
    // 화면이 움직이면 고른 글자의 위치도 바뀐다
    window.addEventListener('scroll', scheduleUpdate, { capture: true, passive: true });
    window.addEventListener('resize', scheduleUpdate, { passive: true });
  });

  onBeforeUnmount(() => {
    cancelAnimationFrame(frame);
    document.removeEventListener('selectionchange', scheduleUpdate);
    editorRef.value?.removeEventListener('mousedown', onPointerDown);
    document.removeEventListener('mouseup', onPointerUp);
    window.removeEventListener('scroll', scheduleUpdate, { capture: true });
    window.removeEventListener('resize', scheduleUpdate);
  });

  return { toolbar, refreshToolbar: scheduleUpdate, startLink, restoreSelection, hideToolbar: hide };
}
