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
} from '../../../shared/post/postImageMarkdown';

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
    ...patch,
  };
}

/** 여러 줄을 쓸 수 있는 블록. Enter 가 새 블록이 아니라 줄바꿈이 된다 */
export function isMultiline(block) {
  return block.type === 'code' || block.type === 'callout';
}

export function isEmptyBlock(block) {
  if (block.type === 'divider') {
    return false;
  }

  // 이미지는 글자가 비어 있어도 주소가 있으면 내용이 있는 것이다
  if (block.type === 'image') {
    return block.url.trim() === '';
  }

  return block.text.trim() === '';
}

/* ── 마크다운 → 블록 ──────────────────────── */

export function toEditorBlocks(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n/g, '\n').split('\n');
  const blocks = [];

  let paragraph = [];

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

    const bullet = lead.match(/^[-*+]\s(.*)$/);
    if (bullet) {
      flushParagraph();
      blocks.push(createBlock('bullet', { text: bullet[1] }));
      continue;
    }

    const ordered = lead.match(/^\d+\.\s(.*)$/);
    if (ordered) {
      flushParagraph();
      blocks.push(createBlock('ordered', { text: ordered[1] }));
      continue;
    }

    paragraph.push(trimmed);
  }

  flushParagraph();

  return blocks.length > 0 ? blocks : [createBlock('paragraph')];
}

/* ── 블록 → 마크다운 ──────────────────────── */

export function toMarkdown(blocks) {
  const pieces = [];
  let orderedCount = 0;

  blocks.forEach((block, index) => {
    const previous = blocks[index - 1];

    // 번호 목록은 이어진 동안만 번호가 올라간다
    orderedCount = block.type === 'ordered' && previous?.type === 'ordered' ? orderedCount + 1 : 1;

    pieces.push({ block, text: serialize(block, orderedCount) });
  });

  return pieces
    .map((piece, index) => {
      const previous = pieces[index - 1];

      if (!previous) {
        return piece.text;
      }

      // 목록끼리는 한 줄만 띄운다. 두 줄 띄우면 마크다운에서 목록이 끊긴다
      const isSameList = isListType(previous.block.type) && previous.block.type === piece.block.type;

      return (isSameList ? '\n' : '\n\n') + piece.text;
    })
    .join('');
}

function isListType(type) {
  return type === 'bullet' || type === 'ordered';
}

function serialize(block, orderedCount) {
  const text = block.text ?? '';

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
    case 'code':
      return `\`\`\`${block.language ?? ''}\n${text}\n\`\`\``;
    case 'callout':
      return `:::${block.variant}\n${text}\n:::`;
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
