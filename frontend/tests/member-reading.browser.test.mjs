import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, member, requests, savedPosition, viewedHandler, recentHandler, positionHandler;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const notFound = route => route.fulfill({ status: 404, json: { success: false, code: 'POST_NOT_FOUND', message: '글을 찾을 수 없습니다.' } });
const card = id => ({ id, categoryId: 1, categoryName: '개발', title: `카드 글 ${id}`, thumbnailImageUrl: null,
  publishedAt: '2026-10-15T09:00:00', views: id });
const longContent = Array.from({ length: 40 }, (_, index) => `문단 ${index + 1} 입니다. ${'긴 본문을 이어 씁니다. '.repeat(12)}`)
  .join('\n\n');
const visit = path => page.goto(`${server.resolvedUrls.local[0]}${path.replace(/^\//, '')}`);
const requestsTo = pattern => requests.filter(request => pattern.test(request.path));
const resumeBar = () => page.getByRole('region', { name: '이어서 읽기' });

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });

async function openPage(viewport = { width: 1280, height: 900 }) {
  page = await browser.newPage({ viewport });
  page.setDefaultTimeout(8000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    requests.push({ path, method: request.method(), query: Object.fromEntries(url.searchParams),
      body: request.postData() ? request.postDataJSON() : null });
    if (path === '/api/v1/auth/me') return ok(route, member);
    if (path === '/api/v1/auth/csrf') return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path === '/api/v1/auth/me/viewed-posts') return viewedHandler(route, url);
    if (path === '/api/v1/auth/me/recent-posts') return recentHandler(route, url);
    if (path === '/api/v1/auth/me/comments') return ok(route, []);
    const position = /^\/api\/v1\/auth\/me\/reading-positions\/(\d+)$/.exec(path);
    if (position) {
      if (request.method() === 'PUT') { savedPosition = request.postDataJSON(); return ok(route); }
      return positionHandler(route, Number(position[1]));
    }
    if (path === '/api/v1/blog/categories') return ok(route, [{ id: 1, name: '개발', sortOrder: 1 }]);
    if (path === '/api/v1/blog/posts') {
      return ok(route, { items: [card(1), card(2), card(3)], page: 0, size: 12, totalElements: 3, totalPages: 1 });
    }
    if (path === '/api/v1/blog/posts/404') return notFound(route);
    if (/^\/api\/v1\/blog\/posts\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop());
      return ok(route, { id, categoryId: 1, categoryName: '개발', title: `긴 글 ${id}`, excerpt: '요약',
        content: longContent, publishedAt: '2026-10-15T09:00:00', views: 3 });
    }
    return ok(route);
  });
}

