import { test } from 'node:test';
import assert from 'node:assert/strict';

import { nextThemeChoice, resolveTheme } from '../src/shared/theme/themeStore.js';

test('a remembered choice wins over the device setting, anything else falls back to it', () => {
  assert.equal(resolveTheme('light', true), 'light');
  assert.equal(resolveTheme('dark', false), 'dark');
  assert.equal(resolveTheme(null, true), 'dark');
  assert.equal(resolveTheme(null, false), 'light');
  // 예전 값이나 손으로 바꾼 값이 남아 있어도 기기 설정을 따른다
  assert.equal(resolveTheme('sepia', true), 'dark');
});

test('picking the opposite of the device is remembered, picking the device again forgets it', () => {
  // 밝은 기기에서 어둡게 → 기억한다
  assert.deepEqual(nextThemeChoice('light', false), { theme: 'dark', stored: 'dark' });
  // 다시 밝게 → 기기와 같으니 지운다. 그래야 기기를 어둡게 바꿨을 때 따라간다
  assert.deepEqual(nextThemeChoice('dark', false), { theme: 'light', stored: null });

  assert.deepEqual(nextThemeChoice('dark', true), { theme: 'light', stored: 'light' });
  assert.deepEqual(nextThemeChoice('light', true), { theme: 'dark', stored: null });
});
