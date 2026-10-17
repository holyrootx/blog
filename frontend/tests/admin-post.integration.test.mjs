import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

// backend: ./gradlew browserIntegrationTest가 임시 DB와 계정을 준비하고 이 파일을 실행한다.
// 로그인·저장·발행·조회 응답을 가로채거나 대체하지 않는다.
const backend = new URL(process.env.BLOG_INTEGRATION_BACKEND_URL);
assert.equal(backend.hostname, '127.0.0.1');
assert.equal(backend.protocol, 'http:');
assert.ok(backend.port);
const title = process.env.BLOG_INTEGRATION_TITLE;
const content = process.env.BLOG_INTEGRATION_CONTENT;
const excerpt = process.env.BLOG_INTEGRATION_EXCERPT;
const password = process.env.BLOG_INTEGRATION_ADMIN_PASSWORD;
assert.ok(title && content && excerpt && password);
let server, browser, origin;

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0, proxy: { '/api': backend.origin } } });
  await server.listen();
  origin = server.resolvedUrls.local[0].replace(/\/$/, '');
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => {
  try { await browser?.close(); } finally { await server?.close(); }
});

async function data(response, status = 200) {
  assert.equal(response.status(), status);
  assert.match(response.headers()['content-type'], /application\/json/);
  const body = await response.json();
  assert.equal(body.success, status === 200);
  return body.data;
}

async function csrf(request) {
  const token = await data(await request.get(`${origin}/api/v1/auth/csrf`));
  return { [token.headerName]: token.token };
}

function responseTo(page, method, path) {
  return page.waitForResponse(response => response.request().method() === method
    && new URL(response.url()).pathname === path);
}

async function reopen(page, id, body) {
  await page.goto(`${origin}/admin/posts/${id}`);
  await page.getByPlaceholder('글 제목', { exact: true }).waitFor();
  await page.waitForFunction(expected => document.querySelector('input[placeholder="글 제목"]')?.value === expected, title);
  assert.equal(await page.getByLabel('요약', { exact: true }).inputValue(), excerpt);
  assert.equal(await page.locator('[contenteditable="true"]').first().innerText(), body);
}

test('real login, CSRF rejection, save, reopen, edit, publish and public read', { timeout: 90000 }, async () => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const anonymous = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  try {
    await data(await anonymous.request.get(`${origin}/api/v1/admin/blog/posts`), 401);
    await data(await anonymous.request.post(`${origin}/api/v1/admin/auth/login`, {
      data: { username: 'browser-test-admin', password },
    }), 403);
    const beforeLogin = await csrf(context.request);
    await data(await context.request.post(`${origin}/api/v1/admin/auth/login`, {
      headers: beforeLogin, data: { username: 'browser-test-admin', password: 'wrong-password' },
    }), 401);

    await page.goto(`${origin}/admin/login?redirect=/admin/posts/new`);
    await page.getByLabel('아이디', { exact: true }).fill('browser-test-admin');
    await page.getByLabel('비밀번호', { exact: true }).fill(password);
    const login = responseTo(page, 'POST', '/api/v1/admin/auth/login');
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await data(await login);
    await page.waitForURL('**/admin/posts/new');
    assert.ok((await context.cookies()).some(cookie => cookie.name === 'JSESSIONID' && cookie.httpOnly));
    assert.equal((await data(await context.request.get(`${origin}/api/v1/admin/auth/me`))).role, 'ROLE_ADMIN');

    const invalidPost = { title: '거절돼야 하는 글', content: '본문', excerpt, categoryId: null };
    for (const headers of [{}, { 'X-CSRF-TOKEN': 'invalid' }, beforeLogin]) {
      await data(await context.request.post(`${origin}/api/v1/admin/blog/posts`, { headers, data: invalidPost }), 403);
    }
    assert.equal((await data(await context.request.get(`${origin}/api/v1/admin/blog/posts`))).totalElements, 0);

    await page.getByPlaceholder('글 제목', { exact: true }).fill(title);
    await page.locator('.admin-field').filter({ has: page.locator('.admin-field__label').filter({ hasText: /^카테고리$/ }) }).getByRole('combobox').click();
    await page.getByRole('option', { name: '통합 검사', exact: true }).click();
    await page.getByLabel('요약', { exact: true }).fill(excerpt);
    await page.locator('[contenteditable="true"]').first().fill(content);
    const created = responseTo(page, 'POST', '/api/v1/admin/blog/posts');
    await page.getByRole('button', { name: '임시저장', exact: true }).click();
    const id = await data(await created);
    assert.ok(Number.isInteger(id) && id > 0);
    await page.waitForURL(`**/admin/posts/${id}`);
    await page.getByText('임시저장했습니다.', { exact: true }).waitFor();
    await reopen(page, id, content);
    const draft = await data(await context.request.get(`${origin}/api/v1/admin/blog/posts/${id}`));
    assert.equal(draft.content, content);
    assert.equal(draft.status, 'DRAFT');
    await data(await anonymous.request.get(`${origin}/api/v1/blog/posts/${id}`), 404);

    const editedContent = `${content} 수정한 내용도 유지됩니다.`;
    await page.locator('[contenteditable="true"]').first().fill(editedContent);
    const edited = responseTo(page, 'PUT', `/api/v1/admin/blog/posts/${id}`);
    await page.getByRole('button', { name: '임시저장', exact: true }).click();
    await data(await edited);
    await page.waitForURL('**/admin/posts');
    await page.getByRole('row').filter({ hasText: title }).click();
    await page.waitForURL(`**/admin/posts/${id}*`);
    await page.locator('[contenteditable="true"]').first().waitFor();
    await page.waitForFunction(expected => document.querySelector('[contenteditable="true"]')?.innerText === expected, editedContent);
    await reopen(page, id, editedContent);

    await page.getByRole('button', { name: '발행', exact: true }).click();
    const published = responseTo(page, 'POST', `/api/v1/admin/blog/posts/${id}/publish`);
    await page.getByRole('dialog').getByRole('button', { name: '확인', exact: true }).click();
    await data(await published);
    await page.getByRole('button', { name: '내리기', exact: true }).waitFor();
    const publicPost = await data(await anonymous.request.get(`${origin}/api/v1/blog/posts/${id}`));
    assert.equal(publicPost.status, 'PUBLISHED');
    assert.equal(publicPost.title, title);
    assert.equal(publicPost.content, editedContent);
    assert.equal(publicPost.excerpt, excerpt);
    assert.ok((await data(await anonymous.request.get(`${origin}/api/v1/blog/posts`))).items.some(post => post.id === id));
    const reader = await anonymous.newPage();
    reader.on('pageerror', error => pageErrors.push(error.message));
    await reader.goto(`${origin}/posts/${id}`);
    await reader.getByRole('heading', { name: title, exact: true }).waitFor();
    await reader.getByText(editedContent, { exact: true }).waitFor();
    await reader.reload();
    await reader.getByText(editedContent, { exact: true }).waitFor();

    await data(await context.request.post(`${origin}/api/v1/admin/auth/logout`, { headers: await csrf(context.request) }));
    await data(await context.request.get(`${origin}/api/v1/admin/blog/posts/${id}`), 401);
    assert.deepEqual(pageErrors, []);
  } catch (error) {
    await mkdir('reports/integration', { recursive: true });
    await page.screenshot({ path: 'reports/integration/admin-post-failure.png', fullPage: true }).catch(() => {});
    throw error;
  } finally {
    await context.close();
    await anonymous.close();
  }
});