beforeEach(async () => {
  errors = []; requests = []; savedPosition = null;
  member = { id: 7, nickname: '읽는너구리', role: 'USER' };
  viewedHandler = (route, url) => ok(route, url.searchParams.get('postIds').split(',').map(Number)
    .filter(id => id === 2).map(postId => ({ postId, lastViewedAt: '2026-10-17T12:30:00Z' })));
  recentHandler = (route, url) => {
    const page = Number(url.searchParams.get('page'));
    const items = page === 0
      ? [{ postId: 5, title: '첫 쪽 글', categoryName: '개발', thumbnailImageUrl: null, lastViewedAt: '2026-10-17T12:30:00Z' }]
      : [{ postId: 4, title: '둘째 쪽 글', categoryName: '일상', thumbnailImageUrl: null, lastViewedAt: '2026-10-16T01:00:00Z' }];
    return ok(route, { items, page, size: 10, totalElements: 2, totalPages: 2 });
  };
  positionHandler = route => ok(route, null);
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('로그인 회원의 글 카드는 한 번의 요청으로 본 적 있는 글에만 "최근 본 글" 을 붙인다', async () => {
  await openPage();
  await visit('/posts');
  await page.locator('.post-card__viewed').waitFor();

  const viewedRequests = requestsTo(/\/viewed-posts$/);
  assert.equal(viewedRequests.length, 1, '카드마다 따로 묻지 않는다');
  assert.deepEqual(viewedRequests[0].query.postIds.split(',').map(Number).sort(), [1, 2, 3]);
  assert.equal(await page.locator('.post-card__viewed').count(), 1);
  const badgeCard = page.locator('.post-card').filter({ has: page.locator('.post-card__viewed') });
  assert.match(await badgeCard.innerText(), /카드 글 2/);
  assert.match(await page.locator('.post-card__viewed').getAttribute('title'), /2026\.10\.17 21:30에 봤습니다/);
});

test('비회원에게는 표시도 요청도 없고, 로그아웃하면 표시가 사라진다', async () => {
  member = null;
  await openPage();
  await visit('/posts');
  await page.getByText('카드 글 3').waitFor();
  await page.waitForTimeout(200);
  assert.equal(requestsTo(/\/viewed-posts$/).length, 0);
  assert.equal(await page.locator('.post-card__viewed').count(), 0);
  await page.close();

  member = { id: 7, nickname: '읽는너구리', role: 'USER' };
  await openPage();
  await visit('/posts');
  await page.locator('.post-card__viewed').waitFor();
  await page.evaluate(async () => {
    const auth = await import('/src/features/member/data/memberAuthStore.js');
    auth.clearMemberSession();
  });
  await page.locator('.post-card__viewed').waitFor({ state: 'detached' });
});

test('읽던 자리가 있으면 저절로 옮기지 않고 단추를 띄우며, 누르면 그 덩어리로 간다', async () => {
  await openPage();
  await visit('/posts/9');
  await page.locator('[data-reading-root] > *').nth(20).waitFor();
  const hash = await page.evaluate(async () => {
    const { fingerprint } = await import('/src/features/post/composables/useReadingPosition.js');
    return fingerprint(document.querySelector('[data-reading-root]').children[20]);
  });

  positionHandler = route => ok(route, { postId: 9, blockIndex: 20, blockOffset: 500, blockHash: hash,
    savedAt: '2026-10-16T12:00:00Z' });
  await visit('/posts/9');
  await resumeBar().waitFor();
  assert.match(await resumeBar().innerText(), /2026\.10\.16 21:00/);
  assert.equal(await page.evaluate(() => window.scrollY), 0, '단추를 누르기 전에는 움직이지 않는다');

  await resumeBar().getByRole('button', { name: '이어서 읽기', exact: true }).click();
  await resumeBar().waitFor({ state: 'detached' });
  await page.waitForFunction(() => {
    const block = document.querySelector('[data-reading-root]').children[20];
    const rect = block.getBoundingClientRect();
    return Math.abs(rect.top + rect.height / 2 - 88) < 40;
  });
  assert.equal(await page.evaluate(() => document.activeElement === document.querySelector('[data-reading-root]').children[20]), true);
});

test('글이 고쳐져 그 덩어리가 없으면 단추를 띄우지 않는다', async () => {
  positionHandler = route => ok(route, { postId: 9, blockIndex: 20, blockOffset: 500, blockHash: 'deadbeef',
    savedAt: '2026-10-16T12:00:00Z' });
  await openPage();
  await visit('/posts/9');
  await page.locator('[data-reading-root] > *').nth(20).waitFor();
  await page.waitForFunction(() => performance.getEntriesByType('resource')
    .some(entry => entry.name.includes('/reading-positions/9')));
  await page.waitForTimeout(200);
  assert.equal(await resumeBar().count(), 0);
});

test('스크롤하지 않고 떠나면 저장하지 않고, 읽다가 떠나면 그 자리를 저장한다', async () => {
  await openPage();
  await visit('/posts/9');
  await page.locator('[data-reading-root] > *').nth(20).waitFor();
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));
  await page.waitForTimeout(200);
  assert.equal(savedPosition, null, '열어 보기만 한 글이 예전 자리를 덮어쓰지 않는다');

  await page.locator('[data-reading-root] > *').nth(15).scrollIntoViewIfNeeded();
  await page.evaluate(() => window.scrollBy(0, 300));
  await page.waitForFunction(() => window.scrollY > 0);
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));
  await page.waitForFunction(() => true);
  await page.waitForTimeout(300);
  assert.ok(savedPosition, '자리를 보냈어야 한다');
  assert.ok(savedPosition.blockIndex >= 10, `본문 중간의 덩어리여야 한다: ${savedPosition.blockIndex}`);
  assert.match(savedPosition.blockHash, /^[0-9a-f]{8}$/);
  assert.ok(savedPosition.blockOffset >= 0 && savedPosition.blockOffset <= 1000);
});

