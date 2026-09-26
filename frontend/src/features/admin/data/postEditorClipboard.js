import { toEditorBlocks } from './postEditorBlocks';

/**
 * 편집기에서 잘라내거나 복사한 블록을 담아 두는 곳.
 *
 * <p><b>편집기 컴포넌트 밖에 둔다.</b> 안에 두면 인스턴스마다 따로 생겨서, 글 A 에서
 * 복사하고 글 B 로 이동하면 비어 버린다. 그러면 붙여넣기가 그냥 빠져나가고 브라우저
 * 기본 동작이 일어나 마크다운이 글자 그대로 본문에 박힌다 — 코드 블록이 사라지고
 * ``` 가 눈에 보이던 것이 이 때문이다.</p>
 *
 * <p>새로고침하면 여기도 비는데, 그때는 시스템 클립보드에 남은 마크다운을
 * {@link readClipboardBlocks} 로 되살린다.</p>
 */
let held = [];

export function holdBlocks(blocks) {
  // 미리보기 주소는 그 화면에서만 뜻이 있는 값이라 들고 다니지 않는다
  held = blocks.map((block) => ({ ...block, previewUrl: '' }));
}

export function heldBlocks() {
  return held;
}

export function hasHeldBlocks() {
  return held.length > 0;
}

/**
 * 담아 둔 것이 없을 때 시스템 클립보드의 글을 블록으로 되돌린다.
 *
 * <p>여기로 오는 경우는 셋이다 — 새로고침한 뒤, 편집기 밖(에디터·메모장)에서 복사해
 * 온 경우, 그리고 이 창이 아닌 다른 창에서 복사한 경우.</p>
 *
 * <p>담아 둔 블록보다 손해가 있다. 이미지 폭·정렬처럼 마크다운에 안 실리는 값은
 * 기본값으로 돌아간다. 그래도 글자로 박히는 것보다는 낫다.</p>
 *
 * @returns 블록 배열. 마크다운이 아니거나 비어 있으면 빈 배열
 */
export function readClipboardBlocks(text) {
  const trimmed = (text ?? '').trim();

  if (trimmed === '') {
    return [];
  }

  const blocks = toEditorBlocks(trimmed);

  // 단락 하나로만 풀렸고 원문에 서식 기호가 없으면 그냥 글자다. 블록으로 만들 것이
  // 아니라 커서 자리에 글자로 들어가야 하므로 브라우저에 맡긴다
  const looksLikeMarkdown = /(^|\n)\s*(#{1,6}\s|```|[-*]\s|>\s|!\[)/.test(trimmed);

  if (blocks.length <= 1 && !looksLikeMarkdown) {
    return [];
  }

  return blocks;
}
