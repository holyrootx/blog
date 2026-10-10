/**
 * 글자 색·배경 색 (노션과 같은 아홉 가지).
 *
 * 저장은 <span data-color="red">글</span> · <span data-color="red-bg">글</span> 이다.
 * 노션처럼 한 구간에는 글자 색이나 배경 색 중 하나만 둔다. 안쪽에 굵게·링크 같은 서식은 들어갈 수 있다.
 *
 * 마크다운에 색 문법이 없어 HTML 모양을 빌렸다. 다른 마크다운 도구에서는 색만 빠지고 글은 그대로 보인다.
 * 공개 화면은 이 모양을 정확히 맞출 때만 색으로 읽는다 — 이름은 아래 목록에 있는 것만 받고,
 * style 같은 다른 속성은 받지 않는다(임의의 CSS 를 본문에 넣는 통로가 되지 않게).
 */
export const INLINE_COLORS = [
  { id: 'gray', label: '회색' },
  { id: 'brown', label: '갈색' },
  { id: 'orange', label: '주황' },
  { id: 'yellow', label: '노랑' },
  { id: 'green', label: '초록' },
  { id: 'blue', label: '파랑' },
  { id: 'purple', label: '보라' },
  { id: 'pink', label: '분홍' },
  { id: 'red', label: '빨강' },
];

const NAMES = INLINE_COLORS.map((color) => color.id).join('|');
const COLOR_NAME = new RegExp(`^(?:${NAMES})(?:-bg)?$`);

export function isInlineColor(value) {
  return COLOR_NAME.test(String(value ?? ''));
}

/** 색 구간 하나를 저장 형식으로 */
export function wrapColor(text, color) {
  return text && isInlineColor(color) ? `<span data-color="${color}">${text}</span>` : text;
}

/**
 * 글을 색 구간과 나머지로 나눈다. 코드(`…`) 안에 적힌 <span> 은 글자 그대로라서 색으로 읽지 않는다.
 *
 * @returns {{ text: string, color: string }[]} color 가 '' 이면 색 없는 구간
 */
export function splitColorSpans(text) {
  const source = String(text ?? '');
  const codeRanges = [...source.matchAll(/`[^`]+`/g)].map((match) => [match.index, match.index + match[0].length]);
  const pattern = new RegExp(`<span data-color="((?:${NAMES})(?:-bg)?)">([\\s\\S]+?)</span>`, 'g');
  const parts = [];
  let last = 0;

  for (const match of source.matchAll(pattern)) {
    if (match.index < last || codeRanges.some(([start, end]) => match.index > start && match.index < end)) {
      continue;
    }

    if (match.index > last) {
      parts.push({ text: source.slice(last, match.index), color: '' });
    }

    parts.push({ text: match[2], color: match[1] });
    last = match.index + match[0].length;
  }

  if (last < source.length) {
    parts.push({ text: source.slice(last), color: '' });
  }

  return parts;
}
