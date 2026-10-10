import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 표 (노션 "단순 표" 와 같은 키 동작). 사람이 치는 순서대로 누르고 저장값(GitHub 표)과 화면을 같이 본다.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor__row:not(.block-editor__row--table) .block-editor__text');
const cell = (row, col) => page.locator(`[data-cell="${row}:${col}"] [contenteditable="true"]`);
const press = key => page.keyboard.press(key);
// 단어 단위로 고르는 키. 맥은 Alt, 리눅스(CI)·윈도는 Control 이다
const WORD = process.platform === 'darwin' ? 'Alt' : 'Control';
const type = text => page.keyboard.type(text, { delay: 10 });
const focusedCell = () => page.evaluate(() => document.activeElement?.closest('[data-cell]')?.dataset.cell ?? '');
const table = (...lines) => lines.join('\n');

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

async function compose(syllables) {
  const client = await page.context().newCDPSession(page);
  for (const steps of syllables) {
    for (const step of steps.slice(0, -1)) {
      await client.send('Input.imeSetComposition', { text: step, selectionStart: step.length, selectionEnd: step.length });
    }
    await client.send('Input.insertText', { text: steps[steps.length - 1] });
  }
  await client.detach();
}

test('/table 로 3×3 표를 만들고 첫 칸에서 바로 쓴다. Tab 은 다음 칸, Shift+Tab 은 앞 칸', async () => {
  await open('');
  await texts().first().click();
  await type('/table');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  assert.equal(await focusedCell(), '0:0');
  await type('이름');
  await press('Tab');
  await type('값');
  await press('Tab');
  await press('Shift+Tab');
  await type('!');
  assert.equal(await markdown(), table('| 이름 | 값! |  |', '| --- | --- | --- |', '|  |  |  |', '|  |  |  |'));
});

test('마지막 칸에서 Tab 은 줄을 더하고, Enter 는 아래 칸, 마지막 줄 Enter 는 표 아래 문단으로 나간다', async () => {
  await open(table('| a | b |', '| --- | --- |', '| c | d |'));
  await cell(1, 1).click();
  await press('End');
  await press('Tab');
  assert.equal(await focusedCell(), '2:0');
  await type('e');
  await press('Enter');
  await type('다음 문단');
  assert.equal(await markdown(), `${table('| a | b |', '| --- | --- |', '| c | d |', '| e |  |')}\n\n다음 문단`);

  await cell(0, 1).click();
  await press('Enter');
  assert.equal(await focusedCell(), '1:1');
});

test('한글 조합 중 Enter·Tab 은 칸을 옮기지 않고 조합만 끝낸다', async () => {
  await open(table('| a | b |', '| --- | --- |', '| c | d |'));
  await cell(0, 0).click();
  await press('End');
  await compose([['ㅎ', '하', '한']]);
  await press('Tab');
  await compose([['ㄱ', '그', '글']]);
  assert.equal(await markdown(), table('| a한 | b글 |', '| --- | --- |', '| c | d |'));
});

test('방향키: 위 문단에서 ↓ 는 첫 칸, 칸 끝 → 는 다음 칸, 첫 줄 ↑ 는 표 위 문단', async () => {
  await open(`위\n\n${table('| ab | cd |', '| --- | --- |', '| 1 | 2 |')}\n\n아래`);
  await texts().first().click();
  await press('ArrowDown');
  assert.equal(await focusedCell(), '0:0');
  await press('End');
  await press('ArrowRight');
  assert.equal(await focusedCell(), '0:1');
  await press('ArrowDown');
  assert.equal(await focusedCell(), '1:1');
  await press('ArrowDown');
  assert.equal(await page.evaluate(() => document.activeElement?.textContent), '아래');
  await press('ArrowUp');
  assert.equal(await focusedCell(), '1:0');
  await press('ArrowUp');
  await press('ArrowUp');
  assert.equal(await page.evaluate(() => document.activeElement?.textContent), '위');
});

