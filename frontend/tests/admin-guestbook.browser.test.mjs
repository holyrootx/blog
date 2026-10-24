import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, requests, rows, detail, notices;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const row = (id, fields = {}) => ({ id, memberId: 10 + id, nickname: `손님${id}`, writtenByAdmin: false,
  content: `방명록 인사 ${id}`, secret: false, edited: false, createdAt: '2026-10-17T21:30:00', deleted: false,
  hiddenByAdmin: false, contentPurged: false, replyContent: null, repliedAt: null, reportCount: 0,
  unhandledReportCount: 0, ...fields });
const lastRequest = (method, pattern) => requests.filter(item => item.method === method && pattern.test(item.path)).at(-1);
const dialog = () => page.getByRole('dialog', { name: '방명록 글 상세' });

before(async () => {
  server = await createServer({ root: process.env.TEST_APP_ROOT || fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = []; requests = [];
  rows = [
    row(3, { secret: true, content: '운영자님께만 드리는 말' }),
    row(2, { reportCount: 2, unhandledReportCount: 2, content: '광고입니다' }),
    row(1, { replyContent: '고마워요', repliedAt: '2026-10-17T22:00:00' }),
  ];
  detail = id => ({
    entry: rows.find(item => item.id === id) ?? row(id),
    deletedAt: null,
    retainedUntil: null,
    reports: id === 2 ? [
      { id: 1, reason: 'SPAM', reasonLabel: '광고·도배', detail: null, reportedAt: '2026-10-17T22:10:00', handled: false,
        reportedContent: '광고입니다 (신고 당시)' },
      { id: 2, reason: 'OTHER', reasonLabel: '기타', detail: '같은 글 반복', reportedAt: '2026-10-17T22:20:00', handled: false,
        reportedContent: '광고입니다 (신고 당시)' },
    ] : [],
  });
  notices = [{ id: 1, type: 'GUESTBOOK_WRITTEN', actorNickname: '손님3', postId: null, postTitle: null, commentId: null,
    guestbookEntryId: 3, preview: '운영자님께만 드리는 말', createdAt: '2026-10-17T21:30:00', unread: true }];
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(8000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const url = new URL(request.url()), path = url.pathname, method = request.method();
    requests.push({ method, path, query: Object.fromEntries(url.searchParams), body: request.postDataJSON?.() ?? null });
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'admin', role: 'ADMIN' });
    if (path === '/api/v1/auth/me') return ok(route, { id: 1, nickname: '운영자', role: 'ROLE_ADMIN' });
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/notifications/unread-count')) return ok(route, notices.filter(item => item.unread).length);
    if (path.endsWith('/notifications')) return ok(route, notices);
    if (path === '/api/v1/admin/blog/guestbook') {
      const status = url.searchParams.get('status');
      const statusRows = {
        REPORTED: rows.filter(item => item.unhandledReportCount > 0),
        HIDDEN: rows.filter(item => item.hiddenByAdmin && !item.deletedByWriter),
        DELETED: rows.filter(item => item.deletedByWriter),
      };
      const items = statusRows[status] ?? rows;
      return ok(route, { items, statusCounts: { all: rows.length, reported: statusRows.REPORTED.length,
        hidden: statusRows.HIDDEN.length, deleted: statusRows.DELETED.length },
        page: 0, size: 20, totalElements: items.length, totalPages: 1 });
    }
    const one = /^\/api\/v1\/admin\/blog\/guestbook\/(\d+)$/.exec(path);
    if (one) return ok(route, detail(Number(one[1])));
    return ok(route, null);
  });
});
afterEach(async (context) => {
  if (page && process.env.QA_SCREENSHOT_DIR) {
    await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/admin-guestbook-${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 80)}.png`, fullPage: true });
  }
  await page?.close(); assert.deepEqual(errors, []);
});

test('the list shows secret entries, replies and pending reports without an unanswered count', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/guestbook`);
  await page.getByRole('row').filter({ hasText: '운영자님께만 드리는 말' }).waitFor();

  assert.match(await page.getByRole('row').filter({ hasText: '손님3' }).textContent(), /비밀글/);
  assert.match(await page.getByRole('row').filter({ hasText: '손님1' }).textContent(), /답글 있음/);
  assert.match(await page.getByRole('row').filter({ hasText: '손님2' }).textContent(), /2건 대기/);
  // 답할 의무가 없는 자리라 "미답변" 을 세지 않는다
  assert.doesNotMatch(await page.locator('.admin-guestbook-page').textContent(), /미답변|답글 없음/);

  await page.getByRole('button', { name: /^신고 1$/ }).click();
  await page.getByRole('button', { name: '조회' }).click();
  await page.getByRole('row').filter({ hasText: '손님3' }).waitFor({ state: 'detached' });
  await page.getByRole('row').filter({ hasText: '손님2' }).waitFor();
  assert.equal(lastRequest('GET', /\/admin\/blog\/guestbook$/).query.status, 'REPORTED');
});

