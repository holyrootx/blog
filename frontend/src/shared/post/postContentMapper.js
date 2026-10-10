/**
 * 마크다운 원문을 화면이 그릴 블록 배열로 바꾼다.
 *
 * HTML 문자열을 만들어 v-html 로 꽂지 않는다. 본문은 지금 관리자만 쓰지만,
 * 렌더 경로에 HTML 주입 구멍을 만들어 두면 나중에 다른 출처(댓글·외부 임포트)가
 * 붙는 순간 그대로 사고가 된다. 토큰 배열로 넘겨 Vue 가 이스케이프하게 둔다.
 *
 * 블록: heading · paragraph · quote · code · list(중첩·할 일) · toggle · table · divider · callout · image
 * 인라인: text · bold · italic · strike · underline · code · link (토큰마다 color 가 붙을 수 있다)
 */

import { safeHref } from '../../features/admin/data/inlineMarkdown.js';
import { IMAGE_LINE_PATTERN, parseImageTitle } from './postImageMarkdown.js';
import { DETAILS_OPEN, readDetails } from './postDetailsMarkdown.js';
import { isTableStart, readTable } from './postTableMarkdown.js';
import { splitColorSpans } from './postInlineColors.js';

const CALLOUT_VARIANTS = ['tip', 'warning', 'note'];

/**
 * @param context 토글 안을 다시 읽을 때 넘긴다. 제목 번호(목차 id)가 겹치지 않게 이어 센다
 */