test('줄 손잡이: 아래에 줄 추가·줄 삭제, 첫 줄 메뉴의 제목 행 끄기', async () => {
  await open(table('| a | b |', '| --- | --- |', '| c | d |'));
  await cell(1, 0).hover();
  await page.getByRole('button', { name: '2번째 줄 메뉴' }).click();
  await page.getByRole('menuitem', { name: '아래에 줄 추가' }).click();
  assert.equal(await focusedCell(), '2:0');
  await type('e');
  assert.equal(await markdown(), table('| a | b |', '| --- | --- |', '| c | d |', '| e |  |'));

  await cell(1, 0).hover();
  await page.getByRole('button', { name: '2번째 줄 메뉴' }).click();
  await page.getByRole('menuitem', { name: '줄 삭제' }).click();
  assert.equal(await markdown(), table('| a | b |', '| --- | --- |', '| e |  |'));

  await cell(0, 0).hover();
  await page.getByRole('button', { name: '1번째 줄 메뉴' }).click();
  const header = page.getByRole('menuitemcheckbox', { name: '제목 행' });
  assert.equal(await header.getAttribute('aria-checked'), 'true');
  await header.click();
  assert.equal(await markdown(), table('|  |  |', '| --- | --- |', '| a | b |', '| e |  |'));
});

test('열 손잡이: 오른쪽에 열 추가·가운데 정렬·열 삭제. 마지막 한 줄·한 열은 지울 수 없다', async () => {
  await open(table('| a |', '| --- |', '| 1 |'));
  await cell(0, 0).hover();
  await page.getByRole('button', { name: '1번째 열 메뉴' }).click();
  assert.equal(await page.getByRole('menuitem', { name: '열 삭제' }).isDisabled(), true);
  await page.getByRole('menuitem', { name: '오른쪽에 열 추가' }).click();
  assert.equal(await focusedCell(), '0:1');
  await type('b');
  assert.equal(await markdown(), table('| a | b |', '| --- | --- |', '| 1 |  |'));

  await page.getByRole('button', { name: '2번째 열 메뉴' }).click();
  await page.getByRole('menuitemcheckbox', { name: '가운데 정렬' }).click();
  assert.equal(await markdown(), table('| a | b |', '| --- | :---: |', '| 1 |  |'));
  assert.equal(await cell(0, 1).evaluate(node => getComputedStyle(node).textAlign), 'center');

  await cell(0, 0).hover();
  await page.getByRole('button', { name: '1번째 열 메뉴' }).click();
  await page.getByRole('menuitem', { name: '열 삭제' }).click();
  assert.equal(await markdown(), table('| b |', '| :---: |', '|  |'));
});

test('메뉴는 키보드로도 쓴다: ↓ 로 고르고 Enter, Esc 는 닫고 칸으로 돌아간다', async () => {
  await open(table('| a | b |', '| --- | --- |', '| c | d |'));
  await cell(1, 1).click();
  await page.getByRole('button', { name: '2번째 줄 메뉴' }).click();
  await press('Escape');
  assert.equal(await page.locator('.block-table__menu').count(), 0);
  assert.equal(await focusedCell(), '1:1');

  await page.getByRole('button', { name: '2번째 줄 메뉴' }).click();
  await press('ArrowDown');
  await press('Enter');
  assert.equal(await markdown(), table('| a | b |', '| --- | --- |', '| c | d |', '|  |  |'));
});

test('끝의 + 막대로 줄·열을 더한다', async () => {
  await open(table('| a |', '| --- |', '| 1 |'));
  await cell(0, 0).hover();
  await page.getByRole('button', { name: '표에 열 추가' }).click();
  await page.getByRole('button', { name: '표에 줄 추가' }).click();
  assert.equal(await markdown(), table('| a |  |', '| --- | --- |', '| 1 |  |', '|  |  |'));
});