test('reports are read in the detail and can be dismissed or hidden', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/guestbook`);
  await page.getByRole('row').filter({ hasText: '광고입니다' }).click();
  await dialog().getByText('신고 2건').waitFor();
  assert.match(await dialog().textContent(), /신고 당시 본문:\s*광고입니다 \(신고 당시\)/);
  assert.doesNotMatch(await dialog().textContent(), /신고자|memberId/);

  await dialog().getByRole('button', { name: '문제 없음' }).click();
  await page.getByText('신고를 문제 없음으로 처리했습니다.').waitFor();
  assert.ok(lastRequest('POST', /\/guestbook\/2\/reports\/dismiss$/));

  await dialog().getByRole('button', { name: '가리기' }).click();
  await page.getByText('방명록 글을 가렸습니다.').waitFor();
  assert.deepEqual(lastRequest('PUT', /\/guestbook\/2\/visibility$/).body, { hidden: true });
});

test('the operator replies from the detail', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/guestbook`);
  await page.getByRole('row').filter({ hasText: '운영자님께만 드리는 말' }).click();
  await dialog().getByText('비밀글이라 답글도 쓴 사람과 운영자만 봅니다').waitFor();
  await dialog().getByLabel(/답글 달기/).fill('  잘 받았습니다  ');
  await dialog().getByRole('button', { name: '답글 저장' }).click();
  await page.getByText('답글을 저장했습니다.').waitFor();
  assert.deepEqual(lastRequest('PUT', /\/guestbook\/3\/reply$/).body, { content: '잘 받았습니다' });
});

test('a guestbook notification opens the entry in the admin screen', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}privacy`);
  await page.locator('.notification-bell__trigger').click();
  const item = page.locator('button.notification-bell__item').first();
  await item.waitFor();
  assert.match(await item.textContent(), /손님3 님이 방명록을 남겼습니다/);
  assert.match(await item.textContent(), /방명록/);
  await item.click();

  await page.waitForURL('**/admin/guestbook');
  await dialog().getByText('운영자님께만 드리는 말').waitFor();
});

test('restore is offered only for entries the operator hid', async () => {
  rows = [
    row(9, { content: '운영자가 가린 글', deleted: true, hiddenByAdmin: true }),
    row(8, { content: '쓴 사람이 지운 글', deleted: true, deletedByWriter: true }),
    row(7, { content: '지운 뒤 가린 글', deleted: true, hiddenByAdmin: true, deletedByWriter: true }),
  ];
  const retention = { 8: '2027-04-18T10:00:00', 7: '2027-04-18T10:00:00' };
  const baseDetail = detail;
  detail = id => ({ ...baseDetail(id), deletedAt: retention[id] ? '2026-10-18T10:00:00' : null, retainedUntil: retention[id] ?? null });
  await page.goto(`${server.resolvedUrls.local[0]}admin/guestbook`);
  await page.getByRole('button', { name: /^전체 3$/ }).waitFor();
  await page.getByRole('button', { name: /^가림 1$/ }).waitFor();
  await page.getByRole('button', { name: /^지움 2$/ }).waitFor();

  await page.getByRole('row').filter({ hasText: '운영자가 가린 글' }).click();
  await dialog().getByRole('button', { name: '다시 공개' }).waitFor();
  assert.match(await page.getByRole('row').filter({ hasText: '운영자가 가린 글' }).textContent(), /가림/);
  assert.deepEqual(await dialog().locator('.admin-guestbook-detail__meta .admin-badge').allTextContents(), ['가림']);
  await dialog().getByRole('button', { name: '닫기', exact: true }).last().click();

  await page.getByRole('button', { name: /^지움 2$/ }).click();
  await page.getByRole('button', { name: '조회' }).click();
  await page.getByRole('row').filter({ hasText: '운영자가 가린 글' }).waitFor({ state: 'detached' });
  assert.equal(lastRequest('GET', /\/admin\/blog\/guestbook$/).query.status, 'DELETED');

  for (const text of ['지운 뒤 가린 글', '쓴 사람이 지운 글']) {
    assert.match(await page.getByRole('row').filter({ hasText: text }).textContent(), /지움/);
    await page.getByRole('row').filter({ hasText: text }).click();
    await dialog().getByText('되살릴 수 없습니다', { exact: false }).waitFor();
    assert.deepEqual(await dialog().locator('.admin-guestbook-detail__meta .admin-badge').allTextContents(), ['지움']);
    assert.equal(await dialog().getByRole('button', { name: '다시 공개' }).count(), 0);
    assert.equal(await dialog().getByRole('button', { name: '가리기' }).count(), 0);
    await dialog().getByRole('button', { name: '닫기', exact: true }).last().click();
    await dialog().waitFor({ state: 'detached' });
  }
});
