import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
let server;
let browser;
let page;
let requests;
let errors;
let handleApi;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9kAAAAASUVORK5CYII=', 'base64');
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = route => route.fulfill({ status: 500, json: { success: false, message: '테스트 요청 실패' } });
const comment = (id, fields = {}) => ({
  id, author: `회원${id}`, nickname: `회원${id}`, createdAt: '2026-10-03T09:00:00',
  content: `댓글 ${id}`, mine: false, deleted: false, likeCount: 0, dislikeCount: 0,
  likedByMe: false, dislikedByMe: false, replies: [], ...fields,
});
const pageOf = (items, fields = {}) => ({ total: items.length, items, hasNext: false, maxLength: 1000, ...fields });
const showComments = data => page.evaluate(value => window.fixture.comments(value), data);
const markdown = () => page.evaluate(() => window.fixture.markdown());
const count = async (selector, expected) => {
  await page.waitForFunction(([s, n]) => document.querySelectorAll(s).length === n, [selector, expected]);
};
const pressed = async (button, expected) => {
  await page.waitForFunction(([id, label, value]) => document.querySelector(`#comment-${id} button[aria-label="${label}"]`)?.getAttribute('aria-pressed') === value,
    [button.id, button.label, String(expected)]);
};

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
  requests = [];
  errors = [];
  handleApi = route => ok(route);
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, permissions: ['clipboard-read', 'clipboard-write'] });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'warning' && msg.text().includes('[Vue warn]')) errors.push(msg.text()); });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    requests.push({ method: request.method(), url: request.url(), body: request.postData(), headers: request.headers() });
    await handleApi(route);
  });
  await page.route('**/fixture.png', route => route.fulfill({ contentType: 'image/png', body: png }));
  await page.goto(`${server.resolvedUrls.local[0]}tests/editor-comments.html`);
  await page.waitForFunction(() => typeof window.fixture?.editor === 'function');
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('block selection: cut, arrow insertion, paste, delete, undo and redo', async () => {
  const handles = page.getByRole('button', { name: '블록 옮기기' });
  await handles.nth(0).click();
  await handles.nth(1).click({ modifiers: ['Shift'] });
  await count('.block-editor__row--selected', 2);
  await page.keyboard.press('Control+x');
  await count('.block-editor__row', 1);
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Control+v');
  await count('.block-editor__row', 3);
  assert.equal(await markdown(), '셋째 문단\n\n첫 문단\n\n둘째 문단');
  await page.keyboard.press('Backspace');
  await count('.block-editor__row', 1);
  await page.keyboard.press('Control+z');
  await count('.block-editor__row', 3);
  await page.keyboard.press('Control+Shift+z');
  await count('.block-editor__row', 1);
});

test('block group drag preserves order; external Markdown paste creates blocks', async () => {
  const handles = page.getByRole('button', { name: '블록 옮기기' });
  await handles.nth(0).click();
  await handles.nth(1).click({ modifiers: ['Shift'] });
  await page.evaluate(() => {
    const dataTransfer = new DataTransfer();
    document.querySelector('.block-editor__handle').dispatchEvent(new DragEvent('dragstart', { bubbles: true, dataTransfer }));
    const tail = document.querySelector('.block-editor__tail');
    tail.dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer }));
    tail.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer }));
  });
  assert.equal(await markdown(), '셋째 문단\n\n첫 문단\n\n둘째 문단');
  await page.evaluate(() => {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', '## 외부 제목\n\n외부 본문');
    document.querySelector('.block-editor').dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData }));
  });
  await count('.block-editor__row', 5);
  assert.match(await markdown(), /## 외부 제목\n\n외부 본문/);
});

test('text input: slash command, Enter, IME guard and reset undo history', async () => {
  await page.evaluate(() => window.fixture.editor(''));
  const text = page.locator('[contenteditable="true"]').first();
  await text.fill('/h1');
  await page.locator('.block-editor__menu').waitFor();
  await text.press('Enter');
  await count('.block-editor__row--heading', 1);
  await page.locator('[contenteditable="true"]').first().fill('제목');
  await page.locator('[contenteditable="true"]').first().press('Enter');
  await count('.block-editor__row', 2);
  await page.locator('[contenteditable="true"]').last().evaluate(element => {
    element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true }));
  });
  await count('.block-editor__row', 2);
  await page.evaluate(() => window.fixture.editor('새 글'));
  await page.locator('[contenteditable="true"]').first().press('Control+z');
  assert.equal(await markdown(), '새 글');
});

