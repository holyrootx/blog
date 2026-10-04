import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server;
let browser;
let page;
let categoriesRequest;
let errors;
const categories = [{ id: 1, name: '테스트 분류', sortOrder: 1 }];
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const titleInput = () => page.getByPlaceholder('글 제목', { exact: true });
const bodyInput = () => page.locator('[contenteditable=true]').first();
const readDraft = () => page.evaluate(() => JSON.parse(localStorage.getItem('admin:post-draft:new')));

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
  categoriesRequest = null;
  errors = [];
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/categories')) { categoriesRequest = route; return; }
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'test', role: 'ADMIN' });
    if (path === '/api/v1/auth/me') return ok(route, null);
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/sidebar/menus')) return ok(route, []);
    if (path === '/api/v1/admin/blog/posts/9001') return ok(route, {
      id: 9001, title: '저장된 제목', categoryId: 1, excerpt: '저장된 요약',
      content: '저장된 본문', thumbnailImageUrl: '', status: 'DRAFT',
      updatedAt: '2026-10-04T00:00:00', publishedAt: null,
    });
    if (path.endsWith('/posts')) return ok(route, { items: [], totalElements: 0 });
    return ok(route);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function openNewAndType() {
  await page.goto(`${server.resolvedUrls.local[0]}admin/posts/new`);
  await titleInput().fill('분류를 기다리며 입력한 제목');
  await bodyInput().fill('분류를 기다리며 입력한 본문');
  assert.ok(categoriesRequest);
}

async function assertInputAndDraft() {
  assert.equal(await titleInput().inputValue(), '분류를 기다리며 입력한 제목');
  assert.equal(await bodyInput().textContent(), '분류를 기다리며 입력한 본문');
  await page.evaluate(() => window.dispatchEvent(new Event('beforeunload')));
  const draft = await readDraft();
  assert.equal(draft?.form.title, '분류를 기다리며 입력한 제목');
  assert.equal(draft?.form.content, '분류를 기다리며 입력한 본문');
}

for (const failure of [false, true]) {
  test(`new post preserves input after delayed categories ${failure ? 'fail' : 'succeed'}`, async () => {
    await openNewAndType();
    const response = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/categories'));
    if (failure) await categoriesRequest.fulfill({ status: 500, json: { success: false } });
    else await ok(categoriesRequest, categories);
    await response;
    // Let the API promise and Vue render finish before inspecting the user's input.
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await assertInputAndDraft();
  });
}

test('new post saves an unload draft while categories are still pending', async () => {
  await openNewAndType();
  await assertInputAndDraft();
});

test('late categories after leaving the editor cannot clear the saved draft', async () => {
  await openNewAndType();
  await page.getByRole('link', { name: '목록', exact: true }).click();
  await page.waitForURL('**/admin/posts');
  await ok(categoriesRequest, categories);
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const draft = await readDraft();
  assert.equal(draft?.form.title, '분류를 기다리며 입력한 제목');
  assert.equal(draft?.form.content, '분류를 기다리며 입력한 본문');
});

test('existing post loads independently of a pending category request', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/posts/9001`);
  await page.waitForFunction(() => document.querySelector('input[placeholder="글 제목"]')?.value === '저장된 제목');
  await titleInput().fill('분류 도착 전 수정한 제목');
  await ok(categoriesRequest, categories);
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  assert.equal(await titleInput().inputValue(), '분류 도착 전 수정한 제목');
});
