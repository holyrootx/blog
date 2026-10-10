import { test } from 'node:test';
import assert from 'node:assert/strict';

import { applyMarkdownShortcut, orderedNumberAt } from '../src/features/admin/data/postEditorShortcuts.js';

const block = (text, type = 'paragraph') => ({ id: 'b', type, text });

test('typed Markdown markers turn into block formats and drop the marker', () => {
  const cases = [
    ['## 소제목', { type: 'heading', level: 2, text: '소제목' }],
    ['- 항목', { type: 'bullet', text: '항목' }],
    ['3. 셋째', { type: 'ordered', text: '셋째' }],
    // 노션과 같다: > 는 토글, " 는 인용
    ['> 토글', { type: 'toggle', text: '토글' }],
    ['" 인용', { type: 'quote', text: '인용' }],
    ['```', { type: 'code', text: '' }],
    ['---', { type: 'divider', text: '' }],
  ];

  for (const [typed, expected] of cases) {
    const target = block(typed);
    assert.equal(applyMarkdownShortcut(target), true, typed);
    for (const [key, value] of Object.entries(expected)) assert.equal(target[key], value, `${typed} ${key}`);
  }
});

test('ordinary text and markers already applied are left alone', () => {
  const plain = block('그냥 문장 - 중간에 기호');
  assert.equal(applyMarkdownShortcut(plain), false);
  assert.equal(plain.text, '그냥 문장 - 중간에 기호');

  // 이미 목록인 블록에서 "- " 로 시작하는 글을 쳐도 한 번 더 벗기지 않는다
  const bullet = block('- 그대로', 'bullet');
  assert.equal(applyMarkdownShortcut(bullet), false);
  assert.equal(bullet.text, '- 그대로');
});

test('ordered numbers restart after any other block', () => {
  // 화면은 번호 목록 블록에만 번호를 묻는다
  const blocks = ['ordered', 'ordered', 'paragraph', 'ordered', 'ordered', 'ordered'].map(type => ({ type }));
  const numbers = blocks.flatMap((item, index) => (item.type === 'ordered' ? [orderedNumberAt(blocks, index)] : []));
  assert.deepEqual(numbers, [1, 2, 1, 2, 3]);
});
