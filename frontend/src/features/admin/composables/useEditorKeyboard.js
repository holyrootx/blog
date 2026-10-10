/**
 * 편집기 전체에서 받는 키. 블록 하나 안의 키(Enter 로 나누기 등)는 편집기가 블록마다 따로 받는다.
 *
 * - Escape: 글자 커서에서 누르면 그 블록을 고르고, 블록 모드에서 다시 누르면 빠져나온다
 * - 블록 모드: 전체 선택·복사·잘라내기·붙여넣기·지우기·위아래 이동
 * - Cmd/Ctrl+Z·Y: 브라우저 기본 되돌리기 대신 편집기의 되돌리기
 */
export function useEditorKeyboard({ editorRef, selection, undo, redo, focusBlock }) {
  const {
    selectedIds,
    keyboardInsertIndex,
    selectOnly,
    selectAllBlocks,
    clearSelection,
    focusEditor,
    moveSelectionCursor,
    extendSelection,
    moveInsertCursor,
    copySelected,
    pasteBlocks,
    removeSelectedBlocks,
    moveSelectedBlocks,
    duplicateSelected,
  } = selection;

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

    if (inBlockMode && handleBlockModeKey(event, key, command)) {
      return;
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

  /** 블록을 고른 상태에서 받은 키. 처리했으면 true */
  function handleBlockModeKey(event, key, command) {
    if (command && key === 'a') {
      event.preventDefault();
      selectAllBlocks();
      return true;
    }

    if (command && (key === 'c' || key === 'x')) {
      event.preventDefault();
      copySelected(key === 'x');
      return true;
    }

    if (command && key === 'v') {
      event.preventDefault();
      pasteBlocks();
      return true;
    }

    // 노션과 같은 단축키: ⌘D 복제, ⌘⇧↑·↓ 옮기기
    if (command && key === 'd' && selectedIds.value.length > 0) {
      event.preventDefault();
      duplicateSelected();
      return true;
    }

    if (command && event.shiftKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown')
      && selectedIds.value.length > 0) {
      event.preventDefault();
      moveSelectedBlocks(event.key === 'ArrowUp' ? -1 : 1);
      return true;
    }

    if ((event.key === 'Delete' || event.key === 'Backspace') && selectedIds.value.length > 0) {
      event.preventDefault();
      removeSelectedBlocks();
      return true;
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

      return true;
    }

    if (event.key === 'Enter' && selectedIds.value.length === 1) {
      event.preventDefault();
      const [id] = selectedIds.value;

      clearSelection();
      focusBlock(id, 'start');
      return true;
    }

    return false;
  }

  return { onEditorKeydown };
}
