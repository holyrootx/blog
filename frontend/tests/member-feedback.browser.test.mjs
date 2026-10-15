import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, member, unread, notices;
let listHandler, countHandler, readHandler, allHandler, commentsHandler, repliesHandler, mineHandler;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = route => route.fulfill({ status: 500, json: { success: false, message: '일시적인 오류입니다.' } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const comment = (id, fields = {}) => ({ id, nickname: `회원${id}`, createdAt: '2026-10-15T09:00:00', content: `댓글 본문 ${id}`, replies: [], ...fields });
const notice = (id, fields = {}) => ({ id, type: 'REPLY', actorNickname: '답글 작성자', postId: 2, postTitle: '테스트 글 2', commentId: 9,
  preview: `알림 내용 ${id}`, createdAt: '2026-10-15T09:00:00', unread: true, ...fields });
const mine = id => ({ id, postId: 1, postTitle: '테스트 글 1', content: `내 댓글 ${id}`, createdAt: '2026-10-15T09:00:00' });
const bell = () => page.locator('.notification-bell__trigger');
const panel = () => page.locator('.notification-bell__dropdown');
const openBell = async () => { await bell().click(); await panel().waitFor(); };
const expectText = (selector, text) => page.waitForFunction(({ selector, text }) => document.querySelector(selector)?.textContent.includes(text), { selector, text });
const visit = path => page.goto(`${server.resolvedUrls.local[0]}${path}`);
const switchMember = async id => {
  member = { id, nickname: `회원${id}`, role: 'USER' };
  await page.evaluate(async () => { const auth = await import('/src/features/member/data/memberAuthStore.js'); await auth.refreshMemberSession(); });
};

before(async () => {
  server = await createServer({ root: process.env.TEST_APP_ROOT || fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = [];
  member = { id: 7, nickname: '테스터', role: 'USER' };
  unread = 47; notices = Array.from({ length: 30 }, (_, i) => notice(i + 1));
  listHandler = route => ok(route, notices);
  countHandler = route => ok(route, unread);
  readHandler = (route, id) => {
    const item = notices.find(item => item.id === id);
    if (item?.unread) { item.unread = false; unread -= 1; }
    return ok(route);
  };
  allHandler = route => { unread = 0; notices.forEach(item => { item.unread = false; }); return ok(route); };
  commentsHandler = route => ok(route, { total: 1, items: [comment(1)], hasNext: false });
  repliesHandler = route => ok(route, { items: [comment(12)], hasNext: false });
  mineHandler = route => ok(route, [mine(1)]);
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(8000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url()), path = url.pathname;
    if (path === '/api/v1/auth/me') return ok(route, member);
    if (path === '/api/v1/auth/csrf') return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/notifications/unread-count')) return countHandler(route);
    if (path.endsWith('/notifications/read')) return allHandler(route);
    const read = /\/notifications\/(\d+)\/read$/.exec(path);
    if (read) return readHandler(route, Number(read[1]));
    if (path.endsWith('/notifications')) return listHandler(route);
    if (path === '/api/v1/auth/me/comments') return mineHandler(route);
    const context = /\/comments\/(\d+)\/context$/.exec(path);
    if (context) return ok(route, { targetCommentId: Number(context[1]), item: comment(Number(context[1])) });
    if (path.endsWith('/replies')) return repliesHandler(route, url);
    if (path.endsWith('/comments')) return commentsHandler(route, url);
    if (/\/posts\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop());
      return ok(route, { id, categoryId: 1, categoryName: '개발', title: `테스트 글 ${id}`, excerpt: '본문 확인',
        content: '성공한 본문은 계속 표시합니다.\n\n## 내용\n\n댓글과 분리된 본문입니다.', publishedAt: '2026-10-15T09:00:00', views: 1 });
    }
    if (path.endsWith('/related')) return ok(route, []);
    return ok(route);
  });
});
afterEach(async (context) => {
  if (page && process.env.QA_SCREENSHOT_DIR) {
    await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 100)}.png`, fullPage: true });
  }
  await page?.close(); assert.deepEqual(errors, []);
});

test('opening the latest 30 notifications preserves the unread total of 47', async () => {
  await visit('posts/1'); await expectText('.notification-bell__badge', '47');
  await openBell(); await panel().locator('button.notification-bell__item').first().waitFor();
  assert.equal(await page.locator('.notification-bell__badge').textContent(), '47');
  assert.equal(await panel().locator('button.notification-bell__item').count(), 30);
});

test('unread notifications outside the latest page keep the all-read action available', async () => {
  unread = 17; notices.forEach(item => { item.unread = false; });
  await visit('posts/1'); await openBell(); await panel().locator('button.notification-bell__item').first().waitFor();
  await panel().getByRole('button', { name: '모두 읽음', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('.notification-bell__badge'));
  assert.equal(unread, 0);
});

test('an unresolved read request cannot delay navigation to the target comment', async () => {
  const requested = deferred(); let held;
  readHandler = route => { held = route; requested.resolve(); };
  await visit('posts/1'); await openBell(); await panel().locator('button.notification-bell__item').first().click();
  await requested.promise;
  await page.waitForURL('**/posts/2#comment-9', { timeout: 2000 });
  await page.locator('#comment-9').waitFor();
  await ok(held);
});

test('a failed read still navigates and reports failure without losing the unread count', async () => {
  readHandler = fail;
  await visit('posts/1'); await openBell(); await panel().locator('button.notification-bell__item').first().click();
  await page.waitForURL('**/posts/2#comment-9');
  await page.getByText('알림의 읽음 처리를 완료하지 못했습니다.', { exact: true }).waitFor();
  await expectText('.notification-bell__badge', '47');
});

test('a failed all-read request keeps unread items and offers another attempt', async () => {
  allHandler = fail;
  await visit('posts/1'); await openBell();
  await panel().getByRole('button', { name: '모두 읽음', exact: true }).click();
  await panel().getByText('읽음 처리를 완료하지 못했습니다. 다시 시도해 주세요.').waitFor();
  await expectText('.notification-bell__badge', '47');
  allHandler = route => { unread = 0; notices.forEach(item => { item.unread = false; }); return ok(route); };
  await panel().getByRole('button', { name: '모두 읽음', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('.notification-bell__badge'));
});

test('first notification failure shows retry instead of an empty inbox', async () => {
  listHandler = fail;
  await visit('posts/1'); await openBell();
  await panel().getByText('알림을 불러오지 못했습니다.', { exact: true }).waitFor();
  assert.equal(await panel().getByText('아직 알림이 없습니다.').count(), 0);
  listHandler = route => ok(route, []);
  await panel().getByRole('button', { name: '다시 시도', exact: true }).click();
  await page.waitForFunction(async () => (await import('/src/features/member/data/notificationStore.js')).useNotifications().loaded.value);
  await panel().getByText('아직 알림이 없습니다.').waitFor();
  assert.equal(await bell().evaluate(el => el === document.activeElement), true);
});

test('failed notification and count refresh retains data with an explicit stale-data notice', async () => {
  notices = [notice(1)];
  await visit('posts/1'); await openBell(); await panel().getByText('알림 내용 1').waitFor();
  await bell().click(); listHandler = fail; countHandler = fail;
  await openBell(); await panel().getByText('알림을 새로 불러오지 못했습니다. 이전에 불러온 목록을 표시합니다.').waitFor();
  await panel().getByText('미확인 알림 수를 갱신하지 못했습니다.').waitFor();
  assert.equal(await panel().getByText('알림 내용 1').count(), 1);
  assert.equal(await page.locator('.notification-bell__badge').textContent(), '47');
  listHandler = route => ok(route, [notice(2)]); countHandler = route => ok(route, 48);
  await panel().getByRole('button', { name: '다시 시도', exact: true }).click();
  await panel().getByText('알림 내용 2').waitFor(); await expectText('.notification-bell__badge', '48');
  assert.equal(await panel().getByRole('alert').count(), 0);
});

test('a late list failure from an earlier opening cannot replace the latest success', async () => {
  const requested = deferred(); let held;
  listHandler = route => { held = route; requested.resolve(); };
  await visit('posts/1'); await openBell(); await requested.promise; await bell().click();
  listHandler = route => ok(route, [notice(99)]);
  await openBell(); await panel().getByText('알림 내용 99').waitFor();
  await fail(held);
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await panel().getByRole('alert').count(), 0);
  assert.equal(await panel().getByText('알림 내용 99').count(), 1);
});

test('changing accounts clears pending notification data and ignores the old response', async () => {
  const requested = deferred(); let held;
  listHandler = route => { held = route; requested.resolve(); };
  await visit('posts/1'); await openBell(); await requested.promise;
  listHandler = route => ok(route, [notice(99)]); unread = 3;
  await switchMember(8); await openBell(); await panel().getByText('알림 내용 99').waitFor();
  await ok(held, [notice(1)]);
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await panel().getByText('알림 내용 1', { exact: true }).count(), 0);
  assert.equal(await page.locator('.notification-bell__badge').textContent(), '3');
});

for (const width of [1280, 390]) test(`notification disclosure supports keyboard navigation and Escape at ${width}px`, async () => {
  notices = [notice(1), notice(2)];
  await page.setViewportSize({ width, height: 900 }); await visit('posts/1');
  await bell().focus(); await page.keyboard.press('Enter'); await panel().getByText('알림 내용 1').waitFor();
  assert.equal(await panel().getAttribute('role'), 'region');
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement.textContent.trim()), '모두 읽음');
  await page.keyboard.press('Tab'); assert.equal(await page.evaluate(() => document.activeElement.classList.contains('notification-bell__item')), true);
  await page.keyboard.press('Escape');
  assert.equal(await bell().getAttribute('aria-expanded'), 'false');
  assert.equal(await bell().evaluate(el => el === document.activeElement), true);
  await page.keyboard.press('Enter'); await panel().getByText('알림 내용 1').waitFor();
  await page.keyboard.press('Tab'); await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Shift+Tab');
  assert.equal(await bell().getAttribute('aria-expanded'), 'false');
  if (width === 390) {
    await page.evaluate(() => document.documentElement.dataset.theme = 'dark');
    await openBell(); await panel().getByText('알림 내용 1').waitFor();
    const rect = await panel().boundingBox();
    assert.ok(rect.x >= 0 && rect.x + rect.width <= width, 'dropdown fits the viewport');
  }
});

test('my-comment failure leaves profile controls usable and retries into a real empty list', async () => {
  mineHandler = fail;
  await visit('settings');
  await page.getByText('내 댓글을 불러오지 못했습니다.', { exact: true }).waitFor();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '테스터');
  assert.equal(await page.getByText('아직 쓴 댓글이 없습니다.').count(), 0);
  mineHandler = route => ok(route, []);
  await page.getByRole('button', { name: '다시 시도', exact: true }).click();
  await page.getByText('아직 쓴 댓글이 없습니다.').waitFor();
});

test('my-comment refresh preserves the old list and nickname draft on network failure', async () => {
  await visit('settings'); await page.getByText('내 댓글 1', { exact: true }).waitFor();
  await page.getByLabel('닉네임', { exact: true }).fill('작성 중인 닉네임');
  mineHandler = route => route.abort('failed');
  await page.getByRole('button', { name: '내 댓글 새로고침', exact: true }).click();
  await page.getByText('댓글을 새로 불러오지 못했습니다. 이전에 불러온 목록을 표시합니다.').waitFor();
  assert.equal(await page.getByText('내 댓글 1', { exact: true }).count(), 1);
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '작성 중인 닉네임');
  mineHandler = route => ok(route, [mine(2)]);
  await page.getByRole('button', { name: '다시 시도', exact: true }).click(); await page.getByText('내 댓글 2', { exact: true }).waitFor();
  assert.equal(await page.locator('.member-comments__date').getAttribute('datetime'), '2026-10-15T09:00:00');
});

test('my comments ignore an earlier account response after account replacement', async () => {
  const requested = deferred(); let held;
  mineHandler = route => { held = route; requested.resolve(); };
  await visit('settings'); await requested.promise;
  mineHandler = route => ok(route, [mine(8)]); await switchMember(8);
  await page.getByText('내 댓글 8', { exact: true }).waitFor(); await ok(held, [mine(7)]);
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await page.getByText('내 댓글 7', { exact: true }).count(), 0);
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '회원8');
});

test('public comment failure preserves the article and distinguishes retry from a normal empty list', async () => {
  commentsHandler = fail;
  await visit('posts/1'); await page.getByText('댓글을 불러오지 못했습니다.', { exact: true }).waitFor();
  assert.equal(await page.getByRole('heading', { name: '테스트 글 1', exact: true }).count(), 1);
  assert.equal(await page.getByText('아직 댓글이 없습니다', { exact: true }).count(), 0);
  commentsHandler = route => ok(route, { total: 0, items: [], hasNext: false });
  await page.locator('.comments').getByRole('button', { name: '다시 시도', exact: true }).click();
  await page.getByText('아직 댓글이 없습니다', { exact: true }).waitFor();
});

test('failed public refresh preserves the list and an in-progress comment draft', async () => {
  await visit('posts/1'); await page.locator('#comment-1').waitFor();
  const input = page.locator('.comment-form textarea'); await input.fill('아직 등록하지 않은 댓글');
  commentsHandler = fail;
  await page.getByRole('button', { name: '댓글 새로고침', exact: true }).click();
  await page.getByText('댓글을 새로 불러오지 못했습니다. 이전에 불러온 목록을 표시합니다.').waitFor();
  assert.equal(await page.locator('#comment-1').count(), 1); assert.equal(await input.inputValue(), '아직 등록하지 않은 댓글');
  commentsHandler = route => ok(route, { total: 1, items: [comment(2)], hasNext: false });
  await page.locator('.comments').getByRole('button', { name: '다시 시도', exact: true }).click(); await page.locator('#comment-2').waitFor();
  assert.equal(await input.inputValue(), '아직 등록하지 않은 댓글');
  assert.equal(await page.locator('#comment-2 .comment__time').getAttribute('datetime'), '2026-10-15T09:00:00');
});

test('failed next-page fetch keeps existing comments and retries the same cursor only on request', async () => {
  let requests = 0, retry = false;
  commentsHandler = (route, url) => {
    if (!url.searchParams.has('cursor')) return ok(route, { total: 21, items: Array.from({ length: 20 }, (_, i) => comment(30 - i)), hasNext: true, nextCursor: 11 });
    assert.equal(url.searchParams.get('cursor'), '11'); requests += 1;
    return retry ? ok(route, { total: 21, items: [comment(10)], hasNext: false }) : fail(route);
  };
  await visit('posts/1'); await page.locator('#comment-30').waitFor(); await page.locator('.comments__sentinel').scrollIntoViewIfNeeded();
  await page.getByText('다음 댓글을 불러오지 못했습니다. 다시 시도해 주세요.').waitFor();
  assert.equal(await page.locator('#comment-30').count(), 1);
  await page.locator('#comment-30').scrollIntoViewIfNeeded(); await page.locator('.comments__sentinel').scrollIntoViewIfNeeded();
  await page.evaluate(() => new Promise(requestAnimationFrame)); assert.equal(requests, 1);
  retry = true; await page.getByRole('button', { name: '댓글 다시 시도', exact: true }).click();
  await page.locator('#comment-10').waitFor(); assert.equal(requests, 2);
});

test('reply failures preserve earlier replies and retry into the same thread', async () => {
  commentsHandler = route => ok(route, { total: 3, items: [comment(1, { replies: [comment(11)], replyHasNext: true, replyNextCursor: 11 })], hasNext: false });
  repliesHandler = fail;
  await visit('posts/1'); await page.getByRole('button', { name: '답글 더 보기', exact: true }).click();
  await page.getByText('답글을 불러오지 못했습니다. 다시 시도해 주세요.').waitFor();
  assert.equal(await page.locator('#comment-11').count(), 1);
  repliesHandler = route => ok(route, { items: [comment(12)], hasNext: false });
  await page.getByRole('button', { name: '답글 다시 시도', exact: true }).click(); await page.locator('#comment-12').waitFor();
  assert.equal(await page.locator('#comment-11').count(), 1);
});

test('late comment errors from a previous post never replace the next post', async () => {
  const held = [];
  commentsHandler = (route, url) => {
    if (url.pathname.includes('/posts/1/')) { held.push(route); return; }
    return ok(route, { total: 1, items: [comment(22)], hasNext: false });
  };
  await visit('posts/1'); await bell().waitFor();
  await page.evaluate(async () => { const { default: router } = await import('/src/app/router/index.js'); await router.push('/posts/2'); });
  await page.locator('#comment-22').waitFor();
  for (const route of held) await fail(route);
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await page.getByText('댓글을 불러오지 못했습니다.', { exact: true }).count(), 0);
  assert.equal(await page.locator('#comment-22').count(), 1);
});

test('submitting a comment during a held refresh cannot lose it when the stale list arrives', async () => {
  await visit('posts/1'); await page.locator('#comment-1').waitFor();
  const held = deferred(); let oldList;
  commentsHandler = route => {
    if (route.request().method() === 'POST') return ok(route, { id: 99 });
    oldList = route; held.resolve();
  };
  await page.getByRole('button', { name: '댓글 새로고침', exact: true }).click(); await held.promise;
  await page.locator('.comment-form textarea').fill('새로 저장한 댓글');
  await page.locator('.comment-form').getByRole('button', { name: '등록', exact: true }).click();
  await page.locator('#comment-99').waitFor();
  await ok(oldList, { total: 1, items: [comment(1)], hasNext: false });
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await page.locator('#comment-99').textContent().then(text => text.includes('새로 저장한 댓글')), true);
});
