import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  createBlock, insertPoint, moveBlockRange, normalizeIndents, subtreeEnd, toEditorBlocks, toMarkdown,
} from '../src/features/admin/data/postEditorBlocks.js';
import { applyMarkdownShortcut, orderedLabel } from '../src/features/admin/data/postEditorShortcuts.js';
import { toPostBody } from '../src/shared/post/postContentMapper.js';

const shape = blocks => blocks.map(block => `${block.type}@${block.indent}${block.type === 'todo' ? (block.checked ? '[x]' : '[ ]') : ''}:${block.text}`);

const NESTED = [
  '- a',
  '  - b',
  '    1. c',
  '    2. d',
  '  - [x] e',
  '- [ ] f',
  '',
  '1. g',
  '   - h',
  '2. i',
].join('\n');

test('중첩 목록·할 일은 깊이를 읽고, 그대로 다시 쓴다 (하위 항목은 부모 표기 너비만큼 들여 씀)', () => {
  const blocks = toEditorBlocks(NESTED);
  assert.deepEqual(shape(blocks), [
    'bullet@0:a', 'bullet@1:b', 'ordered@2:c', 'ordered@2:d', 'todo@1[x]:e', 'todo@0[ ]:f',
    'ordered@0:g', 'bullet@1:h', 'ordered@0:i',
  ]);
  assert.equal(toMarkdown(blocks), NESTED);
});

test('다른 도구가 4칸·탭으로 들여 쓴 목록도 같은 깊이로 읽는다', () => {
  assert.deepEqual(shape(toEditorBlocks('- a\n    - b\n\t\t- c\n- d')), ['bullet@0:a', 'bullet@1:b', 'bullet@2:c', 'bullet@0:d']);
});

test('예전처럼 한 단계뿐인 목록은 열었다 저장해도 원문 그대로다', () => {
  const flat = '- 하나\n- 둘\n\n1. 첫째\n2. 둘째';
  assert.equal(toMarkdown(toEditorBlocks(flat)), flat);
});

test('[] · [ ] · [x] 를 치면 할 일이 된다. 글머리 목록 안에서 쳐도 된다', () => {
  const cases = [
    [{ type: 'paragraph', text: '[] 장보기' }, 'todo', false, '장보기'],
    [{ type: 'paragraph', text: '[x] 끝난 일' }, 'todo', true, '끝난 일'],
    [{ type: 'bullet', text: '[ ] 목록 안' }, 'todo', false, '목록 안'],
  ];
  for (const [block, type, checked, text] of cases) {
    assert.equal(applyMarkdownShortcut(block), true);
    assert.deepEqual([block.type, block.checked, block.text], [type, checked, text]);
  }
});

test('번호 목록 표시는 깊이마다 1. → a. → i. 이다 (저장은 언제나 숫자)', () => {
  assert.deepEqual([orderedLabel(3, 0), orderedLabel(1, 1), orderedLabel(27, 1), orderedLabel(4, 2), orderedLabel(9, 5)],
    ['3.', 'a.', 'aa.', 'iv.', 'ix.']);
});

test('공개 화면은 목록을 트리로 읽고, 할 일은 체크 여부를 가진다', () => {
  const [bullets, numbers] = toPostBody(NESTED);
  assert.equal(bullets.ordered, false);
  assert.equal(bullets.items.length, 2);
  const [a, f] = bullets.items;
  assert.equal(a.checked, null);
  assert.equal(a.children[0].items[0].children[0].ordered, true);
  assert.deepEqual(a.children[0].items[0].children[0].items.map(item => item.inline[0].text), ['c', 'd']);
  assert.equal(a.children[0].items[1].checked, true);
  assert.equal(f.checked, false);
  assert.equal(numbers.ordered, true);
  assert.deepEqual(numbers.items.map(item => item.inline[0].text), ['g', 'i']);
  assert.equal(numbers.items[0].children[0].items[0].inline[0].text, 'h');
});

