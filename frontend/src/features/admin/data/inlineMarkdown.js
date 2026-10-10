/**
 * 인라인 서식과 HTML 사이의 변환.
 *
 * 편집 중인 블록은 contenteditable 이라 화면에는 <strong> 같은 태그가 들어가고,
 * 저장할 때는 다시 마크다운으로 되돌린다. 저장 형식은 끝까지 마크다운이다.
 *
 * 태그 종류를 여기서만 정해두면 "화면에는 되는데 저장하면 사라지는" 서식이 안 생긴다.
 */

import { isInlineColor, splitColorSpans, wrapColor } from '../../../shared/post/postInlineColors.js';

const ESCAPE = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeHtml(text) {
  // 따옴표까지 막아야 한다. href="..." 안에 들어가므로
  // 따옴표가 살아 있으면 속성을 빠져나갈 수 있다
  return String(text ?? '').replace(/[&<>"']/g, (char) => ESCAPE[char]);
}

/**
 * 링크로 허용할 주소인지.
 * javascript: 같은 주소는 누르는 순간 코드가 도는 통로가 된다.
 * 본문은 지금 관리자만 쓰지만, 검사 자리를 비워두면 나중에 다른 출처가 붙는 순간 뚫린다.
 */
export function safeHref(url) {
  const value = String(url ?? '').trim();

  if (!value) {
    return '';
  }

  // 상대경로와 앵커는 그대로 둔다
  if (/^(\/|#|\.\/|\.\.\/)/.test(value)) {
    return value;
  }

  return /^(https?:|mailto:)/i.test(value) ? value : '';
}

/** 마크다운 한 줄 → contenteditable 에 넣을 HTML */
export function inlineToHtml(text) {
  const source = String(text ?? '');

  if (!source) {
    return '';
  }

  // 색 구간은 바깥을 감싼다. 안쪽 서식은 색 없는 구간과 같은 규칙으로 그린다
  let html = splitColorSpans(source)
    .map((part) => {
      const inner = formatsToHtml(part.text);
      return part.color ? `<span data-color="${part.color}">${inner}</span>` : inner;
    })
    .join('');

  // 줄바꿈은 <br> 로 그린다. 글자로 둔 \n 은 화면에서 띄어쓰기처럼 뭉개진다.
  // 끝이 줄바꿈이면 빈 마지막 줄이 보이도록 <br> 을 하나 더 둔다 (브라우저 규칙)
  html = html.replace(/\n/g, '<br>');

  return source.endsWith('\n') ? `${html}<br>` : html;
}

function formatsToHtml(source) {
  const pattern = /(`[^`]+`)|(\*\*[^*]+\*\*)|(~~[^~]+~~)|(<u>[^<]+<\/u>)|(\*[^*]+\*)|(\[[^\]]+\]\([^)\s]*\))/g;
  let html = '';
  let lastIndex = 0;

  for (const match of source.matchAll(pattern)) {
    html += escapeHtml(source.slice(lastIndex, match.index));

    const [value] = match;

    if (value.startsWith('`')) {
      html += `<code>${escapeHtml(value.slice(1, -1))}</code>`;
    } else if (value.startsWith('**')) {
      html += `<strong>${escapeHtml(value.slice(2, -2))}</strong>`;
    } else if (value.startsWith('~~')) {
      html += `<s>${escapeHtml(value.slice(2, -2))}</s>`;
    } else if (value.startsWith('<u>')) {
      html += `<u>${escapeHtml(value.slice(3, -4))}</u>`;
    } else if (value.startsWith('*')) {
      html += `<em>${escapeHtml(value.slice(1, -1))}</em>`;
    } else {
      const link = value.match(/^\[([^\]]+)\]\(([^)\s]*)\)$/);
      const href = escapeHtml(safeHref(link[2]));
      // data-href 는 원문 그대로 둔다. 저장할 때 사용자가 쓴 주소를 잃지 않기 위해서다
      html += `<a href="${href}" data-href="${escapeHtml(link[2])}">${escapeHtml(link[1])}</a>`;
    }

    lastIndex = match.index + value.length;
  }

  return html + escapeHtml(source.slice(lastIndex));
}

const FORMAT_KINDS = {
  strong: 'strong', b: 'strong', em: 'em', i: 'em', s: 's', strike: 's', del: 's', u: 'u', code: 'code', a: 'a',
};

/**
 * contenteditable 의 내용 → 글자 조각(run) 배열.
 *
 * 조각마다 서식 하나(format)와 색 하나(color)를 가진다. 저장 형식이 한 글자에 서식 하나만 담으므로
 * 서식 안의 서식은 바깥 것만 남고 안쪽은 글자로 둔다 — 글자는 잃지 않는다. 색도 바깥 것이 이긴다.
 * 줄바꿈(<br>)은 br: true 인 '\n' 조각이다.
 *
 * @param root 입력칸 자체를 읽을 때 true. 맨 끝 <br> 은 빈 마지막 줄을 보이게 하려고
 *             브라우저가 붙인 자리 채움이라 줄바꿈으로 세지 않는다
 */
