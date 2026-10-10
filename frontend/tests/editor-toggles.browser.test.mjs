import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 토글 (노션과 같은 키 동작). 사람이 치는 순서대로 누르고 저장값(마크다운)과 화면을 같이 본다.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor [contenteditable="true"]');
const visibleTexts = () => page.locator('.block-editor__row:not(.block-editor__row--hidden) [contenteditable="true"]');
const press = key => page.keyboard.press(key);
const type = text => page.keyboard.type(text, { delay: 10 });
const toggleButton = (index = 0) => page.locator('.block-editor__toggle').nth(index);
const focusedText = () => page.evaluate(() => document.activeElement?.textContent ?? '');
const details = (title, ...inner) => (inner.length
  ? `<details>\n<summary>${title}</summary>\n\n${inner.join('\n\n')}\n\n</details>`
  : `<details>\n<summary>${title}</summary>\n</details>`);

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

test('> 로 토글을 만들고, 제목에서 Enter 는 안쪽 첫 줄, 안쪽 빈 줄에서 Enter 는 토글 밖으로 나온다', async () => {
  await open('');
  await texts().first().click();
  await type('> 질문');
  assert.equal(await toggleButton().getAttribute('aria-expanded'), 'true');
  await press('Enter');
  await type('답');
  await press('Enter');
  await type('둘째 줄');
  assert.equal(await markdown(), details('질문', '답', '둘째 줄'));

  await press('Enter');
  await press('Enter');
  await type('밖');
  assert.equal(await markdown(), `${details('질문', '답', '둘째 줄')}\n\n밖`);
});

test('" 를 치면 인용이 된다 (> 는 토글에 양보했다)', async () => {
  await open('');
  await texts().first().click();
  await type('" 인용문');
  assert.equal(await markdown(), '> 인용문');
});

test('▸ 를 누르면 안쪽이 숨고 다시 누르면 보인다. 접어도 저장 내용은 그대로다', async () => {
  await open(`${details('t', '안쪽 하나', '안쪽 둘')}\n\n뒤`);
  assert.equal(await visibleTexts().count(), 4);
  await toggleButton().click();
  assert.equal(await toggleButton().getAttribute('aria-expanded'), 'false');
  assert.equal(await visibleTexts().count(), 2);
  assert.equal(await markdown(), `${details('t', '안쪽 하나', '안쪽 둘')}\n\n뒤`);
  await toggleButton().click();
  assert.equal(await visibleTexts().count(), 4);
});

test('안쪽에 커서가 있을 때 접으면 커서가 토글 제목으로 온다', async () => {
  await open(details('제목', '안쪽'));
  await texts().nth(1).click();
  await toggleButton().click();
  assert.equal(await focusedText(), '제목');
  await type('!');
  assert.equal(await markdown(), details('제목!', '안쪽'));
});

test('접힌 토글 제목에서 Enter 는 숨은 안쪽 뒤, 토글 다음 줄을 만든다', async () => {
  await open(details('t', '숨은 줄'));
  await toggleButton().click();
  await texts().first().click();
  await press('End');
  await press('Enter');
  await type('다음');
  assert.equal(await markdown(), `${details('t', '숨은 줄')}\n\n다음`);
});

test('접힌 토글에서 ↓ 는 숨은 안쪽을 건너뛰고 다음 블록으로 간다', async () => {
  await open(`${details('t', '숨은 줄')}\n\n아래`);
  await toggleButton().click();
  await texts().first().click();
  await press('End');
  await press('ArrowDown');
  assert.equal(await focusedText(), '아래');
});

test('Tab 은 아무 블록이나 바로 위 토글 안으로 들이고, Shift+Tab 은 꺼낸다. 토글이 아니면 들어가지 않는다', async () => {
  await open(`${details('t', '안')}\n\n밖 문단\n\n그다음 문단`);
  await texts().nth(2).click();
  await press('Tab');
  assert.equal(await markdown(), `${details('t', '안', '밖 문단')}\n\n그다음 문단`);
  await press('Shift+Tab');
  assert.equal(await markdown(), `${details('t', '안')}\n\n밖 문단\n\n그다음 문단`);

  // 바로 위가 문단이면 들어갈 곳이 없다
  await texts().nth(3).click();
  await press('Tab');
  assert.equal(await markdown(), `${details('t', '안')}\n\n밖 문단\n\n그다음 문단`);
});

test('토글 안 목록: 목록 Tab 은 목록 안으로, 맨 바깥 항목 Shift+Tab 은 토글 밖으로', async () => {
  await open(details('t', '- a', '- b').replace('- a\n\n- b', '- a\n- b'));
  await texts().nth(2).click();
  await press('Tab');
  assert.equal(await markdown(), details('t', '- a\n  - b'));
  await press('Shift+Tab');
  await press('Shift+Tab');
  assert.equal(await markdown(), `${details('t', '- a')}\n\n- b`);
});