test('image block: alignment, caption, keyboard resize, selection, deletion and undo', async () => {
  await page.evaluate(() => window.fixture.editor('![사진](/fixture.png)\n\n본문'));
  await page.getByRole('button', { name: '오른쪽 정렬', exact: true }).click();
  await page.getByRole('button', { name: '캡션 고치기', exact: true }).click();
  assert.equal(await page.getByRole('textbox', { name: '이미지 캡션' }).evaluate(el => el === document.activeElement), true);
  await page.getByRole('textbox', { name: '이미지 캡션' }).fill('바뀐 캡션');
  await page.locator('.block-editor__image-grip').first().press('ArrowLeft');
  assert.match(await markdown(), /"95% right"/);
  assert.match(await markdown(), /바뀐 캡션/);
  await page.locator('.block-editor__image-preview').click();
  await page.keyboard.press('Backspace');
  await count('.block-editor__row--image', 0);
  await page.keyboard.press('Control+z');
  await count('.block-editor__row--image', 1);
  await page.screenshot({ path: join(tmpdir(), 'blog-editor-refactor.png'), fullPage: true });
});

test('image upload: failure, retry, in-flight delete guard and object URL cleanup', async () => {
  let failed = true;
  let release;
  handleApi = route => {
    if (failed) return fail(route);
    return new Promise(resolve => { release = async () => { await ok(route, { url: '/fixture.png', originalName: 'uploaded.png' }); resolve(); }; });
  };
  await page.evaluate(() => {
    window.revoked = [];
    const revoke = URL.revokeObjectURL;
    URL.revokeObjectURL = url => { window.revoked.push(url); revoke(url); };
  });
  async function pasteImage() {
    await page.locator('[contenteditable="true"]').first().evaluate((el, base64) => {
      const bytes = Uint8Array.from(atob(base64), character => character.charCodeAt(0));
      const clipboardData = new DataTransfer();
      clipboardData.items.add(new File([bytes], 'uploaded.png', { type: 'image/png' }));
      el.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData }));
    }, png.toString('base64'));
  }
  await pasteImage();
  await page.locator('.block-editor__image-error').waitFor();
  failed = false;
  const chooser = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: '다시 고르기' }).click();
  await (await chooser).setFiles({ name: 'uploaded.png', mimeType: 'image/png', buffer: png });
  await page.locator('.block-editor__image-status').waitFor();
  await page.locator('.block-editor__image-preview').click();
  await page.keyboard.press('Backspace');
  await count('.block-editor__row--image', 1);
  assert.ok(release);
  await release();
  await page.locator('.block-editor__image-status').waitFor({ state: 'detached' });
  assert.match(await markdown(), /fixture.png/);
  await page.evaluate(() => window.fixture.unmount());
  assert.equal(await page.evaluate(() => window.revoked.length), 2);
});

test('comment composition and reply preserve payloads, focus and CSRF', async () => {
  let id = 40;
  handleApi = route => ok(route, { id: ++id });
  await showComments(pageOf([comment(1)]));
  await page.evaluate(() => window.fixture.focusComment());
  assert.equal(await page.locator('.comment-form__input').evaluate(el => el === document.activeElement), true);
  await page.locator('.comment-form__input').fill('새 댓글');
  await page.getByRole('button', { name: '등록', exact: true }).click();
  await page.locator('#comment-41').waitFor();
  await page.locator('#comment-1').getByRole('button', { name: '답글', exact: true }).click();
  await page.locator('.reply-form__input').waitFor();
  assert.equal(await page.locator('.reply-form__input').evaluate(el => el === document.activeElement), true);
  await page.locator('.reply-form__input').fill('새 답글');
  await page.getByRole('button', { name: '답글 등록', exact: true }).click();
  await page.locator('#comment-42').waitFor();
  assert.deepEqual(requests.map(r => JSON.parse(r.body)), [{ content: '새 댓글', parentId: null }, { content: '새 답글', parentId: 1 }]);
  assert.ok(requests.every(r => r.headers['x-csrf-token'] === 'fixture-token'));
  assert.equal(await page.locator('.comments__count').textContent(), '3');
});