export function toPostBody(content, context = { headingIndex: 1 }) {
  const source = String(content ?? '').replace(/\r\n/g, '\n');

  if (!source.trim()) {
    return [];
  }

  const lines = source.split('\n');
  const blocks = [];

  let paragraphLines = [];
  let quoteLines = [];
  // 이어진 목록 줄들. 들여쓰기로 목록 안의 목록을 만든다
  let listEntries = [];

  function flushParagraph() {
    if (paragraphLines.length === 0) {
      return;
    }

    // 빈 줄 없이 이어진 줄은 한 문단으로 합친다 (마크다운 기본 규칙)
    blocks.push({ type: 'paragraph', inline: toInline(paragraphLines.join(' ')) });
    paragraphLines = [];
  }

  function flushQuote() {
    if (quoteLines.length === 0) {
      return;
    }

    // 연속된 > 줄은 인용 하나다. 줄마다 상자를 만들면 세 줄 인용이 상자 세 개가 된다
    blocks.push({ type: 'quote', inline: toInline(quoteLines.join(' ')) });
    quoteLines = [];
  }

  function flushList() {
    if (listEntries.length === 0) {
      return;
    }

    buildLists(listEntries).forEach((list) => blocks.push({ type: 'list', ...list }));
    listEntries = [];
  }

  function flushAll() {
    flushParagraph();
    flushQuote();
    flushList();
  }

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index];
    const line = rawLine.trim();

    // 토글: <details><summary>제목</summary> … </details>. 안쪽은 본문처럼 다시 읽는다
    if (DETAILS_OPEN.test(line)) {
      flushAll();

      const { title, body, end } = readDetails(lines, index);
      // 제목 토글(<summary>## 제목</summary>)은 제목처럼 목차에 들어간다. 번호는 안쪽보다 먼저 받는다
      const titled = title.match(/^(#{1,3})\s+(.*)$/);
      const toggle = { type: 'toggle', level: 0, inline: toInline(titled ? titled[2] : title) };

      if (titled) {
        toggle.level = titled[1].length;
        toggle.id = `section-${context.headingIndex}`;
        toggle.text = toPlainText(titled[2]);
        context.headingIndex += 1;
      }

      toggle.children = toPostBody(body, context);
      blocks.push(toggle);
      index = end;
      continue;
    }

    // 표 (GitHub 표). 칸마다 인라인 서식을 읽는다
    if (isTableStart(lines, index)) {
      flushAll();

      const { rows, align, header, end } = readTable(lines, index);
      blocks.push({ type: 'table', header, align, rows: rows.map((row) => row.map((cell) => toInline(cell))) });
      index = end;
      continue;
    }

    // ``` 코드 블록 — 닫는 줄까지 원문 그대로 모은다.
    // 안쪽은 마크다운으로 해석하지 않는다
    const fence = line.match(/^```\s*([\w+-]*)\s*$/);
    if (fence) {
      flushAll();

      const language = fence[1] ?? '';
      const codeLines = [];
      index += 1;

      while (index < lines.length && !/^```\s*$/.test(lines[index].trim())) {
        codeLines.push(lines[index]);
        index += 1;
      }

      blocks.push({ type: 'code', language, code: codeLines.join('\n') });
      continue;
    }

    // ::: 콜아웃 — 닫는 ::: 까지
    const callout = line.match(/^:::\s*(\w+)\s*$/);
    if (callout && CALLOUT_VARIANTS.includes(callout[1])) {
      flushAll();

      const variant = callout[1];
      const calloutLines = [];
      index += 1;

      while (index < lines.length && !/^:::\s*$/.test(lines[index].trim())) {
        calloutLines.push(lines[index].trim());
        index += 1;
      }

      blocks.push({
        type: 'callout',
        variant,
        inline: toInline(calloutLines.filter(Boolean).join(' ')),
      });
      continue;
    }

    if (!line) {
      flushAll();
      continue;
    }

    // --- 구분선. 목록의 - 와 헷갈리지 않도록 세 개 이상만 본다
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line)) {
      flushAll();
      blocks.push({ type: 'divider' });
      continue;
    }

    // # ~ ###### — 단계를 버리지 않는다.
    // 전부 h2 로 뭉개면 #을 쓰든 ###을 쓰든 화면이 같아진다
    // 내용이 없어도 제목이다. 편집기는 빈 제목을 "## " 로 저장하는데,
    // 여기서 안 받아 주면 문단으로 흘러가 "##" 이 글자 그대로 화면에 찍힌다.
    // 뒤쪽을 통째로 없는 셈 치는 건 저장할 때 공백이 잘려 "##" 만 남는 경우까지 받기 위해서다
    const heading = line.match(/^(#{1,6})(?:\s+(.*))?$/);
    if (heading) {
      flushAll();

      const text = (heading[2] ?? '').trim();

      blocks.push({
        type: 'heading',
        level: heading[1].length,
        id: `section-${context.headingIndex}`,
        // 목차는 서식 없는 문자열이 필요하다
        text: toPlainText(text),
        inline: toInline(text),
      });

      context.headingIndex += 1;
      continue;
    }

    // 줄 전체가 이미지면 블록으로 뽑는다. 문장에 섞인 것은 문단 안 인라인으로 남는다
    const image = line.match(IMAGE_LINE_PATTERN);
    if (image) {
      flushAll();

      const src = safeHref(image[2]);

      // 허용하지 않는 주소면 아무것도 그리지 않는다.
      // 깨진 이미지 아이콘보다 없는 편이 낫다
      if (src) {
        blocks.push({ type: 'image', src, alt: image[1], ...parseImageTitle(image[3]) });
      }

      continue;
    }

    if (line.startsWith('>')) {
      flushParagraph();
      flushList();
      quoteLines.push(line.replace(/^>\s?/, ''));
      continue;
    }

    // 할 일: - [ ] 글 · - [x] 글 (GFM 과 같은 표기). 글머리 목록보다 먼저 본다
    const todo = line.match(/^[-*+]\s+\[([ xX])\](?:\s+(.*))?$/);
    const bullet = line.match(/^[-*+]\s+(.+)$/);
    const numbered = line.match(/^\d+\.\s+(.+)$/);

    if (todo || bullet || numbered) {
      flushParagraph();
      flushQuote();

      listEntries.push({
        // 앞 공백 수로 깊이를 정한다. 탭은 4칸으로 센다
        spaces: rawLine.replace(/\t/g, '    ').match(/^ */)[0].length,
        ordered: Boolean(numbered) && !todo,
        checked: todo ? todo[1].toLowerCase() === 'x' : null,
        text: todo ? (todo[2] ?? '') : (bullet ?? numbered)[1],
      });
      continue;
    }

    flushQuote();
    flushList();
    paragraphLines.push(line);
  }

  flushAll();

  return blocks;
}

/**
 * 이어진 목록 줄들을 목록 안의 목록(트리)으로 만든다.
 *
 * 더 들여 쓴 줄은 바로 위 항목의 하위 목록이 된다. 같은 깊이에서 글머리와 번호가 바뀌면
 * 목록을 나눈다. 할 일 항목은 글머리 목록의 항목이고 checked 가 true·false 다(아니면 null).
 *
 * @returns {{ ordered: boolean, items: { inline, checked, children }[] }[]} 맨 바깥 목록들
 */