test('토글 제목 맨 앞 Backspace 는 문단으로 되돌리고, 안쪽 블록은 한 단계 꺼내 글을 남긴다', async () => {
  await open(details('t', '안쪽', '- 목록'));
  await texts().first().click();
  await press('Home');
  await press('Backspace');
  assert.equal(await markdown(), 't\n\n안쪽\n\n- 목록');
  assert.equal(await toggleButton().count(), 0);
});

test('토글 안 맨 앞 Backspace 는 먼저 토글 밖으로 꺼내고, 토글 제목과 합치지 않는다', async () => {
  await open(details('t', '안쪽'));
  await texts().nth(1).click();
  await press('Home');
  await press('Backspace');
  assert.equal(await markdown(), `${details('t')}\n\n안쪽`);
  await press('Backspace');
  // 토글 제목에는 붙지 않는다 (안쪽을 품은 줄이라 합치면 구조가 꼬인다)
  assert.equal(await markdown(), `${details('t')}\n\n안쪽`);
});

test('⌘⇧↑ 로 토글을 옮기면 안쪽이 따라가고, ⌘D 로 복제하면 안쪽까지 복제된다', async () => {
  await open(`앞\n\n${details('t', '안쪽')}`);
  await texts().nth(1).click();
  await press('ControlOrMeta+Shift+ArrowUp');
  assert.equal(await markdown(), `${details('t', '안쪽')}\n\n앞`);
  await press('ControlOrMeta+d');
  assert.equal(await markdown(), `${details('t', '안쪽')}\n\n${details('t', '안쪽')}\n\n앞`);
});

test('빈 토글은 안내 줄을 보이고, 누르면 안쪽 첫 줄에서 바로 쓴다', async () => {
  await open(details('빈'));
  const hint = page.getByRole('button', { name: /빈 토글입니다/ });
  await hint.click();
  await type('채움');
  assert.equal(await markdown(), details('빈', '채움'));
  assert.equal(await hint.count(), 0);
});

test('슬래시 /toggle 로도 만든다', async () => {
  await open('');
  await texts().first().click();
  await type('/toggle');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  await type('메뉴로 만든 토글');
  assert.equal(await markdown(), details('메뉴로 만든 토글'));
});

test('블록을 골라 지우면 토글 안쪽도 같이 지워진다', async () => {
  await open(`${details('t', '안쪽')}\n\n남는 줄`);
  await page.locator('.block-editor__handle').first().click();
  await page.locator('[role="menu"]').waitFor();
  await page.getByRole('menuitem', { name: /삭제/ }).click();
  assert.equal(await markdown(), '남는 줄');
});

test('공개 화면: 토글은 접힌 채로 보이고, 제목을 누르면 안쪽(목록 포함)이 펼쳐진다', async () => {
  await page.evaluate(text => window.fixture.post(text), `${details('**자주** 묻는 질문', '답입니다', '- 하나\n  - 둘')}\n\n뒤 문단`);
  const toggle = page.locator('details.post-toggle');
  assert.equal(await toggle.count(), 1);
  assert.equal(await toggle.evaluate(node => node.open), false);
  assert.equal(await page.getByText('답입니다').isVisible(), false);
  assert.equal(await page.locator('.post-toggle__summary strong').textContent(), '자주');

  await page.locator('.post-toggle__summary').click();
  assert.equal(await page.getByText('답입니다').isVisible(), true);
  assert.equal(await page.locator('.post-toggle__body li li').textContent(), '둘');
  assert.equal(await page.getByText('뒤 문단').isVisible(), true);
});

test('제목 토글: 제목 1 에서 > 를 치면 제목 토글 1, /th2 로도 만들고, 공개 화면에서는 제목 태그로 보인다', async () => {
  await open('');
  await texts().first().click();
  await type('# ');
  await type('> 큰 질문');
  assert.equal(await markdown(), details('# 큰 질문'));
  assert.equal(await page.locator('.block-editor__text--toggle.block-editor__text--h1').count(), 1);
  await press('Enter');
  await type('답');
  assert.equal(await markdown(), details('# 큰 질문', '답'));

  await open('');
  await texts().first().click();
  await type('/th2');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  await type('중간');
  assert.equal(await markdown(), details('## 중간'));

  await page.evaluate(text => window.fixture.post(text), details('## 중간', '안쪽'));
  assert.equal(await page.locator('details.post-toggle--h2 summary h3').textContent(), '중간');
  assert.equal(await page.locator('details.post-toggle--h2').getAttribute('id'), 'section-1');
});
