import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, posts, requests, errors, holdSave, pendingSave, saveFailure;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const filters = { page: '3', size: '50', status: 'DRAFT', categoryId: '1', keyword: '이동' };
const title = () => page.getByPlaceholder('글 제목', { exact: true });
const save = () => page.getByRole('button', { name: '임시저장', exact: true });
const category = () => page.locator('.admin-field').filter({
  has: page.locator('.admin-field__label').filter({ hasText: /^카테고리$/ }),
});
const baseUrl = () => server.resolvedUrls.local[0];
const listUrl = (query = {}) => `${baseUrl()}admin/posts?${new URLSearchParams(query)}`;
const listRequests = () => requests.filter(r => r.method === 'GET' && r.path === '/api/v1/admin/blog/posts');
const query = () => Object.fromEntries(new URL(page.url()).searchParams);

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  posts = Array.from({ length: 101 }, (_, index) => ({
    id: index + 1, title: `이동 검증 글 ${index + 1}`, categoryId: 1, categoryName: '개발',
    status: 'DRAFT', excerpt: '저장된 요약', content: '저장된 본문', thumbnailImageUrl: '',
    createdAt: '2026-10-11T10:00:00', updatedAt: '2026-10-11T10:00:00', views: 0,
  }));
  requests = []; errors = []; holdSave = false; pendingSave = null; saveFailure = null;
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(6000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();
    requests.push({ path, method, query: Object.fromEntries(url.searchParams),
      body: route.request().postData() ? route.request().postDataJSON() : null });
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'test', role: 'ADMIN' });
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/categories')) return ok(route, [
      { id: 1, name: '개발', sortOrder: 1 }, { id: 2, name: '일상', sortOrder: 2 },
    ]);
    if (path === '/api/v1/admin/blog/posts') {
      const q = url.searchParams;
      const matching = posts.filter(post => (!q.get('keyword') || post.title.includes(q.get('keyword')))
        && (!q.get('categoryId') || String(post.categoryId) === q.get('categoryId'))
        && (!q.get('status') || post.status === q.get('status')));
      const size = Number(q.get('size') || 20);
      const index = Number(q.get('page') || 0);
      return ok(route, { items: matching.slice(index * size, (index + 1) * size),
        totalElements: matching.length, totalPages: Math.ceil(matching.length / size),
        statusCounts: { all: posts.length, draft: posts.length }, page: index, size });
    }
    if (path === '/api/v1/admin/blog/posts/101') {
      const post = posts.find(item => item.id === 101);
      if (method === 'PUT') {
        const body = route.request().postDataJSON();
        const complete = () => {
          if (saveFailure) return route.fulfill({ status: saveFailure, json: {
            success: false, code: 'POST_CONFLICT', message: '다른 곳에서 먼저 수정했습니다.',
          } });
          Object.assign(post, body, { updatedAt: '2026-10-11T10:01:00' });
          return ok(route);
        };
        if (holdSave) { pendingSave = complete; return; }
        return complete();
      }
      return ok(route, post);
    }
    if (path.endsWith('/101/publish') || path.endsWith('/101/unpublish')) {
      posts.find(post => post.id === 101).status = path.endsWith('/unpublish') ? 'DRAFT' : 'PUBLISHED';
      return ok(route);
    }
    return ok(route, null);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function openEditorFromList() {
  await page.goto(listUrl(filters));
  await page.getByRole('row').filter({ hasText: '이동 검증 글 101' }).click();
  await page.waitForURL('**/admin/posts/101?**');
  await page.waitForFunction(() => document.querySelector('input[placeholder="글 제목"]')?.value === '이동 검증 글 101');
}

async function waitForList() {
  await page.waitForURL(url => url.pathname === '/admin/posts');
  await page.waitForFunction(() => !document.querySelector('.admin-grid__loading'));
}

test('manual save returns to page 3 with 50 rows and every applied filter, even after an editor reload', async () => {
  await openEditorFromList();
  assert.deepEqual(query(), filters);
  await page.reload();
  await page.waitForFunction(() => document.querySelector('input[placeholder="글 제목"]')?.value === '이동 검증 글 101');
  await title().fill('이동 검증 글 101 수정');
  await save().click();
  await waitForList();
  await page.getByRole('row').filter({ hasText: '이동 검증 글 101 수정' }).waitFor();
  assert.deepEqual(query(), filters);
  assert.deepEqual(listRequests().at(-1).query, { status: 'DRAFT', categoryId: '1', keyword: '이동', trash: 'false', page: '2', size: '50' });
  assert.equal(await page.getByPlaceholder('제목 검색').inputValue(), '이동');
  assert.equal(await page.getByRole('combobox', { name: '페이지당 표시 개수' }).innerText(), '50개씩');
  assert.equal(await page.locator('[aria-current="page"]').textContent().then(value => value.trim()), '3');
  await page.getByText('저장했습니다.', { exact: true }).waitFor();
});

