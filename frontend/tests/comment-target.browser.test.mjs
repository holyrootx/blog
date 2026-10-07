import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, handleContext, handleComments, contextRequests, replyCursors;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const failure = (route, status) => route.fulfill({ status, json: { success: false, message: '요청 실패' } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const comment = (id, fields = {}) => ({ id, nickname: `회원${id}`, createdAt: '2026-10-03T09:00:00',
  content: `댓글 ${id}`, replies: [], deleted: false, ...fields });
const thread = (parentId, target = 111) => comment(parentId, {
  replies: [...Array.from({ length: 10 }, (_, i) => comment(101 + i)), comment(target)],
  replyHasNext: true, replyNextCursor: 110,
});
const initial = () => ({ total: 50, items: Array.from({ length: 20 }, (_, i) => comment(100 - i)), hasNext: false });
const navigate = id => page.evaluate(async hash => {
  const { default: router } = await import('/src/app/router/index.js');
  await router.push({ name: 'post-detail', params: { id: 1 }, hash: `#comment-${hash}` });
}, id);
const arrived = id => page.waitForFunction(target => {
  const rect = document.getElementById(`comment-${target}`)?.getBoundingClientRect();
  return rect && rect.top >= 75 && rect.top < innerHeight - 40;
}, id);

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = []; contextRequests = []; replyCursors = [];
  handleContext = (route, id) => ok(route, { targetCommentId: id, item: thread(1, id) });
  handleComments = route => ok(route, initial());
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(10000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const context = /\/comments\/(\d+)\/context$/.exec(url.pathname);
    if (context) { contextRequests.push(Number(context[1])); return handleContext(route, Number(context[1])); }
    if (url.pathname.endsWith('/replies')) {
      replyCursors.push(url.searchParams.get('cursor'));
      return ok(route, { items: [comment(111), comment(112)], hasNext: false, nextCursor: null });
    }
    if (url.pathname.endsWith('/comments')) return handleComments(route, url);
    if (/\/posts\/\d+$/.test(url.pathname)) return ok(route, {
      id: Number(url.pathname.split('/').pop()), categoryId: 1, categoryName: '기록', title: '알림 목적지 확인',
      excerpt: '요약', content: Array.from({ length: 12 }, () => '충분한 높이의 글 본문입니다.').join('\n\n'),
      publishedAt: '2026-10-03T09:00:00', views: 1,
    });
    if (url.pathname.endsWith('/related')) return ok(route, []);
    return ok(route, null);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });
const open = hash => page.goto(`${server.resolvedUrls.local[0]}posts/1${hash ?? ''}`);

test('an off-page parent and eleventh reply arrive after more than two seconds; reply pagination remains contiguous', async () => {
  const requested = deferred(); let held;
  handleContext = route => { held = route; requested.resolve(); };
  await open('#comment-111');
  await requested.promise;
  await page.waitForTimeout(2200);
  assert.equal(await page.locator('#comment-111').count(), 0);
  await page.getByText('알림의 댓글을 찾고 있습니다.').waitFor();
  await ok(held, { targetCommentId: 111, item: thread(1) });
  await arrived(111);
  await page.locator('#comment-1').getByRole('button', { name: '답글 더 보기' }).click();
  await page.locator('#comment-112').waitFor();
  assert.equal(await page.locator('#comment-111').count(), 1);
  assert.deepEqual(replyCursors, ['110']);
  assert.equal(await page.locator('.comments__count').textContent(), '50');
});

test('a late context for the previous hash cannot replace the next target; changing hash removes context-only content', async () => {
  const requested = deferred(); let held;
  handleContext = (route, id) => {
    if (id === 111) { held = route; requested.resolve(); return; }
    return ok(route, { targetCommentId: id, item: comment(id) });
  };
  await open('#comment-111');
  await requested.promise;
  await navigate(2);
  await arrived(2);
  await ok(held, { targetCommentId: 111, item: thread(1) }).catch(() => {});
  await page.waitForTimeout(100);
  assert.equal(await page.locator('#comment-111').count(), 0);
  await navigate(3);
  await arrived(3);
  assert.equal(await page.locator('#comment-2').count(), 0);
});

test('missing comments are explained and network failures offer a working retry', async () => {
  handleContext = route => failure(route, 500);
  await open('#comment-111');
  await page.getByText('알림의 댓글을 불러오지 못했습니다. 다시 시도해 주세요.').waitFor();
  handleContext = (route, id) => ok(route, { targetCommentId: id, item: thread(1, id) });
  await page.getByRole('button', { name: '다시 시도', exact: true }).click();
  await arrived(111);
  handleContext = route => failure(route, 404);
  await navigate(999);
  await page.getByText('알림의 댓글을 볼 수 없습니다. 삭제되었거나 운영자가 숨긴 댓글일 수 있습니다.').waitFor();
  assert.equal(await page.getByRole('button', { name: '다시 시도', exact: true }).count(), 0);
  assert.equal(await page.locator('#comment-111').count(), 0);
});

test('a context response from the previous post cannot insert its comment into the new post', async () => {
  const requested = deferred(); let held;
  handleContext = (route, id) => {
    if (id === 111) { held = route; requested.resolve(); return; }
    return ok(route, { targetCommentId: id, item: thread(2, id) });
  };
  await open('#comment-111');
  await requested.promise;
  await page.evaluate(async () => {
    const { default: router } = await import('/src/app/router/index.js');
    await router.push({ name: 'post-detail', params: { id: 2 }, hash: '#comment-222' });
  });
  await arrived(222);
  await ok(held, { targetCommentId: 111, item: thread(1) }).catch(() => {});
  await page.waitForTimeout(100);
  assert.match(page.url(), /\/posts\/2#comment-222$/);
  assert.equal(await page.locator('#comment-111').count(), 0);
  assert.equal(await page.locator('#comment-1').count(), 0);
  assert.equal(await page.locator('#comment-222').count(), 1);
});

test('context waits for the initial page and overlapping normal pages never duplicate a parent', async () => {
  const requested = deferred(); let held;
  handleComments = (route, url) => {
    if (!url.searchParams.has('cursor')) { held = route; requested.resolve(); return; }
    return ok(route, { total: 50, items: [comment(1)], hasNext: false });
  };
  await open('#comment-111');
  await requested.promise;
  assert.deepEqual(contextRequests, []);
  await ok(held, { ...initial(), hasNext: true, nextCursor: 81 });
  await arrived(111);
  // The scroll may already have triggered the intersection observer; otherwise use the keyboard fallback.
  await page.evaluate(() => document.querySelector('.comments__more')?.click());
  await page.waitForFunction(() => !document.querySelector('.comments__more'));
  assert.equal(await page.locator('#comment-1').count(), 1);
  assert.equal(await page.locator('#comment-111').count(), 1);
  await navigate(2);
  await arrived(2);
  // Once the normal page has included parent 1, changing the target must retain that regular row.
  assert.equal(await page.locator('#comment-1').count(), 1);
});

test('back navigation reloads the target thread while preserving the previous scroll position', async () => {
  await open('#comment-111');
  await arrived(111);
  await page.evaluate(() => window.scrollTo({ top: 400, behavior: 'instant' }));
  await page.waitForFunction(() => Math.abs(scrollY - 400) < 2);
  await page.evaluate(async () => {
    const { default: router } = await import('/src/app/router/index.js');
    await router.push('/posts/2');
  });
  await page.waitForURL('**/posts/2');
  await page.goBack();
  await page.waitForURL('**/posts/1#comment-111');
  await page.locator('#comment-111').waitFor();
  await page.waitForFunction(() => Math.abs(scrollY - 400) < 2);
});
