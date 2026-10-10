import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 글자 색·배경 색 (노션과 같은 동작). 저장은 <span data-color="…">, 한 구간에 색 하나.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor__row:not(.block-editor__row--table) .block-editor__text');
const press = key => page.keyboard.press(key);
const type = text => page.keyboard.type(text, { delay: 10 });
const toolbar = () => page.getByRole('toolbar', { name: '글자 서식' });
const selected = () => page.evaluate(() => window.getSelection().toString());

async function selectLastWord(locator) {
  await locator.click();
  await press('End');
  await press('Shift+Alt+ArrowLeft');
  await toolbar().waitFor();
}

async function chooseColor(name) {
  await toolbar().getByRole('button', { name: '색', exact: true }).click();
  await page.getByRole('menuitemradio', { name, exact: true }).click();
}

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

test('서식 막대의 색: 고른 글자에 글자 색을 칠하고, 고른 구간은 그대로 남는다. 다시 열면 지금 색이 표시된다', async () => {
  await open('앞 문장 빨강');
  await selectLastWord(texts().first());
  await chooseColor('빨강');
  assert.equal(await markdown(), '앞 문장 <span data-color="red">빨강</span>');
  assert.equal(await selected(), '빨강');
  assert.equal(await texts().first().locator('span[data-color="red"]').evaluate(node => getComputedStyle(node).color !== getComputedStyle(node.parentElement).color), true);

  await toolbar().getByRole('button', { name: '색', exact: true }).click();
  assert.equal(await page.getByRole('menuitemradio', { name: '빨강', exact: true }).getAttribute('aria-checked'), 'true');
  // 기본: 색을 지운다
  await page.getByRole('menuitemradio', { name: '기본', exact: true }).click();
  assert.equal(await markdown(), '앞 문장 빨강');
});

test('배경 색은 굵게를 그대로 두고 바깥을 감싼다. 굵은 글자 일부만 칠해도 글자를 잃지 않는다', async () => {
  await open('**굵은글**');
  await texts().first().click();
  await press('End');
  await press('Shift+ArrowLeft');
  await toolbar().waitFor();
  await chooseColor('노랑 배경');
  assert.equal(await markdown(), '**굵은**<span data-color="yellow-bg">**글**</span>');

  // 굵게는 색 구간 안에서도 켜고 끈다
  await toolbar().getByRole('button', { name: '굵게' }).click();
  assert.equal(await markdown(), '**굵은**<span data-color="yellow-bg">글</span>');
});

test('⌘⇧H 는 마지막으로 쓴 색을 칠한다 (처음에는 노란 형광펜)', async () => {
  await open('하나\n\n둘\n\n셋');
  await selectLastWord(texts().nth(0));
  await press('ControlOrMeta+Shift+h');
  assert.equal(await markdown(), '<span data-color="yellow-bg">하나</span>\n\n둘\n\n셋');

  await selectLastWord(texts().nth(1));
  await chooseColor('파랑');
  await selectLastWord(texts().nth(2));
  await press('ControlOrMeta+Shift+h');
  assert.equal(await markdown(),
    '<span data-color="yellow-bg">하나</span>\n\n<span data-color="blue">둘</span>\n\n<span data-color="blue">셋</span>');
});

test('색 구간을 열었다가 이어 써도 원문 표기가 그대로다. 칠한 뒤 ⌘Z 로 되돌린다', async () => {
  await open('<span data-color="red">**빨간 굵게**</span> 뒤');
  await texts().first().click();
  await press('End');
  await type(' 끝');
  assert.equal(await markdown(), '<span data-color="red">**빨간 굵게**</span> 뒤 끝');

  await selectLastWord(texts().first());
  await chooseColor('초록 배경');
  assert.equal(await markdown(), '<span data-color="red">**빨간 굵게**</span> 뒤 <span data-color="green-bg">끝</span>');
  await press('ControlOrMeta+z');
  assert.equal(await markdown(), '<span data-color="red">**빨간 굵게**</span> 뒤 끝');
});

test('블록 메뉴의 색은 블록 글 전체를 칠한다', async () => {
  await open('첫 블록 **굵게**\n\n둘째 블록');
  await page.locator('.block-editor__handle').first().click();
  await page.getByRole('menuitem', { name: '색' }).click();
  await page.getByRole('menuitem', { name: '보라 글자' }).click();
  assert.equal(await markdown(), '<span data-color="purple">첫 블록 **굵게**</span>\n\n둘째 블록');
});

test('표 칸에서도 서식 막대 색과 ⌘⇧H 가 칸에만 칠해진다', async () => {
  await open('| 이름 | 값 |\n| --- | --- |\n| 사과 | 빨강 |');
  await selectLastWord(page.locator('[data-cell="1:1"] [contenteditable="true"]'));
  await chooseColor('빨강');
  assert.equal(await markdown(), '| 이름 | 값 |\n| --- | --- |\n| 사과 | <span data-color="red">빨강</span> |');

  await selectLastWord(page.locator('[data-cell="1:0"] [contenteditable="true"]'));
  await press('ControlOrMeta+Shift+h');
  assert.equal(await markdown(), '| 이름 | 값 |\n| --- | --- |\n| <span data-color="red">사과</span> | <span data-color="red">빨강</span> |');
});

test('공개 화면: 정한 색만 클래스로 그리고, 굵게·링크는 안에서 그대로 보인다', async () => {
  await page.evaluate(text => window.fixture.post(text),
    '<span data-color="red">**빨간** [링크](https://example.com)</span> <span data-color="blue-bg">파란 배경</span> <span style="color:red">무시</span>');
  const red = page.locator('.post-color--red');
  assert.equal(await red.locator('strong').textContent(), '빨간');
  assert.equal(await red.locator('a').getAttribute('href'), 'https://example.com');
  // 글자 색 안의 링크는 그 색을 따른다
  assert.equal(await red.locator('a').evaluate(node => getComputedStyle(node).color === getComputedStyle(node.parentElement).color), true);
  assert.equal(await page.locator('.post-color--blue-bg').textContent(), '파란 배경');
  assert.notEqual(await page.locator('.post-color--blue-bg').evaluate(node => getComputedStyle(node).backgroundColor), 'rgba(0, 0, 0, 0)');
  assert.equal(await page.getByText('<span style="color:red">무시</span>').count(), 1);
});
