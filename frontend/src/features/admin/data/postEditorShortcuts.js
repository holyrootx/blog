/** 목록 블록인지 (글머리·번호·할 일). 들여쓰기는 목록에만 있다 */
export function isListType(type) {
  return type === 'bullet' || type === 'ordered' || type === 'todo';
}

/**
 * 마크다운 표기를 직접 쳤을 때 블록 서식으로 바꾼다.
 *
 * "## 제목" 을 치면 제목 블록이 되는 식이다. 바꿨으면 true 를 돌려준다 —
 * 부르는 쪽이 커서를 다시 놓고 화면을 맞춰야 한다.
 */
export function applyMarkdownShortcut(block) {
  const heading = block.text.match(/^(#{1,6})\s(.*)$/);

  // 토글 제목에서 # 를 치면 제목 토글이 된다 (노션의 "제목 토글 1·2·3")
  if (heading && block.type === 'toggle' && heading[1].length <= 3) {
    block.toggleLevel = heading[1].length;
    block.text = heading[2];
    return true;
  }

  if (heading) {
    block.type = 'heading';
    block.level = heading[1].length;
    block.text = heading[2];
    return true;
  }

  // 할 일: [] · [ ] · [x] 뒤에 띄어쓰기 (노션과 같다). 글머리 목록 안에서 쳐도 바뀐다
  const todo = block.text.match(/^\[([ xX]?)\]\s(.*)$/);
  if (todo && (block.type === 'paragraph' || block.type === 'bullet')) {
    block.type = 'todo';
    block.checked = todo[1].toLowerCase() === 'x';
    block.text = todo[2];
    return true;
  }

  const bullet = block.text.match(/^[-*+]\s(.*)$/);
  if (bullet && block.type !== 'bullet') {
    block.type = 'bullet';
    block.text = bullet[1];
    return true;
  }

  const ordered = block.text.match(/^\d+\.\s(.*)$/);
  if (ordered && block.type !== 'ordered') {
    block.type = 'ordered';
    block.text = ordered[1];
    return true;
  }

  // 노션과 같다: > 뒤 띄어쓰기는 토글, " 뒤 띄어쓰기는 인용
  const toggle = block.text.match(/^>\s(.*)$/);
  if (toggle && block.type !== 'toggle') {
    // 제목 1~3 에서 치면 제목 토글이 된다
    block.toggleLevel = block.type === 'heading' && block.level <= 3 ? block.level : 0;
    block.type = 'toggle';
    block.open = true;
    block.text = toggle[1];
    return true;
  }

  const quote = block.text.match(/^"\s(.*)$/);
  if (quote && block.type !== 'quote') {
    block.type = 'quote';
    block.text = quote[1];
    return true;
  }

  if (block.text === '```') {
    block.type = 'code';
    block.text = '';
    return true;
  }

  if (block.text === '---') {
    block.type = 'divider';
    block.text = '';
    return true;
  }

  return false;
}

/**
 * 이어진 번호 목록 안에서 몇 번째인지. 문서 전체 순번이 아니다.
 * 같은 깊이의 형제만 센다 — 사이에 낀 하위 항목은 건너뛰고, 부모(더 얕은 항목)를 만나면 멈춘다.
 */
export function orderedNumberAt(blocks, index) {
  const level = blocks[index]?.indent ?? 0;
  let number = 1;

  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const block = blocks[cursor];
    const depth = block.indent ?? 0;

    if (!isListType(block.type) || depth < level) {
      break;
    }

    if (depth > level) {
      continue;
    }

    if (block.type !== 'ordered') {
      break;
    }

    number += 1;
  }

  return number;
}

/** 번호 목록의 표시 글자. 깊이마다 1. → a. → i. 로 바꾼다 (노션과 같다). 저장은 언제나 숫자다 */
export function orderedLabel(number, depth) {
  const kind = depth % 3;

  if (kind === 1) {
    let label = '';
    for (let value = number; value > 0; value = Math.floor((value - 1) / 26)) {
      label = String.fromCharCode(97 + ((value - 1) % 26)) + label;
    }
    return `${label}.`;
  }

  if (kind === 2) {
    const numerals = [[10, 'x'], [9, 'ix'], [5, 'v'], [4, 'iv'], [1, 'i']];
    let rest = number;
    let label = '';
    for (const [value, numeral] of numerals) {
      while (rest >= value) {
        label += numeral;
        rest -= value;
      }
    }
    return `${label}.`;
  }

  return `${number}.`;
}

/** 글머리 표시. 깊이마다 ● → ○ → ■ (노션과 같다) */
export function bulletMarker(depth) {
  return ['•', '◦', '▪'][depth % 3];
}
