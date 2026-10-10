import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 목록 들여쓰기·할 일·줄 합치기 (노션과 같은 키 동작).
 * 사람이 치는 순서대로 누르고, 저장값(마크다운)과 화면 표시를 같이 본다.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor [contenteditable="true"]');
const press = key => page.keyboard.press(key);
const type = text => page.keyboard.type(text, { delay: 10 });
const inEditor = () => page.evaluate(() => Boolean(document.activeElement?.closest('.block-editor')));

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

test('Tab·Shift+Tab 으로 목록을 들이고 내밀며 이어 쓴다', async () => {
  await open('');
  await texts().first().click();
  await type('- a');
  await press('Enter');
  await press('Tab');
  await type('b');
  await press('Enter');
  await type('c');
  await press('Enter');
  await press('Shift+Tab');
  await type('d');
  assert.equal(await markdown(), '- a\n  - b\n  - c\n- d');
  assert.deepEqual(await page.locator('.block-editor__marker').allTextContents().then(items => items.map(item => item.trim())), ['•', '◦', '◦', '•']);
});

test('첫 항목·바로 위보다 두 단계 깊게는 들어가지 않고, Tab 을 눌러도 편집기를 떠나지 않는다', async () => {
  await open('- a\n- b');
  await texts().nth(0).click();
  await press('Tab');
  assert.equal(await markdown(), '- a\n- b');
  assert.equal(await inEditor(), true);

  await texts().nth(1).click();
  await press('Tab');
  await press('Tab');
  assert.equal(await markdown(), '- a\n  - b');

  // 목록이 아닌 줄에서도 Tab 은 편집기에 남는다
  await open('문단');
  await texts().first().click();
  await press('Tab');
  assert.equal(await inEditor(), true);
});

test('빈 하위 항목에서 Enter 는 한 단계 내밀고, 맨 바깥에서 한 번 더 누르면 목록을 끝낸다', async () => {
  await open('- a\n  - b');
  await texts().nth(1).click();
  await press('End');
  await press('Enter');
  await press('Enter');
  await type('c');
  assert.equal(await markdown(), '- a\n  - b\n- c');

  await press('Enter');
  await press('Enter');
  await type('문단');
  assert.equal(await markdown(), '- a\n  - b\n- c\n\n문단');
});

test('부모를 들이거나 내밀면 딸린 하위 항목이 따라간다', async () => {
  await open('- a\n- b\n  - c\n- d');
  await texts().nth(1).click();
  await press('Tab');
  assert.equal(await markdown(), '- a\n  - b\n    - c\n- d');
  await press('Shift+Tab');
  assert.equal(await markdown(), '- a\n- b\n  - c\n- d');
});

test('번호 목록은 깊이마다 1. a. i. 로 보이고, 저장은 깊이마다 1부터 숫자로 한다', async () => {
  await open('1. 첫째\n2. 둘째');
  await texts().nth(1).click();
  await press('End');
  await press('Enter');
  await press('Tab');
  await type('가');
  await press('Enter');
  await type('나');
  await press('Enter');
  await press('Tab');
  await type('깊이');
  assert.equal(await markdown(), '1. 첫째\n2. 둘째\n   1. 가\n   2. 나\n      1. 깊이');
  assert.deepEqual(await page.locator('.block-editor__marker').allTextContents().then(items => items.map(item => item.trim())),
    ['1.', '2.', 'a.', 'b.', 'i.']);
});

test('할 일: [] 로 만들고, 체크 상자를 누르면 완료, Enter 는 새 할 일, 빈 할 일에서 Enter 는 끝낸다', async () => {
  await open('');
  await texts().first().click();
  await type('[] 장보기');
  await press('Enter');
  await type('청소');
  assert.equal(await markdown(), '- [ ] 장보기\n- [ ] 청소');

  const boxes = page.getByRole('checkbox', { name: '할 일 완료' });
  await boxes.nth(0).click();
  assert.equal(await markdown(), '- [x] 장보기\n- [ ] 청소');
  assert.equal(await boxes.nth(0).getAttribute('aria-checked'), 'true');
  // 체크 상자를 눌러도 쓰던 줄의 커서는 그대로다
  await type('!');
  assert.equal(await markdown(), '- [x] 장보기\n- [ ] 청소!');

  await press('Enter');
  await press('Enter');
  await type('/todo');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  await type('메뉴로 만든 할 일');
  // 바로 아래에서 다시 만든 할 일은 같은 목록으로 이어진다
  assert.equal(await markdown(), '- [x] 장보기\n- [ ] 청소!\n- [ ] 메뉴로 만든 할 일');
});

test('Backspace: 들인 항목은 맨 앞에서 내밀고, 맨 바깥 항목은 문단이 되며, 문단 맨 앞은 윗줄에 붙는다', async () => {
  await open('위 문단\n\n- a\n  - b');
  await texts().nth(2).click();
  await press('Home');
  await press('Backspace');
  assert.equal(await markdown(), '위 문단\n\n- a\n- b');
  await press('Backspace');
  assert.equal(await markdown(), '위 문단\n\n- a\n\nb');
  await press('Backspace');
  assert.equal(await markdown(), '위 문단\n\n- ab');
  await type('|');
  assert.equal(await markdown(), '위 문단\n\n- a|b');
});

test('Delete: 줄 끝에서 누르면 아랫줄을 끌어와 붙이고, 커서는 이음매에 있다', async () => {
  await open('앞 **굵게**\n\n뒤 문장');
  await texts().nth(0).click();
  await press('End');
  await press('Delete');
  assert.equal(await markdown(), '앞 **굵게**뒤 문장');
  await type('·');
  assert.match(await markdown(), /^앞 \*\*굵게(·\*\*|\*\*·)뒤 문장$/);
});

test('코드 칸의 Tab 은 공백 두 칸, Shift+Tab 은 줄 앞 공백을 뺀다', async () => {
  await open('```\nline\n```');
  const area = page.locator('.block-editor__input--code');
  await area.click();
  await press('ControlOrMeta+a');
  await press('Home');
  await press('Tab');
  assert.equal(await markdown(), '```\n  line\n```');
  await press('Shift+Tab');
  assert.equal(await markdown(), '```\nline\n```');
  assert.equal(await inEditor(), true);
});
