/**
 * 마크다운 원문을 화면이 그릴 블록 배열로 바꾼다.
 *
 * HTML 문자열을 만들어 v-html 로 꽂지 않는다. 본문은 지금 관리자만 쓰지만,
 * 렌더 경로에 HTML 주입 구멍을 만들어 두면 나중에 다른 출처(댓글·외부 임포트)가
 * 붙는 순간 그대로 사고가 된다. 토큰 배열로 넘겨 Vue 가 이스케이프하게 둔다.
 *
 * 블록: heading · paragraph · quote · code · list · divider · callout · image
 * 인라인: text · bold · italic · code · link
 */

import { safeHref } from '../../features/admin/data/inlineMarkdown';
import { IMAGE_LINE_PATTERN, parseImageTitle } from './postImageMarkdown';

const CALLOUT_VARIANTS = ['tip', 'warning', 'note'];

export function toPostBody(content) {
  const source = String(content ?? '').replace(/\r\n/g, '\n');

  if (!source.trim()) {
    return [];
  }

  const lines = source.split('\n');
  const blocks = [];

  let paragraphLines = [];
  let quoteLines = [];
  let listItems = [];
  let listOrdered = false;
  let headingIndex = 1;

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
    if (listItems.length === 0) {
      return;
    }

    blocks.push({ type: 'list', ordered: listOrdered, items: listItems.map(toInline) });
    listItems = [];
  }

  function flushAll() {
    flushParagraph();
    flushQuote();
    flushList();
  }

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index];
    const line = rawLine.trim();

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
        id: `section-${headingIndex}`,
        // 목차는 서식 없는 문자열이 필요하다
        text: toPlainText(text),
        inline: toInline(text),
      });

      headingIndex += 1;
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

    const bullet = line.match(/^[-*+]\s+(.+)$/);
    const numbered = line.match(/^\d+\.\s+(.+)$/);

    if (bullet || numbered) {
      flushParagraph();
      flushQuote();

      const ordered = Boolean(numbered);

      // 글머리 기호가 바뀌면 목록도 나눈다
      if (listItems.length > 0 && listOrdered !== ordered) {
        flushList();
      }

      listOrdered = ordered;
      listItems.push((bullet ?? numbered)[1]);
      continue;
    }

    flushQuote();
    flushList();
    paragraphLines.push(line);
  }

  flushAll();

  return blocks;
}

export function toPostToc(body) {
  return body
    .filter((block) => block.type === 'heading')
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
  const source = String(text ?? '');

  if (!source) {
    return [];
  }

  const pattern = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*]+\*)|(\[[^\]]+\]\([^)\s]+\))/g;
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
