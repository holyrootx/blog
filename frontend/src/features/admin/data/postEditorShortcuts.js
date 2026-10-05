/**
 * 마크다운 표기를 직접 쳤을 때 블록 서식으로 바꾼다.
 *
 * "## 제목" 을 치면 제목 블록이 되는 식이다. 바꿨으면 true 를 돌려준다 —
 * 부르는 쪽이 커서를 다시 놓고 화면을 맞춰야 한다.
 */
export function applyMarkdownShortcut(block) {
  const heading = block.text.match(/^(#{1,6})\s(.*)$/);
  if (heading) {
    block.type = 'heading';
    block.level = heading[1].length;
    block.text = heading[2];
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

  const quote = block.text.match(/^>\s(.*)$/);
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

/** 이어진 번호 목록 안에서 몇 번째인지. 문서 전체 순번이 아니다 */
export function orderedNumberAt(blocks, index) {
  let number = 1;

  for (let cursor = index - 1; cursor >= 0 && blocks[cursor].type === 'ordered'; cursor -= 1) {
    number += 1;
  }

  return number;
}