test('비회원과 없는 글은 읽던 자리를 묻지 않는다', async () => {
  member = null;
  await openPage();
  await visit('/posts/9');
  await page.locator('[data-reading-root] > *').nth(5).waitFor();
  await page.waitForTimeout(200);
  assert.equal(requestsTo(/\/reading-positions\//).length, 0);
  await page.close();

  member = { id: 7, nickname: '읽는너구리', role: 'USER' };
  await openPage();
  await visit('/posts/404');
  await page.getByText('찾는 글이 없습니다').first().waitFor().catch(() => null);
  await page.waitForTimeout(200);
  assert.equal(requestsTo(/\/reading-positions\//).length, 0);
});

test('휴대폰 폭에서도 단추가 화면 안에 있고 누르기 넉넉하며 가로 스크롤이 생기지 않는다', async () => {
  await openPage({ width: 375, height: 812 });
  await visit('/posts/9');
  await page.locator('[data-reading-root] > *').nth(20).waitFor();
  const hash = await page.evaluate(async () => {
    const { fingerprint } = await import('/src/features/post/composables/useReadingPosition.js');
    return fingerprint(document.querySelector('[data-reading-root]').children[12]);
  });
  positionHandler = route => ok(route, { postId: 9, blockIndex: 12, blockOffset: 0, blockHash: hash,
    savedAt: '2026-10-16T12:00:00Z' });
  await visit('/posts/9');
  await resumeBar().waitFor();

  const box = await resumeBar().boundingBox();
  assert.ok(box.x >= 0 && box.x + box.width <= 375, `화면 안이어야 한다: ${JSON.stringify(box)}`);
  for (const name of ['이어서 읽기', '이어서 읽기 닫기']) {
    const button = await resumeBar().getByRole('button', { name, exact: true }).boundingBox();
    assert.ok(button.height >= 42, `${name} 단추 높이: ${button.height}`);
  }
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));

  await resumeBar().getByRole('button', { name: '이어서 읽기 닫기' }).click();
  await resumeBar().waitFor({ state: 'detached' });
});

test('내 설정의 최근 본 글은 마지막으로 본 시각과 함께 쪽을 이어 붙이고, 메뉴에서 바로 간다', async () => {
  await openPage({ width: 375, height: 812 });
  await visit('/posts');
  await page.locator('.member-menu__trigger').click();
  await page.getByRole('menuitem', { name: '최근 본 글' }).click();
  await page.waitForURL('**/settings#recent-posts');

  const section = page.locator('#recent-posts');
  await section.getByText('첫 쪽 글').waitFor();
  assert.match(await section.innerText(), /2026\.10\.17 21:30에 마지막으로 봄/);
  await section.getByRole('button', { name: '더 보기' }).click();
  await section.getByText('둘째 쪽 글').waitFor();
  assert.equal(await section.getByRole('button', { name: '더 보기' }).count(), 0);
  assert.deepEqual(requestsTo(/\/recent-posts$/).map(request => request.query.page), ['0', '1']);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
});

test('최근 본 글이 없으면 빈 상태를, 실패하면 다시 시도를 보여 준다', async () => {
  let fail = true;
  recentHandler = route => {
    if (fail) {
      fail = false;
      return route.fulfill({ status: 500, json: { success: false, message: '일시적인 오류입니다.' } });
    }
    return ok(route, { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  };
  await openPage();
  await visit('/settings');
  const section = page.locator('#recent-posts');
  await section.getByText('최근 본 글을 불러오지 못했습니다.').waitFor();
  await section.getByRole('button', { name: '다시 시도' }).click();
  await section.getByText('아직 본 글이 없습니다.').waitFor();
});
