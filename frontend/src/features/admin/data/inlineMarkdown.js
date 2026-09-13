/**
 * 인라인 서식과 HTML 사이의 변환.
 *
 * 편집 중인 블록은 contenteditable 이라 화면에는 <strong> 같은 태그가 들어가고,
 * 저장할 때는 다시 마크다운으로 되돌린다. 저장 형식은 끝까지 마크다운이다.
 *
 * 태그 종류를 여기서만 정해두면 "화면에는 되는데 저장하면 사라지는" 서식이 안 생긴다.
 */

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

  const pattern = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*]+\*)|(\[[^\]]+\]\([^)\s]*\))/g;
  let html = '';
  let lastIndex = 0;

  for (const match of source.matchAll(pattern)) {
    html += escapeHtml(source.slice(lastIndex, match.index));

    const [value] = match;

    if (value.startsWith('`')) {
      html += `<code>${escapeHtml(value.slice(1, -1))}</code>`;
    } else if (value.startsWith('**')) {
      html += `<strong>${escapeHtml(value.slice(2, -2))}</strong>`;
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

  html += escapeHtml(source.slice(lastIndex));

  return html;
}

/** contenteditable 의 내용 → 마크다운 한 줄 */
export function htmlToInline(node) {
  let text = '';

  node.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      // 커서를 태그 밖으로 빼려고 넣은 보이지 않는 글자는 저장하지 않는다
      text += child.textContent.replace(/\u200b/g, '');
      return;
    }

    if (child.nodeType !== Node.ELEMENT_NODE) {
      return;
    }

    const inner = htmlToInline(child);
    const tag = child.tagName.toLowerCase();

    if (tag === 'strong' || tag === 'b') {
      text += inner ? `**${inner}**` : '';
    } else if (tag === 'em' || tag === 'i') {
      text += inner ? `*${inner}*` : '';
    } else if (tag === 'code') {
      text += inner ? `\`${inner}\`` : '';
    } else if (tag === 'a') {
      const href = child.getAttribute('data-href') ?? child.getAttribute('href') ?? '';
      text += inner ? `[${inner}](${href})` : '';
    } else if (tag === 'br') {
      text += '\n';
    } else {
      // div·span 처럼 붙여넣기로 섞여 들어온 것은 내용만 남긴다
      text += inner;
    }
  });

  return text;
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