function buildLists(entries) {
  const roots = [];
  // { spaces, list, parent } — parent 는 이 목록을 품은 항목(맨 바깥이면 null)
  const stack = [];

  const newList = (ordered, parent) => {
    const list = { ordered, items: [] };
    (parent ? parent.children : roots).push(list);
    return list;
  };

  entries.forEach((entry) => {
    while (stack.length > 0 && entry.spaces < stack[stack.length - 1].spaces) {
      stack.pop();
    }

    let frame = stack[stack.length - 1];

    if (!frame) {
      frame = { spaces: entry.spaces, list: newList(entry.ordered, null), parent: null };
      stack.push(frame);
    } else if (entry.spaces > frame.spaces && frame.list.items.length > 0) {
      const parent = frame.list.items[frame.list.items.length - 1];
      frame = { spaces: entry.spaces, list: newList(entry.ordered, parent), parent };
      stack.push(frame);
    } else if (frame.list.ordered !== entry.ordered) {
      // 같은 깊이에서 글머리·번호가 바뀌면 목록을 나눈다
      frame.list = newList(entry.ordered, frame.parent);
    }

    frame.list.items.push({ inline: toInline(entry.text), checked: entry.checked, children: [] });
  });

  return roots;
}

export function toPostToc(body) {
  return body
    .filter((block) => block.type === 'heading' || (block.type === 'toggle' && block.level > 0))
    .map((block) => ({
      id: block.id,
      text: block.text,
      level: block.level,
    }));
}

/**
 * 인라인 서식을 토큰 배열로 쪼갠다.
 * `코드`를 먼저 떼어내 그 안의 별표·대괄호가 서식으로 해석되지 않게 한다.
 */
export function toInline(text) {
  // 글자 색·배경 색 구간은 안쪽 서식을 그대로 읽고 토큰마다 색을 붙인다
  return splitColorSpans(text).flatMap((part) => (part.color
    ? formatTokens(part.text).map((token) => ({ ...token, color: part.color }))
    : formatTokens(part.text)));
}

function formatTokens(text) {
  const source = String(text ?? '');

  if (!source) {
    return [];
  }

  // 밑줄은 마크다운 표준 문법이 없어 <u>…</u> 로 저장한다. 정확히 그 모양만 밑줄로 읽고,
  // 그 밖의 HTML 은 지금처럼 글자로 둔다(태그로 그리지 않는다)
  const pattern = /(`[^`]+`)|(\*\*[^*]+\*\*)|(~~[^~]+~~)|(<u>[^<]+<\/u>)|(\*[^*]+\*)|(\[[^\]]+\]\([^)\s]+\))/g;
  const tokens = [];
  let lastIndex = 0;

  for (const match of source.matchAll(pattern)) {
    if (match.index > lastIndex) {
      tokens.push({ type: 'text', text: source.slice(lastIndex, match.index) });
    }

    const [value] = match;

    if (value.startsWith('`')) {
      tokens.push({ type: 'code', text: value.slice(1, -1) });
    } else if (value.startsWith('**')) {
      tokens.push({ type: 'bold', text: value.slice(2, -2) });
    } else if (value.startsWith('~~')) {
      tokens.push({ type: 'strike', text: value.slice(2, -2) });
    } else if (value.startsWith('<u>')) {
      tokens.push({ type: 'underline', text: value.slice(3, -4) });
    } else if (value.startsWith('*')) {
      tokens.push({ type: 'italic', text: value.slice(1, -1) });
    } else {
      const link = value.match(/^\[([^\]]+)\]\(([^)\s]+)\)$/);
      const href = safeHref(link[2]);

      // 허용하지 않는 주소면 링크로 만들지 않고 글자로만 남긴다
      tokens.push(href
        ? { type: 'link', text: link[1], href }
        : { type: 'text', text: link[1] });
    }

    lastIndex = match.index + value.length;
  }

  if (lastIndex < source.length) {
    tokens.push({ type: 'text', text: source.slice(lastIndex) });
  }

  return tokens;
}

/** 목차·검색처럼 서식이 필요 없는 곳에서 쓸 순수 문자열 */
function toPlainText(text) {
  return toInline(text).map((token) => token.text).join('');
}
