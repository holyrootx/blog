import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, heldPost, postRequested;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

/**
 * 글 6 요청이 실제로 나갈 때까지 기다린다.
 *
 * 시간을 정해 두고 기다리면 느린 CI 에서 화면 코드가 아직 안 와 요청 전에 확인하게 된다.
 * 요청이 끝내 안 나가면 그 사실이 실패 사유로 보이게 시간 제한을 둔다.
 */
function waitForPostRequest() {
  return Promise.race([
    postRequested.promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('post detail request was not sent')), 10000)),
  ]);
}
const post = (id, title) => ({ id, categoryId: 1, categoryName: '생각', title, excerpt: `${title} 요약`,
  content: `${title} 본문`, publishedAt: '2026-09-17T14:07:16', views: 1, thumbnailImageUrl: null });
const robots = () => page.locator('meta[name="robots"]').getAttribute('content');
const canonical = () => page.locator('link[rel="canonical"]').getAttribute('href');

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = []; heldPost = null; postRequested = deferred();
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const { pathname } = new URL(route.request().url());
    if (pathname === '/api/v1/blog/posts/5') return ok(route, post(5, '앞의 글'));
    if (pathname === '/api/v1/blog/posts/5/adjacent') return ok(route, { previousPost: null, nextPost: { id: 6, title: '뒤의 글' } });
    if (pathname === '/api/v1/blog/posts/6') { heldPost = route; postRequested.resolve(); return undefined; }
    if (pathname.endsWith('/comments')) return ok(route, { items: [], total: 0, nextCursor: null, hasNext: false });
    if (pathname.endsWith('/related')) return ok(route, []);
    return ok(route, null);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

/** 운영에서 Nginx 가 글 주소를 백엔드로 넘기면 첫 HTML 에 그 글의 메타가 들어 있다. 그 상태를 흉내 낸다 */
async function serveServerRenderedMeta(path) {
  await page.route(`**${path}`, async route => {
    if (route.request().resourceType() !== 'document') return route.fallback();
    const response = await route.fetch();
    const html = (await response.text()).replace(/<!-- blog-meta:start -->[\s\S]*?<!-- blog-meta:end -->/,
      '<title>서버가 넣은 제목 · 정성주의 기록</title>\n<meta name="robots" content="index,follow" />\n'
      + `<link rel="canonical" href="https://jsjlog.me${path}" />`);
    return route.fulfill({ response, body: html });
  });
}

test('first visit keeps the server metadata even when the post API fails', async () => {
  await serveServerRenderedMeta('/posts/6');
  await page.goto(`${server.resolvedUrls.local[0]}posts/6`);
  await waitForPostRequest();
  assert.equal(await page.title(), '서버가 넣은 제목 · 정성주의 기록');
  await heldPost.fulfill({ status: 500, json: { success: false, code: 'INTERNAL_SERVER_ERROR', message: '오류' } });
  // 실패 화면이 그려진 뒤에 본다. 그 전이면 메타를 건드릴 기회가 아직 남아 있다
  await page.getByText('글을 불러오지 못했습니다').waitFor();
  assert.equal(await page.title(), '서버가 넣은 제목 · 정성주의 기록');
  assert.equal(await robots(), 'index,follow');
  assert.equal(await canonical(), 'https://jsjlog.me/posts/6');
});

test('moving to another post inside the app never marks the loading page noindex', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}posts/5`);
  await page.waitForFunction(() => document.title.startsWith('앞의 글'));
  await page.getByRole('link', { name: /뒤의 글/ }).click();
  await page.waitForURL('**/posts/6');
  await waitForPostRequest();
  await page.waitForFunction(() => document.title.startsWith('글 불러오는 중'));
  assert.notEqual(await robots(), 'noindex,follow');
  assert.match(await canonical(), /\/posts\/6$/);
  await ok(heldPost, post(6, '뒤의 글'));
  await page.waitForFunction(() => document.title.startsWith('뒤의 글'));
  assert.equal(await robots(), 'index,follow');
});
