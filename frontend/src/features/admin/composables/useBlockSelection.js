import { nextTick, ref } from 'vue';
import { notifyError } from '../../../shared/toast/toastStore';
import { createBlock, insertPoint, moveBlockRange, shiftIndent, toMarkdown } from '../data/postEditorBlocks';
import { hasHeldBlocks, heldBlocks, holdBlocks, readClipboardBlocks } from '../data/postEditorClipboard';

export function useBlockSelection({ blocks, editorRef, sync, isUploading, isHidden = () => false }) {
  // 드래그로 옮기는 중인 블록과 놓을 자리
  const draggingId = ref('');
  const dropIndex = ref(-1);
  // 여러 블록 선택 (핸들 클릭, Shift+클릭)
  const selectedIds = ref([]);
  const selectionAnchorId = ref('');
  const selectionCursorId = ref('');
  // 잘라낸 뒤 화살표로 옮기는 삽입선. 0은 첫 블록 위, length는 마지막 블록 아래다
  const keyboardInsertIndex = ref(-1);


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

  /** from 에서 direction 쪽으로 화면에 보이는 다음 블록. 없으면 from 그대로 */
  function visibleStep(from, direction) {
    for (let cursor = from + direction; cursor >= 0 && cursor < blocks.value.length; cursor += direction) {
      if (!isHidden(blocks.value[cursor].id)) {
        return cursor;
      }
    }

    return from;
  }

  function moveSelectionCursor(direction) {
    const current = blockIndex(selectionCursorId.value || selectedIds.value[0]);
    const target = current < 0 ? 0 : visibleStep(current, direction);

    if (blocks.value[target]) {
      selectOnly(blocks.value[target].id);
    }
  }

  function extendSelection(direction) {
    const anchor = blockIndex(selectionAnchorId.value || selectedIds.value[0]);
    const cursor = blockIndex(selectionCursorId.value || selectedIds.value[selectedIds.value.length - 1]);
    const target = cursor < 0 ? -1 : visibleStep(cursor, direction);

    if (anchor < 0 || target < 0) {
      return;
    }

    const [from, to] = anchor < target ? [anchor, target] : [target, anchor];
    selectedIds.value = blocks.value.slice(from, to + 1).map((block) => block.id);
    selectionCursorId.value = blocks.value[target].id;
  }

  function moveInsertCursor(direction) {
    let next = keyboardInsertIndex.value;

    // 접힌 토글 안쪽(숨은 블록 앞자리)은 건너뛴다
    do {
      next = Math.min(Math.max(next + direction, 0), blocks.value.length);
    } while (next > 0 && next < blocks.value.length && isHidden(blocks.value[next].id));

    keyboardInsertIndex.value = next;
  }

  /**
   * 고른 블록과 그 안쪽 블록(바로 아래로 더 깊게 들인 블록들)의 id.
   * 토글·목록 부모를 옮기거나 지우면 딸린 블록이 따라가야 한다 (노션과 같다).
   */
  function withDescendants() {
    const selected = new Set(selectedIds.value);
    const result = new Set();
    let parentIndent = null;

    blocks.value.forEach((block) => {
      const indent = block.indent ?? 0;

      if (parentIndent !== null && indent > parentIndent) {
        result.add(block.id);
        return;
      }

      parentIndent = null;

      if (selected.has(block.id)) {
        result.add(block.id);
        parentIndent = indent;
      }
    });

    return result;
  }

  function selectedBlocks() {
    const selected = withDescendants();

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
   * <p>현재 클립보드가 내부 복사 내용과 같을 때만 담아 둔 블록을 쓴다. 그 외에는 글을 블록으로
   * 되돌려 본다 — 새로고침한 뒤나 편집기 밖에서 복사해 온 경우다. 둘 다 안 되면
   * 막지 않고 브라우저에 맡긴다. 글자를 붙여 넣으려던 것일 수 있어서다.</p>
   */
  function onBlockPaste(event) {
    // 코드 칸(입력 요소) 안에서는 붙여넣은 글을 그대로 둔다. #, 백틱, 빈 줄도 코드의 일부다.
    // 마크다운으로 풀면 "# 주석" 이 코드 밖의 제목이 된다
    if (event.target instanceof HTMLTextAreaElement || event.target instanceof HTMLInputElement) {
      return;
    }

    const text = event.clipboardData?.getData('text/plain') ?? '';
    const pasting = hasHeldBlocks() && text === toMarkdown(heldBlocks())
      ? heldBlocks()
      : readClipboardBlocks(text);

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
    const selected = withDescendants();
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

  async function pasteBlocks() {
    try {
      const text = await navigator.clipboard.readText();
      if (!text) return;
      const source = hasHeldBlocks() && text === toMarkdown(heldBlocks())
        ? heldBlocks()
        : readClipboardBlocks(text);
      insertBlocks(source.length ? source : [createBlock('paragraph', { text })]);
    } catch {
      notifyError('클립보드를 읽지 못했습니다. 본문을 클릭한 뒤 다시 붙여 넣어 주세요.');
    }
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

    // 놓는 자리의 깊이에 맞춘다. 토글 안에 붙여 넣으면 토글 안쪽이 된다
    const point = insertPoint(blocks.value, at);
    const pasted = shiftIndent(source.map(cloneBlock), point.depth);
    blocks.value.splice(point.at, 0, ...pasted);
    keyboardInsertIndex.value = -1;
    selectedIds.value = pasted.map((block) => block.id);
    selectionAnchorId.value = pasted[0]?.id ?? '';
    selectionCursorId.value = pasted[pasted.length - 1]?.id ?? '';

    sync();
    focusEditor();
  }

  /** 고른 블록을 바로 아래에 복제하고, 복제본을 고른다 (노션 ⌘D) */
  function duplicateSelected() {
    const selected = withDescendants();
    const indexes = blocks.value
      .map((block, index) => (selected.has(block.id) ? index : -1))
      .filter((index) => index >= 0);

    if (indexes.length === 0 || selectedBlocks().some((block) => isUploading(block))) {
      return;
    }

    const copies = selectedBlocks().map(cloneBlock);
    blocks.value.splice(Math.max(...indexes) + 1, 0, ...copies);
    selectedIds.value = copies.map((block) => block.id);
    selectionAnchorId.value = copies[0].id;
    selectionCursorId.value = copies[copies.length - 1].id;

    sync();
    focusEditor();
  }

  function moveSelectedBlocks(direction) {
    const selected = withDescendants();
    const indexes = blocks.value
      .map((block, index) => (selected.has(block.id) ? index : -1))
      .filter((index) => index >= 0);

    if (indexes.length === 0) {
      return;
    }

    // 떨어져 고른 블록은 한 덩어리로 모은 뒤 옮긴다
    const first = indexes[0];
    const moving = blocks.value.filter((block) => selected.has(block.id));
    const gathered = [
      ...blocks.value.slice(0, first).filter((block) => !selected.has(block.id)),
      ...moving,
      ...blocks.value.slice(first).filter((block) => !selected.has(block.id)),
    ];
    const next = moveBlockRange(gathered, first, first + moving.length, direction);

    if (!next || next.every((block, index) => block.id === blocks.value[index]?.id)) {
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

    const selected = withDescendants();
    const moving = blocks.value.filter((block) => selected.has(block.id));
    const selectedBefore = blocks.value
      .slice(0, dropIndex.value)
      .filter((block) => selected.has(block.id)).length;
    const rest = blocks.value.filter((block) => !selected.has(block.id));
    const point = insertPoint(rest, Math.min(Math.max(dropIndex.value - selectedBefore, 0), rest.length));
    const next = [...rest.slice(0, point.at), ...moving, ...rest.slice(point.at)];

    // 제자리에 놓았으면 깊이도 건드리지 않는다
    if (!next.every((block, index) => block.id === blocks.value[index]?.id)) {
      // 놓은 자리의 깊이에 맞춘다. 펼친 토글 바로 아래에 놓으면 토글 안으로 들어간다
      shiftIndent(moving, point.depth);
      blocks.value = next;
      sync();
    }

    resetDrag();
  }

  function resetDrag() {
    draggingId.value = '';
    dropIndex.value = -1;
  }

  return {
    draggingId,
    dropIndex,
    selectedIds,
    keyboardInsertIndex,
    selectOnly,
    selectAllBlocks,
    toggleSelect,
    clearSelection,
    focusEditor,
    selectImageBlock,
    moveSelectionCursor,
    extendSelection,
    moveInsertCursor,
    copySelected,
    onBlockCopy,
    onBlockCut,
    onBlockPaste,
    removeSelectedBlocks,
    pasteBlocks,
    moveSelectedBlocks,
    duplicateSelected,
    withDescendants,
    onDragStart,
    onDragOver,
    onDrop,
    resetDrag,
  };
}
