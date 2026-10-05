import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, rows, pending, requests, errors;
const expiredTitle = '복구 기간이 지난 글';
const expiredAt = '2026-08-01T12:34:56.123456';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const dialog = () => page.getByRole('dialog', { name: '이 글을 영구 삭제할까요?' });
const titleInput = () => dialog().getByLabel('삭제할 글 제목을 그대로 입력해 주세요');
const confirm = () => dialog().getByRole('button', { name: '영구 삭제', exact: true });

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  rows = [
    { id: 1, title: '아직 복구 가능한 글', deletedAt: '2026-10-04T12:00:00', restoreUntil: '2026-11-03T12:00:00', restorable: true, purgeable: false },
    { id: 2, title: expiredTitle, deletedAt: expiredAt, restoreUntil: '2026-08-31T12:34:56.123456', restorable: false, purgeable: true },
  ];
  requests = []; pending = null; errors = [];
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/permanent')) { requests.push(route.request().postDataJSON()); pending = route; return; }
    if (url.pathname === '/api/v1/admin/auth/me') return ok(route, { username: 'test', role: 'ADMIN' });
    if (url.pathname.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (url.pathname.endsWith('/categories')) return ok(route, []);
    if (url.pathname === '/api/v1/admin/blog/posts') {
      const items = url.searchParams.get('trash') === 'true' ? rows : [];
      return ok(route, { items, totalElements: items.length, totalPages: items.length ? 1 : 0, statusCounts: { all: 0, trash: rows.length } });
    }
    return ok(route, null);
  });
  await page.goto(`${server.resolvedUrls.local[0]}admin/posts`);
  await page.getByRole('button', { name: '휴지통 2', exact: true }).click();
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await page.getByRole('row').filter({ hasText: expiredTitle }).waitFor();
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function openConfirmation() {
  await page.getByRole('row').filter({ hasText: expiredTitle }).getByRole('button', { name: '영구 삭제', exact: true }).click();
  await dialog().waitFor();
}

test('restore window and exact title prevent accidental permanent deletion; cancel resets input', async () => {
  const recent = page.getByRole('row').filter({ hasText: '아직 복구 가능한 글' });
  assert.equal(await recent.getByRole('button', { name: '영구 삭제', exact: true }).isDisabled(), true);
  assert.equal(await recent.getByRole('button', { name: '복구', exact: true }).isEnabled(), true);
  await openConfirmation();
  assert.equal(await confirm().isDisabled(), true);
  await titleInput().fill('다른 글');
  assert.equal(await confirm().isDisabled(), true);
  await titleInput().fill(expiredTitle);
  assert.equal(await confirm().isEnabled(), true);
  await dialog().getByRole('button', { name: '취소', exact: true }).click();
  await openConfirmation();
  assert.equal(await titleInput().inputValue(), '');
  assert.deepEqual(requests, []);
});

test('one pending request keeps confirmation open and sends the original deletion timestamp', async () => {
  await openConfirmation();
  await titleInput().fill(expiredTitle);
  const sent = page.waitForRequest('**/posts/2/permanent');
  await confirm().click();
  await sent;
  await page.keyboard.press('Escape');
  assert.equal(await dialog().isVisible(), true);
  assert.equal(await titleInput().isDisabled(), true);
  assert.equal(await dialog().getByRole('button', { name: '처리 중…', exact: true }).isDisabled(), true);
  assert.deepEqual(requests, [{ title: expiredTitle, deletedAt: expiredAt }]);
  rows = rows.filter(row => row.id !== 2);
  await ok(pending);
  await dialog().waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: '휴지통 1', exact: true }).waitFor();
  assert.equal(await page.getByRole('row').filter({ hasText: expiredTitle }).count(), 0);
});

test('a stale confirmation error preserves the dialog and never removes the row', async () => {
  await openConfirmation();
  await titleInput().fill(expiredTitle);
  const sent = page.waitForRequest('**/posts/2/permanent');
  await confirm().click();
  await sent;
  await pending.fulfill({ status: 409, json: { success: false, code: 'POST_PURGE_CONFIRMATION_MISMATCH', message: '확인한 글 정보가 일치하지 않습니다.' } });
  await dialog().getByText('확인한 글 정보가 일치하지 않습니다.', { exact: true }).waitFor();
  assert.equal(await confirm().isEnabled(), true);
  assert.equal(await page.getByRole('row').filter({ hasText: expiredTitle }).count(), 1);
});

test('retained comment detail identifies a permanently deleted post and disables publishing actions', async () => {
  const comment = { id: 7, postId: 2, postTitle: '영구 삭제된 글', postPurged: true,
    parentId: null, nickname: '회원', content: '신고 확인용 댓글', deleted: false, hidden: false,
    contentRetainedUntil: '2027-04-05T12:00:00', reports: [], moderations: [], histories: [] };
  await page.route('**/api/v1/admin/blog/comments**', route => {
    if (new URL(route.request().url()).pathname.endsWith('/7')) return ok(route, comment);
    return ok(route, { items: [comment], totalElements: 1, totalPages: 1 });
  });
  await page.goto(`${server.resolvedUrls.local[0]}admin/comments`);
  const row = page.locator('.admin-comment-row').filter({ hasText: comment.content });
  await row.waitFor();
  await row.getByText('보관 중', { exact: true }).waitFor();
  assert.equal(await row.getByText('미답변', { exact: true }).count(), 0);
  assert.equal(await row.getByRole('button', { name: '숨김', exact: true }).isDisabled(), true);
  assert.equal(await row.getByRole('button', { name: '답글', exact: true }).count(), 0);
  await row.click();
  const detail = page.getByRole('dialog', { name: '댓글 상세' });
  await detail.getByText(/원글이 영구 삭제되어 댓글을 공개하거나 복구할 수 없습니다/).waitFor();
  assert.equal(await detail.getByRole('link').count(), 0);
  assert.equal(await detail.getByRole('button', { name: '가리기', exact: true }).count(), 0);
});