test('comment and reply edit share form; deleting parent preserves reply placeholder', async () => {
  await showComments(pageOf([comment(1, { mine: true, replies: [comment(2, { mine: true })] })], { total: 2 }));
  await page.locator('#comment-2').getByRole('button', { name: '수정', exact: true }).click();
  await page.getByRole('textbox', { name: '답글 고치기' }).fill('수정 답글');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await page.getByText('수정 답글', { exact: true }).waitFor();
  const parentActions = page.locator('#comment-1 > .comment__main .comment__actions');
  await parentActions.getByRole('button', { name: '삭제', exact: true }).click();
  await parentActions.getByRole('button', { name: '지우기', exact: true }).click();
  await page.getByText('삭제된 댓글입니다.', { exact: true }).waitFor();
  await page.locator('#comment-2').getByRole('button', { name: '삭제', exact: true }).click();
  await page.locator('#comment-2').getByRole('button', { name: '지우기', exact: true }).click();
  await page.locator('#comment-2').waitFor({ state: 'detached' });
  assert.equal(await page.locator('.comments__count').textContent(), '0');
  assert.deepEqual(requests.map(r => r.method), ['PUT', 'DELETE', 'DELETE']);
});

test('likes and dislikes remain independent; report submitted once and marked', async () => {
  let likedByMe = false;
  let dislikedByMe = false;
  handleApi = route => {
    const request = route.request();
    if (request.url().includes('/reaction')) {
      const type = request.method() === 'DELETE' ? new URL(request.url()).searchParams.get('type') : request.postDataJSON().type;
      if (type === 'LIKE') likedByMe = request.method() !== 'DELETE';
      else dislikedByMe = request.method() !== 'DELETE';
      return ok(route, { likeCount: Number(likedByMe), dislikeCount: Number(dislikedByMe), likedByMe, dislikedByMe });
    }
    return ok(route);
  };
  await showComments(pageOf([comment(1)]));
  const row = page.locator('#comment-1');
  await row.getByRole('button', { name: '좋아요', exact: true }).click();
  await pressed({ id: 1, label: '좋아요' }, true);
  await row.getByRole('button', { name: '싫어요', exact: true }).click();
  await pressed({ id: 1, label: '싫어요' }, true);
  await row.getByRole('button', { name: '좋아요', exact: true }).click();
  await pressed({ id: 1, label: '좋아요' }, false);
  await pressed({ id: 1, label: '싫어요' }, true);
  await row.getByRole('button', { name: '신고', exact: true }).click();
  await page.getByRole('radio', { name: '광고·도배' }).check();
  await page.getByRole('dialog').getByRole('button', { name: '신고', exact: true }).click();
  await row.getByRole('button', { name: '이미 신고한 댓글' }).waitFor();
  assert.equal(await row.getByRole('button', { name: '이미 신고한 댓글' }).isDisabled(), true);
  assert.deepEqual(JSON.parse(requests.at(-1).body), { reason: 'SPAM', detail: null });
});

test('failed mutations retain input and data; pending submission suppresses duplicates', async () => {
  let release;
  handleApi = route => new Promise(resolve => { release = async () => { await fail(route); resolve(); }; });
  await showComments(pageOf([comment(1, { mine: true })]));
  await page.locator('.comment-form__input').fill('보존할 초안');
  await page.getByRole('button', { name: '등록', exact: true }).click();
  await page.getByRole('button', { name: '등록 중', exact: true }).waitFor();
  await page.locator('.comment-form').dispatchEvent('submit');
  assert.equal(requests.length, 1);
  await release();
  await page.locator('.comment-form__error').waitFor();
  assert.equal(await page.locator('.comment-form__input').inputValue(), '보존할 초안');
  handleApi = fail;
  await page.locator('#comment-1').getByRole('button', { name: '수정', exact: true }).click();
  await page.getByRole('textbox', { name: '댓글 고치기' }).fill('고칠 내용');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await page.locator('.comment-edit__error').waitFor();
  assert.equal(await page.getByRole('textbox', { name: '댓글 고치기' }).inputValue(), '고칠 내용');
  await page.locator('.comment-edit').getByRole('button', { name: '취소', exact: true }).click();
  assert.equal(await page.locator('#comment-1 .comment__text').textContent(), '댓글 1');
});

test('infinite scroll reconnects after skeleton; stale page cannot replace new post', async () => {
  let release;
  handleApi = route => new Promise(resolve => { release = async () => { await ok(route, pageOf([comment(99)])); resolve(); }; });
  await page.evaluate(value => window.fixture.comments(value, true), pageOf([], { hasNext: true, nextCursor: 10 }));
  await page.locator('.comments__initial-skeleton').waitFor();
  assert.equal(requests.length, 0);
  await page.evaluate(() => window.fixture.loading(false));
  await page.waitForResponse(() => false, { timeout: 100 }).catch(() => {});
  await page.locator('.comments__more-skeleton').waitFor();
  assert.equal(requests.length, 1);
  await page.evaluate(value => window.fixture.comments(value, false, 2), pageOf([comment(2)]));
  await release();
  await page.locator('#comment-2').waitFor();
  await count('#comment-99', 0);
  assert.equal(await page.locator('.comments__count').textContent(), '1');
  handleApi = route => ok(route, pageOf([comment(3)], { total: 2 }));
  await showComments(pageOf([comment(2)], { total: 2, hasNext: true, nextCursor: 1 }));
  await page.locator('.comments__sentinel').scrollIntoViewIfNeeded();
  await page.getByRole('button', { name: '댓글 더 보기' }).click().catch(() => {});
  await page.locator('#comment-3').waitFor();
});