test('칸 안 서식: ⌘B, 서식 막대(바꾸기 단추 없음), 칸 안에서 친 **굵게** 가 저장된다', async () => {
  await open(table('| 굵게 칠 글 | b |', '| --- | --- |', '| c | d |'));
  await cell(0, 0).click();
  await press('End');
  await press('Shift+ArrowLeft');
  await press('ControlOrMeta+b');
  assert.equal(await markdown(), table('| 굵게 칠 **글** | b |', '| --- | --- |', '| c | d |'));

  await cell(1, 0).click();
  await press('End');
  await press(`Shift+${WORD}+ArrowLeft`);
  const toolbar = page.getByRole('toolbar', { name: '글자 서식' });
  await toolbar.waitFor();
  assert.equal(await toolbar.getByTitle('블록 바꾸기').count(), 0);
  await toolbar.getByRole('button', { name: /기울임/ }).click();
  assert.equal(await markdown(), table('| 굵게 칠 **글** | b |', '| --- | --- |', '| *c* | d |'));

  await cell(1, 1).click();
  await press('End');
  await type(' **굵게**');
  assert.equal(await markdown(), table('| 굵게 칠 **글** | b |', '| --- | --- |', '| *c* | d **굵게** |'));
  assert.equal(await cell(1, 1).locator('strong').textContent(), '굵게');
});

test('여러 줄 글을 칸에 붙이면 한 줄로 들어가고 블록이 새로 생기지 않는다. | 는 \\| 로 저장된다', async () => {
  await open(table('| a |', '| --- |', '| 1 |'));
  await cell(1, 0).click();
  await press('End');
  await page.evaluate(() => {
    const data = new DataTransfer();
    data.setData('text/plain', '둘\n셋 | 넷\n\n# 다섯');
    document.activeElement.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
  });
  assert.equal(await markdown(), table('| a |', '| --- |', '| 1둘 셋 \\| 넷 # 다섯 |'));
  assert.equal(await page.locator('.block-editor__row').count(), 1);
});

test('⌘Z 는 칸 입력을 되돌리고, ⌘D 는 표를 복제하며 커서는 쓰던 칸에 남는다', async () => {
  await open(table('| a |', '| --- |', '| 1 |'));
  await cell(1, 0).click();
  await press('End');
  await type('23');
  assert.equal(await markdown(), table('| a |', '| --- |', '| 123 |'));
  await press('ControlOrMeta+z');
  assert.equal(await markdown(), table('| a |', '| --- |', '| 1 |'));

  await cell(1, 0).click();
  await press('ControlOrMeta+d');
  assert.equal(await markdown(), `${table('| a |', '| --- |', '| 1 |')}\n\n${table('| a |', '| --- |', '| 1 |')}`);
  assert.equal(await focusedCell(), '1:0');
  await type('!');
  // 복제본은 원본과 칸 배열을 나눠 쓰지 않는다
  assert.equal(await markdown(), `${table('| a |', '| --- |', '| 1! |')}\n\n${table('| a |', '| --- |', '| 1 |')}`);
});

test('표 아래 문단 맨 앞 Backspace 는 표에 붙지 않고 표의 마지막 줄로 간다', async () => {
  await open(`${table('| a |', '| --- |', '| 1 |')}\n\n아래`);
  await texts().first().click();
  await press('Home');
  await press('Backspace');
  assert.equal(await markdown(), `${table('| a |', '| --- |', '| 1 |')}\n\n아래`);
  assert.equal(await focusedCell(), '1:0');
});

test('공개 화면: 제목 행은 th, 정렬과 칸 서식이 그대로 보인다', async () => {
  await page.evaluate(text => window.fixture.post(text), table('| 이름 | 값 |', '| --- | ---: |', '| **a** | 1 |'));
  assert.deepEqual(await page.locator('.post-table th').allTextContents(), ['이름', '값']);
  assert.equal(await page.locator('.post-table td strong').textContent(), 'a');
  assert.equal(await page.locator('.post-table td').nth(1).evaluate(node => getComputedStyle(node).textAlign), 'right');
});
