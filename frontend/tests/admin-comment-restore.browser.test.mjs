import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const comment = (id, fields = {}) => ({ id, postId: 1, postTitle: '테스트 글', parentId: null, nickname: `회원${id}`,
  memberRole: 'USER', content: `댓글 ${id}`, createdAt: '2026-10-18T10:00:00', hidden: false, answered: false,
  reportCount: 0, unhandledReportCount: 0, contentPurged: false, postPurged: false, deletedByWriter: false, ...fields });
const row = text => page.locator('.admin-comment-row').filter({ hasText: text });

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
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(8000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'admin', role: 'ADMIN' });
    if (path === '/api/v1/auth/me') return ok(route, { id: 1, nickname: '운영자', role: 'ROLE_ADMIN' });
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/notifications/unread-count')) return ok(route, 0);
    if (path === '/api/v1/admin/blog/comments') {
      const items = [
        comment(1, { content: '보이는 댓글' }),
        comment(2, { content: '운영자가 가린 댓글', hidden: true }),
        comment(3, { content: '글쓴이가 지운 댓글', hidden: true, deletedByWriter: true }),
        comment(4, { content: '', hidden: true, deletedByWriter: true, contentPurged: true }),
      ];
      return ok(route, { items, statusCounts: { all: 4, unanswered: 0, hidden: 3, reported: 0 },
        page: 0, size: 20, totalElements: 4, totalPages: 1 });
    }
    return ok(route, null);
  });
});
afterEach(async (context) => {
  if (page && process.env.QA_SCREENSHOT_DIR) {
    await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/admin-comment-${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 80)}.png`, fullPage: true });
  }
  await page?.close(); assert.deepEqual(errors, []);
});

test('only comments hidden by the operator offer a restore button', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/comments`);
  await row('보이는 댓글').waitFor();

  assert.equal(await row('보이는 댓글').getByRole('button', { name: '숨김' }).count(), 1);
  assert.equal(await row('운영자가 가린 댓글').getByRole('button', { name: '복구' }).count(), 1);

  // 글쓴이가 지운 댓글에는 되돌리기 단추가 없고 상태로 까닭을 말한다
  const deleted = row('글쓴이가 지운 댓글');
  assert.equal(await deleted.getByRole('button', { name: '복구' }).count(), 0);
  assert.match(await deleted.textContent(), /작성자 삭제/);

  const purged = page.locator('.admin-comment-row').filter({ hasText: '보관 기간이 지나 원문을 파기했습니다.' });
  assert.equal(await purged.getByRole('button', { name: '복구' }).count(), 0);
});
