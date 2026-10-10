import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 블록 조작: + 버튼, 손잡이 메뉴(바꾸기·복제·옮기기·삭제), ⌘D·⌘⇧↑↓ (노션과 같은 동작).
 * 조작한 뒤 이어 친 글자가 어디에 들어가는지까지 저장값으로 본다.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor [contenteditable="true"]');
const rows = () => page.locator('.block-editor__row');
const press = key => page.keyboard.press(key);
const type = text => page.keyboard.type(text, { delay: 10 });
const menu = () => page.getByRole('menu', { name: /블록 메뉴/ });

before(async () => {
  server = await createServer({
    root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
  });
  await server.listen();
  const chrome = process.env.CHROME_PATH;
  browser = await chromium.launch({ headless: true, ...(chrome ? { executablePath: chrome } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = [];
  page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'warning' && msg.text().includes('[Vue warn]')) errors.push(msg.text()); });
  await page.route('**/api/v1/**', route => route.fulfill({ json: { success: true, data: null } }));
  await page.goto(`${server.resolvedUrls.local[0]}tests/editor-comments.html`);
  await page.waitForFunction(() => typeof window.fixture?.editor === 'function');
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('+ 는 그 블록 아래에 빈 줄을 만들고 바로 이어 쓴다. Option 을 누른 채면 위에 만든다', async () => {
  await open('first\n\nsecond');
  await rows().nth(0).hover();
  await rows().nth(0).getByRole('button', { name: '아래에 줄 추가' }).click();
  await type('between');
  assert.equal(await markdown(), 'first\n\nbetween\n\nsecond');

  await rows().nth(0).hover();
  await rows().nth(0).getByRole('button', { name: '아래에 줄 추가' }).click({ modifiers: ['Alt'] });
  await type('top');
  assert.equal(await markdown(), 'top\n\nfirst\n\nbetween\n\nsecond');
});

test('손잡이를 누르면 블록이 골라지고, 손잡이 왼쪽에 메뉴가 열려 아래 블록을 가리지 않는다', async () => {
  await open('one\n\ntwo\n\nthree');
  const handles = page.getByRole('button', { name: '블록 옮기기' });
  await handles.nth(0).click();
  await menu().waitFor();

  assert.equal(await rows().nth(0).evaluate(row => row.classList.contains('block-editor__row--selected')), true);
  const menuBox = await menu().boundingBox();
  const handleBox = await handles.nth(1).boundingBox();
  assert.ok(menuBox.x + menuBox.width <= handleBox.x, '메뉴는 손잡이 왼쪽에 있어야 한다');
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('role')), 'menuitem');
});

test('메뉴: 복제·아래로·위로·삭제가 고른 블록에 적용된다', async () => {
  await open('one\n\ntwo\n\nthree');
  const handles = page.getByRole('button', { name: '블록 옮기기' });

  await handles.nth(0).click();
  await menu().getByRole('menuitem', { name: /복제/ }).click();
  assert.equal(await markdown(), 'one\n\none\n\ntwo\n\nthree');

  await handles.nth(0).click();
  await menu().getByRole('menuitem', { name: /아래로 옮기기/ }).click();
  assert.equal(await markdown(), 'one\n\none\n\ntwo\n\nthree');
  await handles.nth(2).click();
  await menu().getByRole('menuitem', { name: /위로 옮기기/ }).click();
  assert.equal(await markdown(), 'one\n\ntwo\n\none\n\nthree');

  await handles.nth(2).click();
  await menu().getByRole('menuitem', { name: /삭제/ }).click();
  assert.equal(await markdown(), 'one\n\ntwo\n\nthree');
});

test('메뉴는 키보드로도 쓴다: ↓·→ 로 바꾸기를 열어 제목 2 로 바꾸고, Esc 로 닫아도 블록은 골라져 있다', async () => {
  await open('change me\n\nstay');
  await page.getByRole('button', { name: '블록 옮기기' }).nth(0).click();
  await menu().waitFor();

  // 첫 항목이 바꾸기다
  await press('ArrowRight');
  await page.getByRole('menuitem', { name: '제목 2' }).waitFor();
  for (let i = 0; i < 2; i += 1) await press('ArrowDown');
  await page.waitForFunction(() => document.activeElement?.textContent.trim() === '제목 2');
  await press('Enter');
  assert.equal(await markdown(), '## change me\n\nstay');

  await page.getByRole('button', { name: '블록 옮기기' }).nth(1).click();
  await menu().waitFor();
  await press('Escape');
  await menu().waitFor({ state: 'detached' });
  assert.equal(await rows().nth(1).evaluate(row => row.classList.contains('block-editor__row--selected')), true);
  await press('Delete');
  assert.equal(await markdown(), '## change me');
});

test('글을 쓰다가 ⌘D 는 지금 블록을 복제하고, 커서는 원래 블록에 남는다', async () => {
  await open('alpha\n\nbeta');
  await texts().nth(0).click();
  await press('End');
  await press('ControlOrMeta+d');
  await type('!');
  assert.equal(await markdown(), 'alpha!\n\nalpha\n\nbeta');
});

test('글을 쓰다가 ⌘⇧↓·↑ 는 지금 블록을 옮기고, 커서가 따라간다', async () => {
  await open('alpha\n\nbeta\n\ngamma');
  await texts().nth(0).click();
  await press('End');
  await press('ControlOrMeta+Shift+ArrowDown');
  await type('1');
  assert.equal(await markdown(), 'beta\n\nalpha1\n\ngamma');

  await press('ControlOrMeta+Shift+ArrowDown');
  await press('ControlOrMeta+Shift+ArrowUp');
  await press('ControlOrMeta+Shift+ArrowUp');
  await type('2');
  assert.equal(await markdown(), 'alpha12\n\nbeta\n\ngamma');
});

test('블록을 고른 상태에서도 ⌘D·⌘⇧↑↓ 가 된다', async () => {
  await open('alpha\n\nbeta');
  await texts().nth(1).click();
  await press('Escape');
  await press('ControlOrMeta+d');
  assert.equal(await markdown(), 'alpha\n\nbeta\n\nbeta');
  await press('ControlOrMeta+Shift+ArrowUp');
  await press('ControlOrMeta+Shift+ArrowUp');
  assert.equal(await markdown(), 'beta\n\nalpha\n\nbeta');
});