test('unapplied search input cannot change the return context', async () => {
  await page.goto(listUrl(filters));
  await page.getByPlaceholder('제목 검색').fill('아직 조회하지 않은 검색어');
  await category().getByRole('combobox').click();
  await page.getByRole('option', { name: '일상', exact: true }).click();
  await page.getByRole('row').filter({ hasText: '이동 검증 글 101' }).click();
  await title().waitFor();
  assert.deepEqual(query(), filters);
  await save().click();
  await waitForList();
  assert.deepEqual(query(), filters);
  assert.equal(await page.getByPlaceholder('제목 검색').inputValue(), '이동');
  assert.equal(await category().getByRole('combobox').innerText(), '개발');
});

for (const returnQuery of ['', '?page=-1&size=999&status=WRONG&categoryId=-2&keyword=a&keyword=b']) {
  test(`direct editor entry returns to the default list (${returnQuery || 'no context'})`, async () => {
    await page.goto(`${baseUrl()}admin/posts/101${returnQuery}`);
    await title().waitFor();
    await save().click();
    await waitForList();
    assert.deepEqual(query(), {});
    assert.equal(listRequests().at(-1).query.page, '0');
    assert.equal(listRequests().at(-1).query.size, '20');
  });
}

for (const status of [409, 500]) {
  test(`failed save (${status}) keeps the editor, input and local backup`, async () => {
    await openEditorFromList();
    saveFailure = status;
    await title().fill('실패해도 남아야 하는 제목');
    await save().click();
    await page.locator('.admin-form-error').getByText('다른 곳에서 먼저 수정했습니다.', { exact: true }).waitFor();
    assert.equal(new URL(page.url()).pathname, '/admin/posts/101');
    assert.equal(await title().inputValue(), '실패해도 남아야 하는 제목');
    const draft = await page.evaluate(() => JSON.parse(localStorage.getItem('admin:post-draft:101')));
    assert.equal(draft.form.title, '실패해도 남아야 하는 제목');
  });
}

test('typing while a save is pending keeps unsaved input and a fresh backup; the next save returns', async () => {
  await openEditorFromList();
  holdSave = true;
  await title().fill('이동 저장 요청에 담긴 제목');
  const sent = page.waitForRequest(request => request.method() === 'PUT');
  await save().click(); await sent;
  await title().fill('이동 기다리는 동안 더 쓴 제목');
  await pendingSave();
  await page.locator('.admin-editor__saved').getByText(/저장 중 추가한 내용은 아직 저장되지 않았습니다/).waitFor();
  assert.equal(new URL(page.url()).pathname, '/admin/posts/101');
  assert.equal(await title().inputValue(), '이동 기다리는 동안 더 쓴 제목');
  assert.equal(posts.find(post => post.id === 101).title, '이동 저장 요청에 담긴 제목');
  const draft = await page.evaluate(() => JSON.parse(localStorage.getItem('admin:post-draft:101')));
  assert.equal(draft.form.title, '이동 기다리는 동안 더 쓴 제목');
  assert.equal(draft.baseUpdatedAt, '2026-10-11T10:01:00');
  holdSave = false;
  await save().click();
  await waitForList();
  assert.deepEqual(query(), filters);
  assert.equal(requests.filter(r => r.method === 'PUT').at(-1).body.updatedAt, '2026-10-11T10:01:00');
  assert.equal(await page.evaluate(() => localStorage.getItem('admin:post-draft:101')), null);
});

test('a delayed save cannot pull the user back after they have already left the editor', async () => {
  await openEditorFromList();
  holdSave = true;
  const sent = page.waitForRequest(request => request.method() === 'PUT');
  await save().click(); await sent;
  await page.getByRole('link', { name: '목록', exact: true }).click();
  await waitForList();
  await pendingSave();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  assert.deepEqual(query(), {});
});