const TOGGLES = [
  '<details>',
  '<summary>바깥 토글</summary>',
  '',
  '안쪽 문단',
  '',
  '- 안쪽 목록',
  '  - 더 안쪽',
  '',
  '<details>',
  '<summary>안쪽 토글</summary>',
  '',
  '가장 안쪽',
  '',
  '</details>',
  '',
  '</details>',
  '',
  '<details>',
  '<summary>빈 토글</summary>',
  '</details>',
  '',
  '뒤 문단',
].join('\n');

test('토글은 <details> 로 읽고 쓴다. 안쪽 블록(목록·토글 포함)은 한 단계 깊다', () => {
  const blocks = toEditorBlocks(TOGGLES);
  assert.deepEqual(shape(blocks), [
    'toggle@0:바깥 토글', 'paragraph@1:안쪽 문단', 'bullet@1:안쪽 목록', 'bullet@2:더 안쪽',
    'toggle@1:안쪽 토글', 'paragraph@2:가장 안쪽', 'toggle@0:빈 토글', 'paragraph@0:뒤 문단',
  ]);
  assert.equal(toMarkdown(blocks), TOGGLES);
});

test('저장할 수 없는 들여쓰기는 담을 수 있는 자리로 올린다 (문단 아래 하위 항목, 목록 아래 문단)', () => {
  const blocks = [
    createBlock('paragraph', { text: 'p' }),
    createBlock('bullet', { text: '부모 없는 하위 항목', indent: 1 }),
    createBlock('bullet', { text: 'a' }),
    createBlock('paragraph', { text: '목록 아래 문단', indent: 1 }),
    createBlock('toggle', { text: 't' }),
    createBlock('paragraph', { text: '두 단계 건너뜀', indent: 3 }),
  ];
  assert.deepEqual(shape(normalizeIndents(blocks)), [
    'paragraph@0:p', 'bullet@0:부모 없는 하위 항목', 'bullet@0:a', 'paragraph@0:목록 아래 문단',
    'toggle@0:t', 'paragraph@1:두 단계 건너뜀',
  ]);
});

test('끼워 넣을 자리: 펼친 토글 아래는 안쪽, 접힌 토글 아래는 숨은 안쪽을 건너뛴 다음 줄', () => {
  const open = toEditorBlocks('<details>\n<summary>t</summary>\n\n안\n\n</details>\n\n뒤');
  assert.deepEqual(insertPoint(open, 1), { at: 1, depth: 1 });
  open[0].open = false;
  assert.deepEqual(insertPoint(open, 1), { at: 2, depth: 0 });
  // 부모와 첫 하위 항목 사이는 하위 항목 깊이
  assert.deepEqual(insertPoint(toEditorBlocks('- a\n  - b'), 1), { at: 1, depth: 1 });
  assert.deepEqual(insertPoint(toEditorBlocks('- a\n  - b'), 2), { at: 2, depth: 1 });
  assert.deepEqual(insertPoint([], 0), { at: 0, depth: 0 });
});

test('토글을 옮기면 안쪽이 따라가고, 옆 토글은 안쪽까지 한 칸으로 건너뛴다', () => {
  const blocks = toEditorBlocks('<details>\n<summary>A</summary>\n\na1\n\n</details>\n\n<details>\n<summary>B</summary>\n\nb1\n\n</details>');
  assert.equal(subtreeEnd(blocks, 0), 2);
  const moved = moveBlockRange(blocks, 2, subtreeEnd(blocks, 2), -1);
  assert.deepEqual(shape(moved), ['toggle@0:B', 'paragraph@1:b1', 'toggle@0:A', 'paragraph@1:a1']);
  assert.equal(moveBlockRange(blocks, 0, 2, -1), null);
});

const TABLE = [
  '| 이름 | 값 | 비고 |',
  '| --- | :---: | ---: |',
  '| **굵은** 이름 | a \\| b | `코드` |',
  '| 둘째 |  | 끝 |',
].join('\n');

