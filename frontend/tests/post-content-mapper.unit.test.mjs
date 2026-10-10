import { test } from 'node:test';
import assert from 'node:assert/strict';

import { toInline, toPostBody } from '../src/shared/post/postContentMapper.js';

const types = tokens => tokens.map(token => `${token.type}:${token.text}`);

test('인라인: 굵게·기울임·취소선·밑줄·코드·링크를 토큰으로 나눈다', () => {
  assert.deepEqual(
    types(toInline('a **b** *c* ~~d~~ <u>e</u> `f` [g](https://example.com)')),
    ['text:a ', 'bold:b', 'text: ', 'italic:c', 'text: ', 'strike:d', 'text: ', 'underline:e', 'text: ', 'code:f', 'text: ', 'link:g'],
  );
});

test('인라인: <u> 말고 다른 HTML 은 태그로 읽지 않고 글자로 둔다', () => {
  assert.deepEqual(types(toInline('<b>x</b> <script>y</script> <u onclick="z">w</u>')),
    ['text:<b>x</b> <script>y</script> <u onclick="z">w</u>']);
});

test('인라인: 코드 안의 ~~ 와 <u> 는 서식이 아니다', () => {
  assert.deepEqual(types(toInline('`~~a~~ <u>b</u>`')), ['code:~~a~~ <u>b</u>']);
});

test('제목의 목차 글자에는 취소선·밑줄 기호가 남지 않는다', () => {
  const [heading] = toPostBody('## ~~옛~~ <u>새</u> 제목');
  assert.equal(heading.text, '옛 새 제목');
});

test('토글: 제목은 인라인, 안쪽은 블록 트리로 읽고, 안쪽 제목도 목차 번호를 이어 받는다', () => {
  const body = toPostBody([
    '## 앞 제목',
    '',
    '<details>',
    '<summary>**굵은** 토글</summary>',
    '',
    '## 안쪽 제목',
    '',
    '- 안쪽 목록',
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
    '## 뒤 제목',
  ].join('\n'));

  assert.deepEqual(body.map(block => block.type), ['heading', 'toggle', 'heading']);
  const toggle = body[1];
  assert.deepEqual(types(toggle.inline), ['bold:굵은', 'text: 토글']);
  assert.deepEqual(toggle.children.map(block => block.type), ['heading', 'list', 'toggle']);
  assert.equal(toggle.children[2].children[0].type, 'paragraph');
  assert.deepEqual([body[0].id, toggle.children[0].id, body[2].id], ['section-1', 'section-2', 'section-3']);
});

test('토글: 닫는 </details> 가 없으면 글 끝까지를 안쪽으로 읽고 글자를 잃지 않는다', () => {
  const [toggle] = toPostBody('<details>\n<summary>t</summary>\n\n안쪽');
  assert.equal(toggle.type, 'toggle');
  assert.equal(toggle.children[0].type, 'paragraph');
});

test('표: 제목 행·정렬·칸 인라인 서식을 읽는다. 제목 행이 빈 표는 제목 행 없이 그린다', () => {
  const [table] = toPostBody('| 이름 | 값 |\n| :--- | ---: |\n| **a** | `1` |');
  assert.equal(table.type, 'table');
  assert.equal(table.header, true);
  assert.deepEqual(table.align, ['left', 'right']);
  assert.deepEqual(table.rows.map(cells => cells.map(types)), [[['text:이름'], ['text:값']], [['bold:a'], ['code:1']]]);

  const [plain] = toPostBody('|  |  |\n| --- | --- |\n| a | b |');
  assert.equal(plain.header, false);
  assert.deepEqual(plain.rows.map(cells => cells.map(types)), [[['text:a'], ['text:b']]]);
});

const colored = tokens => tokens.map(token => `${token.type}:${token.text}${token.color ? `@${token.color}` : ''}`);

test('색: <span data-color> 안의 서식을 읽고 토큰마다 색을 붙인다. 정한 이름이 아니면 글자로 둔다', () => {
  assert.deepEqual(
    colored(toInline('앞 <span data-color="red">**굵게** 빨강</span> <span data-color="yellow-bg">형광</span> 뒤')),
    ['text:앞 ', 'bold:굵게@red', 'text: 빨강@red', 'text: ', 'text:형광@yellow-bg', 'text: 뒤'],
  );
  assert.deepEqual(colored(toInline('<span data-color="evil">x</span>')), ['text:<span data-color="evil">x</span>']);
  assert.deepEqual(colored(toInline('<span style="color:red">x</span>')), ['text:<span style="color:red">x</span>']);
  // 코드 안에 적은 태그는 글자다
  assert.deepEqual(colored(toInline('`<span data-color="red">x</span>`')), ['code:<span data-color="red">x</span>']);
});

test('색: 목차 글자에는 색 태그가 남지 않는다', () => {
  const [heading] = toPostBody('## <span data-color="blue">파란</span> 제목');
  assert.equal(heading.text, '파란 제목');
  assert.deepEqual(colored(heading.inline), ['text:파란@blue', 'text: 제목']);
});

test('제목 토글은 제목처럼 목차에 들어가고, 안쪽 제목보다 번호를 먼저 받는다', async () => {
  const { toPostToc } = await import('../src/shared/post/postContentMapper.js');
  const body = toPostBody('## 앞\n\n<details>\n<summary>## **접는** 제목</summary>\n\n### 안쪽\n\n</details>');
  assert.deepEqual(body.map(block => [block.type, block.id ?? '', block.level]), [['heading', 'section-1', 2], ['toggle', 'section-2', 2]]);
  assert.equal(body[1].children[0].id, 'section-3');
  assert.deepEqual(toPostToc(body), [{ id: 'section-1', text: '앞', level: 2 }, { id: 'section-2', text: '접는 제목', level: 2 }]);
});