test('changing a title out of the filter returns to the last valid page and fetches it', async () => {
  await openEditorFromList();
  await title().fill('검색 조건에서 빠진 글');
  await save().click();
  await page.waitForURL(url => url.pathname === '/admin/posts' && url.searchParams.get('page') === '2');
  await page.getByRole('row').filter({ hasText: '이동 검증 글 100' }).waitFor();
  assert.deepEqual(query(), { ...filters, page: '2' });
  assert.deepEqual(listRequests().slice(-2).map(request => request.query.page), ['2', '1']);
});

test('an empty result returns to page 1 without dropping the filters or looping requests', async () => {
  await openEditorFromList();
  posts = posts.filter(post => post.id === 101);
  await title().fill('검색 조건에서 빠진 마지막 글');
  await save().click();
  await page.waitForURL(url => url.pathname === '/admin/posts' && !url.searchParams.has('page'));
  await page.getByText('조회조건에 맞는 글이 없습니다.', { exact: true }).waitFor();
  const { page: ignored, ...expected } = filters;
  assert.equal(ignored, '3');
  assert.deepEqual(query(), expected);
  assert.deepEqual(listRequests().map(request => request.query.page), ['2', '2', '0']);
});

test('new searches and page size changes reset the page; back and forward restore each result', async () => {
  await page.goto(listUrl(filters));
  await page.getByRole('row').filter({ hasText: '이동 검증 글 101' }).waitFor();
  await page.getByRole('combobox', { name: '페이지당 표시 개수' }).click();
  await page.getByRole('option', { name: '10개씩', exact: true }).click();
  await page.waitForURL(url => url.searchParams.get('size') === '10' && !url.searchParams.has('page'));
  await page.getByRole('button', { name: '다음 페이지', exact: true }).click();
  await page.waitForURL(url => url.searchParams.get('page') === '2');
  await page.getByPlaceholder('제목 검색').fill('이동 검증 글 101');
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await page.waitForURL(url => url.searchParams.get('keyword') === '이동 검증 글 101' && !url.searchParams.has('page'));
  await page.goBack();
  await page.getByRole('row').filter({ hasText: '이동 검증 글 11' }).waitFor();
  assert.equal(await page.getByPlaceholder('제목 검색').inputValue(), '이동');
  assert.equal(query().page, '2');
  await page.goForward();
  await page.getByRole('row').filter({ hasText: '이동 검증 글 101' }).waitFor();
  assert.equal(await page.getByPlaceholder('제목 검색').inputValue(), '이동 검증 글 101');
  assert.equal(query().page, undefined);
});

test('publishing still saves then changes status while keeping the editor open', async () => {
  await openEditorFromList();
  await title().fill('발행할 제목');
  await page.getByRole('button', { name: '발행', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: '확인', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: '내리기', exact: true }).waitFor();
  assert.equal(new URL(page.url()).pathname, '/admin/posts/101');
  assert.deepEqual(requests.filter(r => ['PUT', 'POST'].includes(r.method)).map(r => r.path),
    ['/api/v1/admin/blog/posts/101', '/api/v1/admin/blog/posts/101/publish']);
});

for (const mobile of [false, true]) {
  test(`category shows exactly one all option and can clear a selection (${mobile ? 'mobile' : 'desktop'})`, async () => {
    if (mobile) await page.setViewportSize({ width: 420, height: 900 });
    await page.goto(listUrl());
    if (mobile) {
      await page.getByRole('button', { name: /^펼치기/ }).click();
      const select = category().locator('select');
      await select.locator('option').filter({ hasText: /^일상$/ }).waitFor({ state: 'attached' });
      assert.deepEqual(await select.locator('option').allTextContents().then(labels => labels.map(s => s.trim())), ['전체', '개발', '일상']);
      await select.selectOption('1');
      await select.selectOption('');
    } else {
      const combo = category().getByRole('combobox');
      await combo.click();
      await page.getByRole('option', { name: '일상', exact: true }).waitFor();
      assert.deepEqual(await page.getByRole('option').allTextContents().then(labels => labels.map(s => s.trim())), ['전체', '개발', '일상']);
      await page.getByRole('option', { name: '개발', exact: true }).click();
      await combo.click();
      await page.getByRole('option', { name: '전체', exact: true }).click();
    }
    await page.getByRole('button', { name: '조회', exact: true }).click();
    assert.equal(query().categoryId, undefined);
  });
}
