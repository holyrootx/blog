import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 글자를 고르면 뜨는 서식 막대(노션과 같은 동작).
 *
 * 막대가 "뜨는지" 로 끝내지 않는다. 단추를 눌러도 고른 글자가 그대로 골라져 있는지,
 * 화면에 보이는 서식과 저장값(마크다운)이 같은지까지 본다.
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
// 단어 단위로 고르는 키. 맥은 Alt, 리눅스(CI)·윈도는 Control 이다
const WORD = process.platform === 'darwin' ? 'Alt' : 'Control';
const selected = () => page.evaluate(() => window.getSelection().toString());
const toolbar = () => page.getByRole('toolbar', { name: '글자 서식' });
const button = name => toolbar().getByRole('button', { name, exact: true });
/** 막대 상태는 선택이 바뀐 다음 프레임에 맞춰진다 */
const pressedState = (name, value) => page.waitForFunction(([label, expected]) => document
  .querySelector(`.admin-text-toolbar [aria-label="${label}"]`)?.getAttribute('aria-pressed') === expected, [name, value]);

/** 블록 끝의 마지막 단어를 키보드로 고른다 */
async function selectLastWord(index = 0) {
  await texts().nth(index).click();
  await press('End');
  await press(`Shift+${WORD}+ArrowLeft`);
  await toolbar().waitFor();
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

test('키보드로 고르면 고른 글자 바로 위에 막대가 뜨고, 고르기를 풀면 사라진다', async () => {
  await open('alpha beta gamma');
  await selectLastWord();

  const bar = await toolbar().boundingBox();
  const word = await page.evaluate(() => {
    const rect = window.getSelection().getRangeAt(0).getBoundingClientRect();
    return { top: rect.top, left: rect.left, right: rect.right };
  });
  assert.ok(bar.y + bar.height <= word.top, '고른 글자 위에 있어야 한다');
  assert.ok(bar.x <= word.right && bar.x + bar.width >= word.left, '고른 글자와 가로로 겹쳐야 한다');

  await press('ArrowRight');
  await toolbar().waitFor({ state: 'detached' });
});

test('마우스로 끌어 고르면 손을 뗀 뒤에 뜬다', async () => {
  await open('drag over these words');
  const box = await texts().first().boundingBox();
  await page.mouse.move(box.x + 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + 120, box.y + box.height / 2, { steps: 6 });
  assert.equal(await toolbar().count(), 0, '끄는 동안에는 뜨지 않는다');
  await page.mouse.up();
  await toolbar().waitFor();
  assert.ok((await selected()).length > 0);
});

test('굵게·기울임·코드 단추는 켜고 끄며, 누른 뒤에도 같은 글자가 골라져 있다', async () => {
  await open('alpha beta gamma');
  await selectLastWord();

  await button('굵게').click();
  assert.equal(await markdown(), 'alpha beta **gamma**');
  assert.equal(await selected(), 'gamma');
  await pressedState('굵게', 'true');

  await button('굵게').click();
  assert.equal(await markdown(), 'alpha beta gamma');
  assert.equal(await selected(), 'gamma');

  await button('기울임').click();
  assert.equal(await markdown(), 'alpha beta *gamma*');
  await button('기울임').click();
  await button('코드').click();
  assert.equal(await markdown(), 'alpha beta `gamma`');
  assert.equal(await selected(), 'gamma');

  // 단축키도 같은 일을 한다 (노션: ⌘E 코드)
  await press('ControlOrMeta+e');
  assert.equal(await markdown(), 'alpha beta gamma');
});

test('이미 서식이 있는 글자에는 다른 서식을 걸지 않는다 — 보이는 것과 저장되는 것이 같다', async () => {
  await open('alpha **beta**');
  await selectLastWord();

  await pressedState('굵게', 'true');
  assert.equal(await button('기울임').isDisabled(), true);
  assert.equal(await button('링크').isDisabled(), true);
  await press('ControlOrMeta+i');
  assert.equal(await markdown(), 'alpha **beta**');

  // 서식이 일부 섞인 구간 전체에 걸면, 안쪽 서식을 화면에서도 같이 푼다
  await open('a **b** c');
  await texts().first().click();
  await press('ControlOrMeta+a');
  await toolbar().waitFor();
  await press('ControlOrMeta+i');
  assert.equal(await markdown(), '*a b c*');
  assert.equal(await texts().first().evaluate(element => element.querySelectorAll('strong, b').length), 0);
});

test('링크: 주소를 넣으면 고른 글자에 걸리고, 다시 열어 바꾸거나 지울 수 있다', async () => {
  await open('read the docs');
  await selectLastWord();

  await button('링크').click();
  const input = page.getByRole('textbox', { name: '링크 주소' });
  await input.waitFor();
  assert.equal(await input.evaluate(element => element === document.activeElement), true);

  await input.fill('my site');
  await press('Enter');
  await page.getByRole('alert').filter({ hasText: '띄어쓰기' }).waitFor();

  await input.fill('example.com/docs');
  await press('Enter');
  assert.equal(await markdown(), 'read the [docs](https://example.com/docs)');
  assert.equal(await selected(), 'docs');

  // ⌘K 로 다시 열면 지금 주소가 들어 있다
  await press('ControlOrMeta+k');
  await input.waitFor();
  assert.equal(await input.inputValue(), 'https://example.com/docs');
  await toolbar().getByRole('button', { name: '링크 제거' }).click();
  assert.equal(await markdown(), 'read the docs');
  assert.equal(await selected(), 'docs');
});

test('링크 입력을 Esc 로 닫으면 아무것도 바뀌지 않고 고른 글자가 되살아난다', async () => {
  await open('keep this');
  await selectLastWord();
  await button('링크').click();
  await page.getByRole('textbox', { name: '링크 주소' }).fill('https://example.com');
  await press('Escape');
  assert.equal(await markdown(), 'keep this');
  assert.equal(await selected(), 'this');
  await toolbar().getByRole('button', { name: '굵게' }).waitFor();
});

test('블록 바꾸기: 고른 채로 제목·목록으로 바꾸면 글은 그대로이고 고른 글자도 남는다', async () => {
  await open('first block\n\nsecond block');
  await selectLastWord(0);

  await toolbar().getByRole('button', { name: /텍스트/ }).click();
  await toolbar().getByRole('option', { name: '제목 2' }).click();
  assert.equal(await markdown(), '## first block\n\nsecond block');
  assert.equal(await selected(), 'block');
  await toolbar().getByRole('button', { name: /제목 2/ }).waitFor();

  await toolbar().getByRole('button', { name: /제목 2/ }).click();
  await toolbar().getByRole('option', { name: '글머리 목록' }).click();
  assert.equal(await markdown(), '- first block\n\nsecond block');
});

test('두 블록에 걸쳐 고르면 막대를 띄우지 않는다', async () => {
  await open('one\n\ntwo');
  await selectLastWord(0);

  // 블록마다 입력칸이 따로라 키보드로는 걸칠 수 없다. 마우스로 끌어 걸친 상태를 그대로 만든다
  await page.evaluate(() => {
    const [first, second] = document.querySelectorAll('.block-editor [contenteditable="true"]');
    const range = document.createRange();
    range.setStart(first.firstChild, 1);
    range.setEnd(second.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
  });
  await toolbar().waitFor({ state: 'detached' });
});

test('밑줄·취소선: 단추와 ⌘U·⌘⇧S 로 켜고 끄고, ~~글자~~ 는 치는 즉시 취소선이 된다', async () => {
  await open('alpha beta gamma');
  await selectLastWord();

  await button('밑줄').click();
  assert.equal(await markdown(), 'alpha beta <u>gamma</u>');
  assert.equal(await selected(), 'gamma');
  await pressedState('밑줄', 'true');
  await press('ControlOrMeta+u');
  assert.equal(await markdown(), 'alpha beta gamma');

  await press('ControlOrMeta+Shift+s');
  assert.equal(await markdown(), 'alpha beta ~~gamma~~');
  await pressedState('취소선', 'true');
  await button('취소선').click();
  assert.equal(await markdown(), 'alpha beta gamma');

  await press('End');
  await page.keyboard.type(' ~~old~~ new', { delay: 10 });
  assert.equal(await markdown(), 'alpha beta gamma ~~old~~ new');
  assert.equal(await texts().first().evaluate(element => element.querySelectorAll('s').length), 1);
});

test('불러온 취소선·밑줄은 편집기에서도 그 모양으로 보이고, 손대지 않으면 그대로 저장된다', async () => {
  await open('~~지운 말~~ 그리고 <u>강조</u>');
  assert.equal(await texts().first().evaluate(element => element.innerHTML), '<s>지운 말</s> 그리고 <u>강조</u>');
  await texts().first().click();
  await press('End');
  await page.keyboard.type('!');
  await press('Backspace');
  assert.equal(await markdown(), '~~지운 말~~ 그리고 <u>강조</u>');
});
