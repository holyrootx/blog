/**
 * 편집 화면이 다루는 블록 모델과 마크다운 변환.
 *
 * 저장 형식은 그대로 마크다운이다. 블록은 "쓰는 동안"만 존재하는 표현이고,
 * 저장할 때 마크다운으로 되돌린다. 그래야 이미 있는 글 999건과 공개 화면이
 * 아무것도 바뀌지 않는다.
 *
 * 블록 하나 = 목록 항목 하나다. 노션도 그렇게 다룬다.
 */

import {
  DEFAULT_IMAGE_ALIGN,
  IMAGE_LINE_PATTERN,
  formatImageTitle,
  parseImageTitle,
} from '../../../shared/post/postImageMarkdown.js';
import { isListType, orderedNumberAt } from './postEditorShortcuts.js';
import { DETAILS_OPEN, readDetails } from '../../../shared/post/postDetailsMarkdown.js';
import { isTableStart, readTable, writeTable } from '../../../shared/post/postTableMarkdown.js';

let sequence = 0;

export function createBlock(type = 'paragraph', patch = {}) {
  sequence += 1;

  return {
    id: `block-${sequence}`,
    type,
    text: '',
    level: 2,
    language: '',
    variant: 'tip',
    url: '',
    alt: '',
    // 이미지 폭(%). 0 은 지정 없음이고 100% 로 그려진다.
    // 기본값을 100 으로 두면 안 된다 — 예전 글을 열었다 저장만 해도
    // 모든 이미지에 "100%" 가 붙어 원문이 달라진다
    width: 0,
    // 줄 안에서 사진이 놓이는 자리. 기본은 왼쪽이다
    align: DEFAULT_IMAGE_ALIGN,
    // 원본 픽셀 크기. 비율을 미리 알려 이미지 도착 때 글이 밀리지 않게 한다
    naturalWidth: 0,
    naturalHeight: 0,
    // 고른 파일을 그 자리에서 보여주는 임시 주소. 저장 형식에는 들어가지 않는다
    previewUrl: '',
    // 목록(글머리·번호·할 일)의 들여쓰기 깊이. 0 이 맨 바깥이다
    indent: 0,
    // 할 일의 완료 여부
    checked: false,
    // 토글이 펼쳐져 있는지. 편집 화면에서만 쓰고 저장하지 않는다(공개 글은 접힌 채로 시작한다)
    open: true,
    // 제목 토글의 제목 단계(1~3). 0 이면 보통 토글이다
    toggleLevel: 0,
    // 표의 칸 글자(줄마다 칸 배열), 열마다 정렬('' · left · center · right), 첫 줄이 제목 행인지.
    // 칸을 고칠 때는 배열을 새로 만든다 — 되돌리기 장면과 복제본이 같은 배열을 나눠 쓰기 때문이다
    rows: [],
    columnAlign: [],
    header: true,
    ...patch,
  };
}

/** 여러 줄을 쓸 수 있는 블록. Enter 가 새 블록이 아니라 줄바꿈이 된다 */
export function isMultiline(block) {
  return block.type === 'code' || block.type === 'callout';
}

export function isEmptyBlock(block) {
  if (block.type === 'divider' || block.type === 'table') {
    return false;
  }

  // 이미지는 글자가 비어 있어도 주소가 있으면 내용이 있는 것이다
  if (block.type === 'image') {
    return block.url.trim() === '';
  }

  return block.text.trim() === '';
}

/**
 * 본문에서 첫 번째 이미지 주소를 찾는다.
 *
 * 코드 블록 안의 마크다운 예시는 실제 이미지가 아니므로 건너뛴다. 대표 이미지가
 * 비어 있을 때만 이 값을 사용하고, 사용자가 직접 등록한 주소는 건드리지 않는다.
 */
