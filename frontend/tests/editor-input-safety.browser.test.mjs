import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

/**
 * 글쓰기 중 입력이 사라지지 않는지 — 사람이 치는 순서 그대로 확인한다.
 *
 * "블록이 만들어졌다" 로 끝내지 않는다. 블록을 만든 직후 이어 친 글자, 서식을 바꾼 뒤의 앞뒤 글,
 * 붙여넣은 원문이 저장값(마크다운)에 한 글자도 빠짐없이 남는지까지 본다.
 */
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let errors;

const markdown = () => page.evaluate(() => window.fixture.markdown());
const open = value => page.evaluate(text => window.fixture.editor(text), value);
const texts = () => page.locator('.block-editor [contenteditable="true"]');
const type = text => page.keyboard.type(text, { delay: 10 });
const press = key => page.keyboard.press(key);

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
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, permissions: ['clipboard-read', 'clipboard-write'] });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'warning' && msg.text().includes('[Vue warn]')) errors.push(msg.text()); });
  await page.route('**/api/v1/**', route => route.fulfill({ json: { success: true, data: null } }));
  await page.goto(`${server.resolvedUrls.local[0]}tests/editor-comments.html`);
  await page.waitForFunction(() => typeof window.fixture?.editor === 'function');
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('구분선을 만든 직후 이어 친 글자가 빠지지 않는다 (--- 와 /hr 둘 다)', async () => {
  await open('');
  await texts().first().click();
  await type('---');
  await type('바로 이어 쓴 문장');
  assert.equal(await markdown(), '---\n\n바로 이어 쓴 문장');

  await press('Enter');
  await type('앞 문장 /hr');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  await type('구분선 아래 문장');
  assert.equal(await markdown(), '---\n\n바로 이어 쓴 문장\n\n앞 문장\n\n---\n\n구분선 아래 문장');
});

test('문장 중간에서 /h1 을 골라도 앞뒤 글이 남고 커서는 제자리다', async () => {
  await open('prefix suffix');
  await texts().first().click();
  await press('End');
  for (let i = 0; i < 'suffix'.length; i += 1) await press('ArrowLeft');
  await type('/h1');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  assert.equal(await markdown(), '# prefix suffix');

  await type('X');
  assert.equal(await markdown(), '# prefix Xsuffix');
});

test('/ 메뉴의 글자 서식은 커서 자리에 들어가고, 자리 글자를 바로 덮어쓴다', async () => {
  await open('one two');
  await texts().first().click();
  await press('End');
  for (let i = 0; i < 'two'.length; i += 1) await press('ArrowLeft');
  await type('/b');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  await type('bold');
  assert.equal(await markdown(), 'one **bold**two');
});

test('코드 칸에 붙여넣은 여러 줄은 #·빈 줄·들여쓰기까지 코드로 남는다', async () => {
  const code = '# shell comment\necho "hi"\n\n  indented line\n```not a fence';

  await open('');
  await texts().first().click();
  await type('```');
  const area = page.locator('.block-editor__input--code');
  await area.waitFor();

  // 편집기가 코드 칸의 붙여넣기를 가로채지 않는다
  const intercepted = await area.evaluate(element => {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', '# 주석');
    return !element.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData }));
  });
  assert.equal(intercepted, false);

  await page.evaluate(text => navigator.clipboard.writeText(text), code);
  await area.focus();
  await press('ControlOrMeta+v');
  await page.waitForFunction(expected => window.fixture.markdown().includes(expected), 'indented line');

  assert.equal(await markdown(), `\`\`\`\n${code}\n\`\`\``);
  assert.equal(await page.locator('.block-editor__row').count(), 1);
});

test('콜아웃 안 Enter 는 저장값에서도 줄바꿈이고, 빈 끝 줄에서 한 번 더 누르면 빠져나온다', async () => {
  await open(':::tip\nline one\n:::');
  await texts().first().click();
  await press('End');
  await press('Enter');
  await type('line two');
  assert.equal(await markdown(), ':::tip\nline one\nline two\n:::');

  await press('Enter');
  await press('Enter');
  await type('after');
  assert.equal(await markdown(), ':::tip\nline one\nline two\n:::\n\nafter');
});