export function readRuns(node, { root = false } = {}) {
  const runs = [];

  function walk(parent, context, isRoot) {
    const children = [...parent.childNodes];
    const last = children[children.length - 1];

    children.forEach((child, index) => {
      if (child.nodeType === Node.TEXT_NODE) {
        // 커서를 태그 밖으로 빼려고 넣은 보이지 않는 글자는 저장하지 않는다
        const text = child.textContent.replace(/\u200b/g, '');

        if (text) {
          runs.push({ ...context, text });
        }

        return;
      }

      if (child.nodeType !== Node.ELEMENT_NODE) {
        return;
      }

      const tag = child.tagName.toLowerCase();

      if (tag === 'br') {
        if (!(isRoot && child === last)) {
          runs.push({ ...context, text: '\n', br: true });
        }

        return;
      }

      // 엔터나 붙여넣기로 들어온 줄 단위 요소는 줄의 경계다.
      // 내용만 이어 붙이면 화면의 두 줄이 저장할 때 한 줄로 붙는다.
      // 빈 줄(<div><br></div>)도 줄 하나다. 앞이 이미 줄바꿈으로 끝나도 새 줄을 연다
      if (tag === 'div' || tag === 'p') {
        if (index > 0) {
          runs.push({ ...context, text: '\n', br: true });
        }

        walk(child, context, true);
        return;
      }

      const kind = FORMAT_KINDS[tag];

      if (kind) {
        walk(child, context.format ? context : {
          ...context,
          format: kind,
          href: kind === 'a' ? child.getAttribute('data-href') ?? child.getAttribute('href') ?? '' : '',
        }, false);
        return;
      }

      const color = tag === 'span' ? child.getAttribute('data-color') : null;

      if (color && isInlineColor(color) && !context.color) {
        walk(child, { ...context, color }, false);
        return;
      }

      // div·span 처럼 붙여넣기로 섞여 들어온 것은 내용만 남긴다
      walk(child, context, false);
    });
  }

  walk(node, { format: '', href: '', color: '' }, root);

  return runs;
}

function formatRun(run) {
  const { text } = run;

  switch (run.format) {
    case 'strong':
      return `**${text}**`;
    case 'em':
      return `*${text}*`;
    case 's':
      return `~~${text}~~`;
    case 'u':
      return `<u>${text}</u>`;
    case 'code':
      return `\`${text}\``;
    case 'a':
      return `[${text}](${run.href})`;
    default:
      return text;
  }
}

/** 글자 조각 → 마크다운. 이웃한 같은 서식은 하나로 잇고, 같은 색끼리 한 구간으로 감싼다 */
export function runsToMarkdown(runs) {
  const merged = [];

  runs.forEach((run) => {
    const previous = merged[merged.length - 1];

    if (previous && previous.format === run.format && previous.href === run.href && previous.color === run.color) {
      previous.text += run.text;
    } else {
      merged.push({ format: run.format, href: run.href, color: run.color, text: run.text });
    }
  });

  let markdown = '';
  let group = '';
  let groupColor = '';

  merged.forEach((run) => {
    if (run.color !== groupColor) {
      markdown += wrapColor(group, groupColor);
      group = '';
      groupColor = run.color;
    }

    group += formatRun(run);
  });

  return markdown + wrapColor(group, groupColor);
}

/** 글자 조각의 [start, end) 에 색을 칠한다('' 이면 지운다). 세는 글자에 줄바꿈은 넣지 않는다 */
export function colorRuns(runs, start, end, color) {
  const result = [];
  let offset = 0;

  runs.forEach((run) => {
    if (run.br) {
      result.push({ ...run, color: offset > start && offset < end ? color : run.color });
      return;
    }

    const runStart = offset;
    const runEnd = offset + run.text.length;
    offset = runEnd;

    // 고른 구간 경계에서 조각을 쪼갠다
    const cuts = [runStart, Math.min(Math.max(start, runStart), runEnd), Math.min(Math.max(end, runStart), runEnd), runEnd];

    for (let index = 0; index < 3; index += 1) {
      const [from, to] = [cuts[index], cuts[index + 1]];

      if (to > from) {
        const inside = from >= start && to <= end;
        result.push({ ...run, text: run.text.slice(from - runStart, to - runStart), color: inside ? color : run.color });
      }
    }
  });

  return result;
}

/** contenteditable 의 내용 → 마크다운 한 줄 */
export function htmlToInline(node, { root = false } = {}) {
  return runsToMarkdown(readRuns(node, { root }));
}

/**
 * 방금 친 글자로 서식이 완성됐는지 본다.
 * 노션처럼 닫는 기호를 치는 순간 기호가 사라지고 서식이 적용된다.
 *
 * @returns {{ markerLength: number, tag: string, text: string } | null}
 */
export function findCompletedMarker(textBeforeCaret) {
  const rules = [
    { pattern: /\*\*([^*]+)\*\*$/, tag: 'strong', markerLength: 2 },
    { pattern: /~~([^~]+)~~$/, tag: 's', markerLength: 2 },
    { pattern: /(?:^|[^*])\*([^*]+)\*$/, tag: 'em', markerLength: 1 },
    { pattern: /`([^`]+)`$/, tag: 'code', markerLength: 1 },
  ];

  for (const rule of rules) {
    const match = textBeforeCaret.match(rule.pattern);

    if (match) {
      return {
        tag: rule.tag,
        text: match[1],
        // 기호를 포함한 전체 길이. 이만큼 지우고 태그로 바꾼다
        markerLength: match[1].length + rule.markerLength * 2,
      };
    }
  }

  return null;
}