test('signed-out UI and mobile/desktop comments retain layout and avatar fallback', async () => {
  await showComments(pageOf([comment(1), comment(2, { mine: true })]));
  await page.evaluate(() => window.fixture.member({ id: 1, nickname: '한글 회원', profileImageUrl: '/broken-avatar.png' }));
  await page.waitForFunction(() => document.querySelector('.comment-form .comment-avatar')?.textContent === '한');
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.equal(await page.locator('.comment-form__actions').evaluate(el => getComputedStyle(el).whiteSpace), 'nowrap');
    await page.screenshot({ path: join(tmpdir(), `blog-comments-refactor-${width}.png`), fullPage: true });
  }
  await page.evaluate(() => window.fixture.member(null));
  await page.getByRole('button', { name: '로그인하고 댓글 쓰기' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '수정', exact: true }).count(), 0);
  assert.equal(await page.getByRole('button', { name: '신고', exact: true }).count(), 0);
  await page.locator('#comment-1').getByRole('button', { name: '좋아요', exact: true }).click();
  assert.equal(await page.evaluate(() => window.fixture.route()), 'member-login');
  assert.equal(requests.length, 0);
});

test('late edit A response preserves the open B draft', async () => {
  let release;
  handleApi = route => new Promise(resolve => { release = async () => { await ok(route); resolve(); }; });
  await showComments(pageOf([comment(1, { mine: true }), comment(2, { mine: true })]));
  await page.locator('#comment-1').getByRole('button', { name: '수정', exact: true }).click();
  await page.getByRole('textbox', { name: '댓글 고치기' }).fill('A 저장');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await page.getByRole('button', { name: '저장 중…' }).waitFor();
  await page.locator('#comment-2').getByRole('button', { name: '수정', exact: true }).click();
  await page.getByRole('textbox', { name: '댓글 고치기' }).fill('B 보관할 초안');
  await release(); await page.getByRole('button', { name: '저장', exact: true }).waitFor();
  assert.equal(await page.getByRole('textbox', { name: '댓글 고치기' }).inputValue(), 'B 보관할 초안');
  assert.equal(await page.locator('#comment-1 .comment__text').textContent(), 'A 저장');
});

test('late create A response leaves post B and its draft intact', async () => {
  let release;
  handleApi = route => new Promise(resolve => { release = async () => { await ok(route, { id: 101 }); resolve(); }; });
  await showComments(pageOf([comment(1)]));
  await page.locator('.comment-form__input').fill('A 댓글');
  await page.getByRole('button', { name: '등록', exact: true }).click();
  await page.getByRole('button', { name: '등록 중', exact: true }).waitFor();
  await page.evaluate(value => window.fixture.comments(value, false, 2), pageOf([comment(2)]));
  await page.locator('.comment-form__input').fill('B 초안');
  await release(); await page.locator('#comment-2').waitFor();
  assert.equal(await page.locator('#comment-101').count(), 0);
  assert.equal(await page.locator('.comment-form__input').inputValue(), 'B 초안');
  assert.equal(await page.locator('.comments__count').textContent(), '1');
});

for (const close of ['Escape', 'button', 'backdrop']) test(`report response marks only original target after ${close} and reopen`, async () => {
  let release;
  handleApi = route => new Promise(resolve => { release = async () => { await ok(route); resolve(); }; });
  await showComments(pageOf([comment(1), comment(2)]));
  await page.locator('#comment-1').getByRole('button', { name: '신고', exact: true }).click();
  await page.getByRole('radio', { name: '광고·도배' }).check();
  await page.getByRole('dialog').getByRole('button', { name: '신고', exact: true }).click();
  await page.getByRole('button', { name: '보내는 중…' }).waitFor();
  if (close === 'Escape') await page.keyboard.press('Escape');
  else if (close === 'button') await page.getByRole('dialog').getByRole('button', { name: '닫기', exact: true }).click();
  else await page.locator('.modal').click({ position: { x: 2, y: 2 } });
  await page.locator('#comment-2').getByRole('button', { name: '신고', exact: true }).click();
  await release(); await page.locator('#comment-1').getByRole('button', { name: '이미 신고한 댓글' }).waitFor();
  assert.equal(await page.getByRole('dialog').count(), 1);
  assert.equal(await page.locator('#comment-2').getByRole('button', { name: '신고', exact: true }).count(), 1);
  assert.equal(requests.length, 1);
});