test('불러온 여러 줄 콜아웃·문단은 줄을 나눠 보여 주고, 손대지 않으면 그대로 저장된다', async () => {
  await open(':::note\n첫 줄\n둘째 줄\n:::\n\n문단 첫 줄\n문단 둘째 줄');
  assert.equal(await texts().nth(0).evaluate(element => element.querySelectorAll('br').length), 1);
  assert.equal(await texts().nth(1).evaluate(element => element.innerText), '문단 첫 줄\n문단 둘째 줄');

  await texts().nth(1).click();
  await press('End');
  await type('!');
  await press('Backspace');
  assert.equal(await markdown(), ':::note\n첫 줄\n둘째 줄\n:::\n\n문단 첫 줄\n문단 둘째 줄');
});

// 굵게 뒤에 이어 친 글자가 굵게 이어지는지는 편집기마다 다르다(브라우저 기본은 이어짐).
// 여기서 지키는 것은 "고른 글의 맨 앞으로 튀지 않고 바로 뒤에 붙는다" 이다
test('굵게를 켜고 끈 뒤에도 고른 글자가 그대로 골라져 있고, 이어 친 글자는 그 뒤에 붙는다', async () => {
  await open('alpha beta');
  await texts().first().click();
  await press('End');
  await press('Shift+Alt+ArrowLeft');
  await press('ControlOrMeta+b');
  assert.equal(await markdown(), 'alpha **beta**');
  assert.equal(await page.evaluate(() => window.getSelection().toString()), 'beta');

  await press('ControlOrMeta+b');
  assert.equal(await markdown(), 'alpha beta');
  assert.equal(await page.evaluate(() => window.getSelection().toString()), 'beta');

  await press('ControlOrMeta+b');
  await press('ArrowRight');
  await type('!');
  assert.match(await markdown(), /^alpha \*\*beta(!\*\*|\*\*!)$/);
});

test('아래·위 키 한 번에 이웃 문단으로 가고, 가로 위치를 지킨다. 구분선은 건너뛴다', async () => {
  await open('first paragraph text\n\nsecond paragraph text\n\n---\n\nthird paragraph text');
  const first = texts().nth(0);
  const box = await first.boundingBox();

  // "first paragraph text" 가운데쯤을 누른다
  await page.mouse.click(box.x + 60, box.y + box.height / 2);
  await press('ArrowDown');
  await type('X');
  const afterDown = (await markdown()).split('\n\n')[1];
  assert.notEqual(afterDown, 'second paragraph text');
  assert.ok(!afterDown.startsWith('X') && !afterDown.endsWith('X'), afterDown);

  await press('Backspace');
  await press('ArrowDown');
  await type('Y');
  assert.match((await markdown()).split('\n\n')[3], /Y/);
  assert.equal((await markdown()).split('\n\n')[2], '---');

  await press('Backspace');
  await press('ArrowUp');
  await press('ArrowUp');
  await type('Z');
  assert.match((await markdown()).split('\n\n')[0], /Z/);
});

test('/ 메뉴는 아래 공간이 모자라면 위로 열리고, 열린 채 스크롤돼도 화면 안에 있고, 방향키로 고른 항목이 늘 보인다', async () => {
  await page.setViewportSize({ width: 1200, height: 640 });
  await open(Array.from({ length: 30 }, (_, i) => `문단 ${i + 1}`).join('\n\n'));
  const target = texts().nth(14);
  await target.click();
  await press('End');
  await type(' /');

  const menu = page.locator('.block-editor__menu');
  await menu.waitFor();

  // 메뉴가 열린 채로 그 줄을 화면 맨 아래로 보낸다. 아래 공간이 없어졌으니 위로 다시 자리를 잡아야 한다
  await target.evaluate(element => element.closest('.block-editor__row').scrollIntoView({ block: 'end' }));
  await page.waitForFunction(() => {
    const list = document.querySelector('.block-editor__menu');
    const box = list?.getBoundingClientRect();
    return list?.classList.contains('admin-slash--up') && box.top >= 0 && box.bottom <= window.innerHeight;
  });

  const total = await page.locator('.admin-slash__item').count();
  for (let i = 1; i < total; i += 1) await press('ArrowDown');
  const visible = await page.evaluate(() => {
    const list = document.querySelector('.block-editor__menu');
    const active = list.querySelector('.admin-slash__item--active');
    const outer = list.getBoundingClientRect();
    const inner = active.getBoundingClientRect();
    return inner.top >= outer.top - 1 && inner.bottom <= outer.bottom + 1 && active.getAttribute('aria-selected') === 'true';
  });
  assert.equal(visible, true);

  await press('Escape');
  await type('계속');
  assert.match(await markdown(), /문단 15 \/계속/);
});

