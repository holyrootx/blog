import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

/**
 * 관리자 화면 틀. 긴 글을 쓰면서 본문이 길어져도 왼쪽 메뉴는 화면에 붙어 있어야 한다.
 */
let server;
let browser;
let page;
let errors;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const longContent = Array.from({ length: 120 }, (_, index) => `${index + 1}번째 문단입니다.`).join('\n\n');
const menus = [
  { menuName: '대시보드', menuType: 'LINK', routePath: '/admin', routeName: 'admin-dashboard' },
  {
    menuName: '글', menuType: 'GROUP', items: [
      { menuName: '글 목록', menuType: 'LINK', routePath: '/admin/posts', routeName: 'admin-posts' },
    ],
  },
];

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
  errors = [];
  page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'test', role: 'ADMIN' });
    if (path === '/api/v1/auth/me') return ok(route, null);
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path.endsWith('/sidebar/menus')) return ok(route, menus);
    if (path.endsWith('/categories')) return ok(route, [{ id: 1, name: '분류', sortOrder: 1 }]);
    if (path === '/api/v1/admin/blog/posts/9001') return ok(route, {
      id: 9001, title: '긴 글', categoryId: 1, excerpt: '', content: longContent,
      thumbnailImageUrl: '', status: 'DRAFT', updatedAt: '2026-10-10T00:00:00', publishedAt: null,
    });
    return ok(route);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

const sidebarBox = () => page.evaluate(() => {
  const rect = document.querySelector('.admin-sidebar').getBoundingClientRect();
  return { top: rect.top, bottom: rect.bottom, viewport: window.innerHeight, scrolled: window.scrollY };
});

test('긴 글을 끝까지 내려도 왼쪽 메뉴와 로그아웃 단추가 화면에 남는다', async () => {
  await page.goto(`${server.resolvedUrls.local[0]}admin/posts/9001`);
  await page.getByText('120번째 문단입니다.').waitFor();
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));

  const box = await sidebarBox();
  assert.ok(box.scrolled > 2000, `본문이 충분히 길어야 한다: ${box.scrolled}`);
  assert.equal(Math.round(box.top), 0);
  assert.equal(Math.round(box.bottom), box.viewport);
  assert.equal(await page.locator('.admin-sidebar__footer').isVisible(), true);
  const footer = await page.locator('.admin-sidebar__footer').boundingBox();
  assert.ok(footer.y + footer.height <= box.viewport, '로그아웃 단추가 화면 안에 있다');

  if (process.env.SHOT_DIR) {
    await page.screenshot({ path: `${process.env.SHOT_DIR}/admin-sidebar-sticky.png` });
  }
});

test('좁은 화면에서는 메뉴가 위에 쌓이므로 고정하지 않고 같이 올라간다', async () => {
  await page.setViewportSize({ width: 900, height: 900 });
  await page.goto(`${server.resolvedUrls.local[0]}admin/posts/9001`);
  await page.getByText('120번째 문단입니다.').waitFor();
  assert.equal(await page.locator('.admin-sidebar').evaluate(node => getComputedStyle(node).position), 'static');
});