test('fresh external paste is not replaced by previously held blocks', async () => {
  await page.evaluate(() => window.fixture.editor('기존 복사 블록\n\n붙여넣을 자리'));
  await page.getByRole('button', { name: '블록 옮기기' }).first().click();
  await page.locator('.block-editor').evaluate(element => {
    element.dispatchEvent(new ClipboardEvent('copy', { bubbles: true, cancelable: true, clipboardData: new DataTransfer() }));
  });
  const cancelled = await page.locator('[contenteditable=true]').last().evaluate(element => {
    const clipboardData = new DataTransfer(); clipboardData.setData('text/plain', '새 외부 문장');
    return !element.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData }));
  });
  assert.equal(cancelled, false); // Browser inserts plain text at the caret.
  assert.equal((await markdown()).match(/기존 복사 블록/g).length, 1);
});

test('reply paging offers retry and merges duplicate local replies once', async () => {
  await showComments(pageOf([comment(1, { replies: [comment(2), comment(5)], replyHasNext: true, replyNextCursor: 2 })]));
  handleApi = fail;
  await page.getByRole('button', { name: '답글 더 보기', exact: true }).click();
  await page.getByRole('button', { name: '답글 더 보기', exact: true }).waitFor();
  handleApi = route => ok(route, { items: [comment(3), comment(5)], hasNext: false, nextCursor: null });
  await page.getByRole('button', { name: '답글 더 보기', exact: true }).click();
  await page.locator('#comment-3').waitFor();
  assert.equal(await page.locator('#comment-5').count(), 1);
  assert.equal(await page.getByRole('button', { name: '답글 더 보기', exact: true }).count(), 0);
  assert.match(requests.at(-1).url, /comments\/1\/replies\?size=20&cursor=2/);
});

for (const modifier of ['Control', 'Meta']) test(`${modifier} block paste reads fresh system clipboard and preserves internal groups`, async () => {
  await page.evaluate(() => window.fixture.editor('원래 하나\n\n원래 둘\n\n마지막'));
  const handles = page.getByRole('button', { name: '블록 옮기기' });
  await handles.first().click(); await handles.nth(1).click({ modifiers: ['Shift'] });
  await page.keyboard.press(`${modifier}+c`);
  await page.waitForFunction(async () => (await navigator.clipboard.readText()) === '원래 하나\n\n원래 둘');
  await page.keyboard.press(`${modifier}+v`); await count('.block-editor__row', 5);
  assert.equal((await markdown()).match(/원래 하나/g).length, 2);
  await page.evaluate(() => navigator.clipboard.writeText('새로 복사한 외부 텍스트'));
  await page.keyboard.press(`${modifier}+v`); await count('.block-editor__row', 6);
  assert.match(await markdown(), /새로 복사한 외부 텍스트/);
  assert.equal((await markdown()).match(/원래 하나/g).length, 2);
});

test('clipboard Markdown survives editor page reload', async () => {
  await page.evaluate(() => window.fixture.editor('## 복사할 제목\n\n본문'));
  const handles = page.getByRole('button', { name: '블록 옮기기' });
  await handles.first().click(); await handles.nth(1).click({ modifiers: ['Shift'] });
  await page.keyboard.press('Control+c');
  await page.waitForFunction(async () => (await navigator.clipboard.readText()).startsWith('## 복사할 제목'));
  await page.reload(); await page.waitForFunction(() => typeof window.fixture?.editor === 'function');
  await page.getByRole('button', { name: '블록 옮기기' }).first().click();
  await page.keyboard.press('Control+v'); await count('.block-editor__row', 5);
  assert.match(await markdown(), /## 복사할 제목\n\n본문/);
});

test('failed logout keeps the member and draft visible until a confirmed retry succeeds', async () => {
  let logoutFails = true;
  handleApi = route => {
    if (route.request().url().endsWith('/auth/me')) return ok(route, { id: 1, nickname: '테스트 회원', role: 'USER' });
    if (route.request().url().endsWith('/auth/logout') && logoutFails) return fail(route);
    return ok(route);
  };
  await showComments(pageOf([comment(1)]));
  await page.locator('.comment-form__input').fill('유지할 초안');
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
  assert.equal(await page.locator('.comment-form__input').inputValue(), '유지할 초안');
  logoutFails = false;
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByRole('button', { name: '로그인하고 댓글 쓰기' }).waitFor();
});