test('글이 없을 때도 쓸 자리가 넉넉하고, 그 아래를 누르면 이어 쓴다', async () => {
  await open('짧은 글');
  const tail = page.locator('.block-editor__tail');
  const box = await tail.boundingBox();
  assert.ok(box.height >= 160, `빈 영역 높이 ${box.height}`);

  await page.mouse.click(box.x + box.width / 2, box.y + box.height - 20);
  await type('이어 쓴 문장');
  assert.equal(await markdown(), '짧은 글\n\n이어 쓴 문장');
});

test('기존 글은 열었다 고치지 않고 저장해도 원문 그대로다', async () => {
  const sample = [
    '# 제목',
    '문단 첫 줄\n같은 문단 둘째 줄',
    ':::tip\n팁 첫 줄\n팁 둘째 줄\n:::',
    '```bash\n# 주석\n\necho "hi"\n```',
    '![설명](https://example.com/a.png "50% center 1200x800")',
    '1. 하나\n2. 둘',
    '> 인용 첫 줄\n> 인용 둘째 줄',
    '- 가\n- 나',
    '---',
    '**굵게** *기울임* `코드` [링크](https://example.com)',
  ].join('\n\n');

  await open(sample);
  await texts().last().click();
  await press('End');
  await type('Z');
  await press('Backspace');
  assert.equal(await markdown(), sample);
});

/**
 * 한글 조합 입력. 완성된 글자를 한 번에 넣지 않고, 입력기처럼 ㅎ → 하 → 한 조합 단계를 거쳐 확정한다.
 * 실제 OS 입력기(받침 바꾸기·한영 전환)까지 대신하지는 못한다 — 그건 사람이 직접 확인한다.
 */
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

test('한글 조합: 조합 중 받침 고치기, 바로 누른 Enter, 제목·굵게 기호, / 메뉴와 섞여도 빠지거나 겹치지 않는다', async () => {
  await open('');
  await texts().first().click();
  // 둘째 글자는 "글" 까지 쳤다가 조합 중에 받침을 지우고(그) 다시 친다(글)
  await compose([['ㅎ', '하', '한'], ['ㄱ', '그', '글', '그', '글']]);
  await press('Enter');
  await type('# ');
  await compose([['ㅈ', '제'], ['ㅁ', '모', '목']]);
  await press('Enter');
  await type('**');
  await compose([['ㄱ', '구', '굵'], ['ㄱ', '게']]);
  await type('**');
  await type(' /');
  await compose([['ㅈ', '제'], ['ㅁ', '모', '목']]);
  await type('2');

  assert.equal(await markdown(), '한글\n\n# 제목\n\n**굵게** /제목2');
  await page.locator('.block-editor__menu').waitFor();
  await press('Enter');
  assert.equal(await markdown(), '한글\n\n# 제목\n\n## **굵게**');

  // 굵은 글자 끝에서 이어 쓰면 굵게가 이어질 수 있다(브라우저 기본). 빠지거나 겹치지만 않으면 된다
  await press('End');
  await compose([['ㄲ', '끝']]);
  assert.match(await markdown(), /^한글\n\n# 제목\n\n## \*\*굵게(끝\*\*|\*\*끝)$/);
});