test('표: GitHub 표를 줄·칸·정렬로 읽고, 그대로 다시 쓴다 (칸 안의 | 는 \\| )', () => {
  const [table, ...rest] = toEditorBlocks(`${TABLE}\n\n뒤 문단`);
  assert.equal(table.type, 'table');
  assert.equal(table.header, true);
  assert.deepEqual(table.rows, [['이름', '값', '비고'], ['**굵은** 이름', 'a | b', '`코드`'], ['둘째', '', '끝']]);
  assert.deepEqual(table.columnAlign, ['', 'center', 'right']);
  assert.deepEqual(shape(rest), ['paragraph@0:뒤 문단']);
  assert.equal(toMarkdown([table, ...rest]), `${TABLE}\n\n뒤 문단`);
});

test('표: 제목 행을 끈 표는 빈 첫 줄로 쓰고, 빈 첫 줄은 제목 행이 없는 표로 읽는다', () => {
  const table = createBlock('table', { rows: [['a', 'b'], ['c', 'd']], header: false });
  const markdown = toMarkdown([table]);
  assert.equal(markdown, '|  |  |\n| --- | --- |\n| a | b |\n| c | d |');
  const [read] = toEditorBlocks(markdown);
  assert.equal(read.header, false);
  assert.deepEqual(read.rows, [['a', 'b'], ['c', 'd']]);
});

test('표: 칸 수가 모자란 줄은 빈 칸을 채우고, 넘치는 칸은 버린다. 앞뒤 | 가 없어도 읽는다', () => {
  const [table] = toEditorBlocks('a | b\n--- | ---\n1 |\n2 | 3 | 4');
  assert.deepEqual(table.rows, [['a', 'b'], ['1', ''], ['2', '3']]);
  // | 가 없는 줄에서 표가 끝난다 (빈 줄 없이 이어 쓴 문장이 표 칸으로 빨려 들어가지 않게)
  assert.deepEqual(toEditorBlocks('| a |\n| --- |\n| 1 |\n이어 쓴 문장').map(block => block.type), ['table', 'paragraph']);
});

test('표가 아닌 것: | 가 든 문장, --- 줄 수가 다른 것은 그대로 문단·구분선이다', () => {
  assert.deepEqual(shape(toEditorBlocks('A | B 는 둘 중 하나')), ['paragraph@0:A | B 는 둘 중 하나']);
  assert.deepEqual(toEditorBlocks('a | b\n---').map(block => block.type), ['paragraph', 'divider']);
});

test('표는 토글 안에도 들어간다', () => {
  const source = '<details>\n<summary>t</summary>\n\n| a |\n| --- |\n| 1 |\n\n</details>';
  const blocks = toEditorBlocks(source);
  assert.deepEqual(blocks.map(block => `${block.type}@${block.indent}`), ['toggle@0', 'table@1']);
  assert.equal(toMarkdown(blocks), source);
});

test('제목 토글: <summary>## 제목</summary> 로 읽고 쓴다. 제목에서 > , 토글에서 # 를 치면 제목 토글이 된다', () => {
  const source = '<details>\n<summary>## 접는 제목</summary>\n\n안\n\n</details>';
  const [toggle] = toEditorBlocks(source);
  assert.equal(toggle.type, 'toggle');
  assert.equal(toggle.toggleLevel, 2);
  assert.equal(toggle.text, '접는 제목');
  assert.equal(toMarkdown(toEditorBlocks(source)), source);

  const heading = createBlock('heading', { level: 1, text: '> 큰 제목' });
  assert.equal(applyMarkdownShortcut(heading), true);
  assert.deepEqual([heading.type, heading.toggleLevel, heading.text], ['toggle', 1, '큰 제목']);

  const plain = createBlock('toggle', { text: '### 작은' });
  assert.equal(applyMarkdownShortcut(plain), true);
  assert.deepEqual([plain.type, plain.toggleLevel, plain.text], ['toggle', 3, '작은']);

  // 제목 4 이하에서 > 는 보통 토글이다 (노션의 제목 토글은 1~3)
  const small = createBlock('heading', { level: 4, text: '> 넷' });
  applyMarkdownShortcut(small);
  assert.deepEqual([small.type, small.toggleLevel], ['toggle', 0]);
});