export function firstImageUrlOf(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n/g, '\n').split('\n');
  let inCode = false;

  for (const line of lines) {
    if (/^```/.test(line.trim())) {
      inCode = !inCode;
      continue;
    }

    if (inCode) {
      continue;
    }

    const image = line.replace(/^\s+/, '').match(IMAGE_LINE_PATTERN);

    if (image) {
      return image[2];
    }
  }

  return '';
}

/** index 블록과 그 안쪽 블록(바로 아래로 더 깊게 들인 블록들)이 끝나는 자리 (끝은 포함하지 않음) */
export function subtreeEnd(blocks, index) {
  const base = blocks[index]?.indent ?? 0;
  let end = index + 1;

  while (end < blocks.length && (blocks[end].indent ?? 0) > base) {
    end += 1;
  }

  return end;
}

/**
 * child 를 parent 안으로 들일 수 있는지. 저장 형식(마크다운)이 담을 수 있는 경우만 된다 —
 * 토글은 무엇이든 품고, 목록 항목은 목록 항목만 품는다.
 */
export function canNest(parent, child) {
  if (!parent) {
    return false;
  }

  return parent.type === 'toggle' || (isListType(parent.type) && isListType(child.type));
}

/**
 * 저장할 수 없는 들여쓰기를 바로잡는다. 옮기기·합치기·끌어 놓기 뒤에 부모 없이 남은 블록
 * (문단 아래 하위 항목처럼)은 담을 수 있는 가장 깊은 자리로 올라간다. 글은 그대로다.
 * 화면과 저장 결과가 어긋나지 않게 하려는 것이다 — 저장하면 어차피 이 모양으로 펴진다.
 */
export function normalizeIndents(blocks) {
  const chain = [];

  blocks.forEach((block) => {
    let depth = Math.min(block.indent ?? 0, chain.length);

    while (depth > 0 && !canNest(chain[depth - 1], block)) {
      depth -= 1;
    }

    if ((block.indent ?? 0) !== depth) {
      block.indent = depth;
    }

    chain.length = depth;
    chain.push(block);
  });

  return blocks;
}

/**
 * at 자리에 블록 묶음을 끼워 넣을 때의 실제 자리와 바깥 깊이 (노션과 같다).
 *  - 펼친 토글 바로 아래: 토글 안쪽 첫 줄
 *  - 접힌 토글 바로 아래: 숨은 안쪽을 건너뛴 토글 다음 줄
 *  - 부모와 첫 하위 항목 사이: 하위 항목과 같은 깊이
 *  - 그 밖: 윗줄과 같은 깊이
 */
export function insertPoint(blocks, at) {
  const above = blocks[at - 1];

  if (!above) {
    return { at, depth: 0 };
  }

  const depth = above.indent ?? 0;

  if (above.type === 'toggle') {
    return above.open ? { at, depth: depth + 1 } : { at: subtreeEnd(blocks, at - 1), depth };
  }

  const below = blocks[at];

  return { at, depth: below && (below.indent ?? 0) > depth ? below.indent : depth };
}

/** 묶음의 첫 블록이 depth 에 오도록 묶음 전체를 같은 만큼 들이거나 내민다 */
export function shiftIndent(group, depth) {
  const delta = depth - (group[0]?.indent ?? 0);

  group.forEach((block) => {
    block.indent = Math.max((block.indent ?? 0) + delta, 0);
  });

  return group;
}

/**
 * blocks[start, end) 묶음을 위나 아래로 한 칸 옮긴 새 배열. 못 옮기면 null.
 * 한 칸은 "옆 블록과 그 안쪽 전부" 다 — 옆 블록의 하위 항목 사이에 끼어들지 않는다 (노션과 같다).
 */
export function moveBlockRange(blocks, start, end, direction) {
  const moving = blocks.slice(start, end);
  const rest = [...blocks.slice(0, start), ...blocks.slice(end)];
  const base = moving[0]?.indent ?? 0;
  let at;

  if (direction < 0) {
    if (start === 0) {
      return null;
    }

    at = start - 1;

    while (at > 0 && (rest[at].indent ?? 0) > base) {
      at -= 1;
    }
  } else {
    if (start >= rest.length) {
      return null;
    }

    at = subtreeEnd(rest, start);
  }

  return [...rest.slice(0, at), ...moving, ...rest.slice(at)];
}

/* ── 마크다운 → 블록 ──────────────────────── */

export function toEditorBlocks(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n/g, '\n').split('\n');
  const blocks = [];

  let paragraph = [];
  // 이어진 목록 줄의 들여쓰기 칸 수. 더 들여 쓴 줄이 한 단계 깊은 항목이다
  let listColumns = [];

  function listLevel(line) {
    const spaces = line.replace(/\t/g, '    ').match(/^ */)[0].length;

    while (listColumns.length > 0 && spaces < listColumns[listColumns.length - 1]) {
      listColumns.pop();
    }

    if (listColumns.length === 0 || spaces > listColumns[listColumns.length - 1]) {
      listColumns.push(spaces);
    }

    return listColumns.length - 1;
  }

  function flushParagraph() {
    if (paragraph.length === 0) {
      return;
    }

    blocks.push(createBlock('paragraph', { text: paragraph.join('\n') }));
    paragraph = [];
  }

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const trimmed = line.trim();
    // 접두어 판정에는 왼쪽 공백만 지운다.
    // 양쪽을 다 지우면 "# " 의 뒤 공백이 사라져 제목으로 안 읽힌다
    const lead = line.replace(/^\s+/, '');

    // 토글: <details><summary>제목</summary> … </details>. 안쪽 블록은 한 단계 깊게 둔다
    if (DETAILS_OPEN.test(trimmed)) {
      flushParagraph();
      listColumns = [];

      const { title, body, end } = readDetails(lines, index);
      // 제목 토글: <summary>## 제목</summary>
      const titled = title.match(/^(#{1,3})\s+(.*)$/);
      blocks.push(createBlock('toggle', {
        text: titled ? titled[2] : title,
        toggleLevel: titled ? titled[1].length : 0,
      }));
      // 안쪽이 비어 있으면 빈 문단을 넣지 않는다
      (body.trim() ? toEditorBlocks(body) : []).forEach((child) => {
        child.indent = (child.indent ?? 0) + 1;
        blocks.push(child);
      });
      index = end;
      continue;
    }

    // 표 (GitHub 표). 제목 행이 빈 표는 제목 행을 끈 표다
    if (isTableStart(lines, index)) {
      flushParagraph();
      listColumns = [];

      const { rows, align, header, end } = readTable(lines, index);
      const columns = Math.max(1, ...rows.map((row) => row.length), align.length);
      blocks.push(createBlock('table', {
        rows: rows.length > 0 ? rows : [Array(columns).fill('')],
        columnAlign: align,
        header,
      }));
      index = end;
      continue;
    }

    const fence = trimmed.match(/^```\s*([\w+-]*)\s*$/);
    if (fence) {
      flushParagraph();

      const codeLines = [];
      index += 1;

      while (index < lines.length && !/^```\s*$/.test(lines[index].trim())) {
        codeLines.push(lines[index]);
        index += 1;
      }

      blocks.push(createBlock('code', { language: fence[1] ?? '', text: codeLines.join('\n') }));
      continue;
    }

    const callout = trimmed.match(/^:::\s*(tip|warning|note)\s*$/);
    if (callout) {
      flushParagraph();

      const calloutLines = [];
      index += 1;

      while (index < lines.length && !/^:::\s*$/.test(lines[index].trim())) {
        calloutLines.push(lines[index]);
        index += 1;
      }

      blocks.push(createBlock('callout', { variant: callout[1], text: calloutLines.join('\n') }));
      continue;
    }

    if (!trimmed) {
      flushParagraph();
      listColumns = [];
      continue;
    }

    if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      flushParagraph();
      blocks.push(createBlock('divider'));
      continue;
    }

    // 내용이 비어도 서식은 살린다. "# " 를 "#" 로 바꿔버리면
    // 열었다 저장만 해도 원문이 달라진다
    const heading = lead.match(/^(#{1,6})\s(.*)$/);
    if (heading) {
      flushParagraph();
      blocks.push(createBlock('heading', { level: heading[1].length, text: heading[2] }));
      continue;
    }

    if (lead.startsWith('>')) {
      flushParagraph();

      // 이어진 > 줄은 인용 하나다. 줄마다 블록을 만들면
      // 열었다 저장만 해도 인용이 여러 개로 쪼개진다
      const quoteLines = [];

      while (index < lines.length && lines[index].replace(/^\s+/, '').startsWith('>')) {
        quoteLines.push(lines[index].replace(/^\s+/, '').replace(/^>\s?/, ''));
        index += 1;
      }

      index -= 1;
      blocks.push(createBlock('quote', { text: quoteLines.join('\n') }));
      continue;
    }

    // 줄 전체가 이미지일 때만 블록으로 만든다. 문장 안에 섞인 이미지는
    // 문단의 인라인 표기로 남겨 둔다 — 블록으로 끌어내면 문장이 끊긴다
    const image = lead.match(IMAGE_LINE_PATTERN);
    if (image) {
      flushParagraph();
      blocks.push(createBlock('image', {
        alt: image[1],
        url: image[2],
        ...parseImageTitle(image[3]),
      }));
      continue;
    }

    // 할 일(- [ ] · - [x])은 글머리 목록보다 먼저 본다
    const todo = lead.match(/^[-*+]\s\[([ xX])\](?:\s(.*))?$/);
    if (todo) {
      flushParagraph();
      blocks.push(createBlock('todo', {
        indent: listLevel(line), checked: todo[1].toLowerCase() === 'x', text: todo[2] ?? '',
      }));
      continue;
    }

    const bullet = lead.match(/^[-*+]\s(.*)$/);
    if (bullet) {
      flushParagraph();
      blocks.push(createBlock('bullet', { indent: listLevel(line), text: bullet[1] }));
      continue;
    }

    const ordered = lead.match(/^\d+\.\s(.*)$/);
    if (ordered) {
      flushParagraph();
      blocks.push(createBlock('ordered', { indent: listLevel(line), text: ordered[1] }));
      continue;
    }

    listColumns = [];
    paragraph.push(trimmed);
  }

  flushParagraph();

  return blocks.length > 0 ? blocks : [createBlock('paragraph')];
}

/* ── 블록 → 마크다운 ──────────────────────── */

export function toMarkdown(blocks) {
  const pieces = [];
  // 깊이마다 앞에 붙일 공백. 하위 항목은 부모 표기("- ", "1. ") 너비만큼 들여 쓴다 —
  // 그래야 GitHub 같은 다른 마크다운 도구에서도 같은 구조로 읽힌다
  const prefixes = [];

  for (let index = 0; index < blocks.length; index += 1) {
    const block = blocks[index];

    // 토글은 바로 아래 더 깊은 블록들을 안쪽으로 품는다. 안쪽은 한 단계 얕게 해서 따로 쓴다
    if (block.type === 'toggle') {
      const base = block.indent ?? 0;
      let end = index + 1;

      while (end < blocks.length && (blocks[end].indent ?? 0) > base) {
        end += 1;
      }

      const children = blocks.slice(index + 1, end).map((child) => ({ ...child, indent: (child.indent ?? 0) - base - 1 }));
      const inner = toMarkdown(children);
      const marker = block.toggleLevel ? `${'#'.repeat(block.toggleLevel)} ` : '';
      const head = `<details>\n<summary>${marker}${(block.text ?? '').replace(/\n+/g, ' ')}</summary>`;

      prefixes.length = 0;
      pieces.push({ block, text: inner ? `${head}\n\n${inner}\n\n</details>` : `${head}\n</details>` });
      index = end - 1;
      continue;
    }

    if (!isListType(block.type)) {
      prefixes.length = 0;
      pieces.push({ block, text: serialize(block, 1) });
      continue;
    }

    const depth = Math.min(block.indent ?? 0, prefixes.length);
    const number = orderedNumberAt(blocks, index);
    const prefix = depth === 0 ? '' : prefixes[depth - 1].childPrefix;
    const marker = block.type === 'ordered' ? `${number}. ` : '- ';

    prefixes.length = depth;
    prefixes.push({ childPrefix: prefix + ' '.repeat(marker.length) });

    pieces.push({ block, text: prefix + serialize(block, number) });
  }

  return pieces
    .map((piece, index) => {
      const previous = pieces[index - 1];

      if (!previous) {
        return piece.text;
      }

      // 목록끼리는 한 줄만 띄운다. 두 줄 띄우면 마크다운에서 목록이 끊긴다.
      // 하위 항목이나 같은 종류(글머리·할 일은 같은 "-" 목록)면 이어 붙인다
      const both = isListType(previous.block.type) && isListType(piece.block.type);
      const nested = (previous.block.indent ?? 0) > 0 || (piece.block.indent ?? 0) > 0;
      const sameKind = (previous.block.type === 'ordered') === (piece.block.type === 'ordered');

      return (both && (nested || sameKind) ? '\n' : '\n\n') + piece.text;
    })
    .join('');
}

function serialize(block, orderedCount) {
  const raw = block.text ?? '';
  // 제목·목록은 마크다운에서 한 줄이어야 한다. 붙여넣기로 줄바꿈이 섞여 들어와도
  // 그대로 내보내면 다음 줄이 제목·목록 밖의 문단이 된다
  const text = ['heading', 'bullet', 'ordered', 'todo'].includes(block.type) ? raw.replace(/\n+/g, ' ') : raw;

  switch (block.type) {
    case 'heading':
      return `${'#'.repeat(block.level)} ${text}`;
    case 'quote':
      // 여러 줄이면 줄마다 > 를 붙여야 한 인용으로 묶인다
      return text.split('\n').map((line) => `> ${line}`).join('\n');
    case 'bullet':
      return `- ${text}`;
    case 'ordered':
      return `${orderedCount}. ${text}`;
    case 'todo':
      return `- [${block.checked ? 'x' : ' '}] ${text}`;
    case 'code':
      return `\`\`\`${block.language ?? ''}\n${text}\n\`\`\``;
    case 'callout':
      return `:::${block.variant}\n${text}\n:::`;
    case 'table':
      return writeTable({ rows: block.rows, align: block.columnAlign, header: block.header });
    case 'image': {
      // 적을 것이 없으면 title 칸을 통째로 뺀다. 폭을 건드린 적 없는 글은
      // 열었다 저장해도 원문 그대로여야 한다
      const title = formatImageTitle(block);

      return `![${block.alt ?? ''}](${block.url ?? ''}${title ? ` "${title}"` : ''})`;
    }
    case 'divider':
      return '---';
    default:
      return text;
  }
}
