/**
 * 토글(<details>) 마크다운 읽기. 편집 화면(postEditorBlocks)과 공개 화면(postContentMapper)이 같이 쓴다.
 *
 * 저장 모양:
 *   <details>
 *   <summary>제목</summary>
 *
 *   안쪽 마크다운
 *
 *   </details>
 * GitHub 같은 다른 마크다운 도구에서도 접고 펴는 글로 보인다.
 */

export const DETAILS_OPEN = /^<details(\s+open)?>$/i;
export const DETAILS_CLOSE = /^<\/details>$/i;

/**
 * lines[start] 의 <details> 부터 짝이 맞는 </details> 까지 읽는다. 안에 토글이 또 있어도 된다.
 * @returns {{ title: string, body: string, end: number }} end 는 </details> 줄의 자리
 */
export function readDetails(lines, start) {
  const inner = [];
  let depth = 1;
  let cursor = start + 1;

  for (; cursor < lines.length; cursor += 1) {
    const trimmed = lines[cursor].trim();

    if (DETAILS_OPEN.test(trimmed)) {
      depth += 1;
    } else if (DETAILS_CLOSE.test(trimmed)) {
      depth -= 1;

      if (depth === 0) {
        break;
      }
    }

    inner.push(lines[cursor]);
  }

  const first = inner.findIndex((line) => line.trim() !== '');
  const summary = first >= 0 ? inner[first].trim().match(/^<summary>(.*)<\/summary>$/i) : null;

  if (summary) {
    inner.splice(first, 1);
  }

  return { title: summary ? summary[1] : '', body: inner.join('\n'), end: cursor };
}
