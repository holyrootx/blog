import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, member, entries, requests, listHandler, notices;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = (route, status, code, message) => route.fulfill({ status, json: { success: false, code, message } });
const entry = (id, fields = {}) => ({ id, nickname: `손님${id}`, writtenByAdmin: false, content: `방명록 인사 ${id}`,
  secret: false, readable: true, mine: false, edited: false, hiddenByAdmin: false, reportedByMe: false,
  createdAt: '2026-10-17T21:30:00', reply: null, ...fields });
const pageOf = (items, { page = 0, totalElements = items.length, totalPages = 1 } = {}) => ({
  items, page, size: 10, totalElements, totalPages, maxLength: 500 });
const visit = path => page.goto(`${server.resolvedUrls.local[0]}${path}`);
const entryItem = id => page.locator(`#guestbook-${id}`);
const lastRequest = (method, pattern) => requests.filter(item => item.method === method && pattern.test(item.path)).at(-1);

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
  member = { id: 7, nickname: '테스터', role: 'ROLE_USER' };
  entries = [
    entry(3, { nickname: '테스터', mine: true }),
    entry(2, { secret: true, readable: false, content: null }),
    entry(1, { reply: { nickname: '운영자', content: '와 주셔서 고마워요', repliedAt: '2026-10-17T22:00:00' } }),
  ];
  listHandler = route => ok(route, pageOf(entries));
  notices = [];
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(8000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url()), path = url.pathname, method = request.method();
    requests.push({ method, path, query: Object.fromEntries(url.searchParams), body: request.postDataJSON?.() ?? null });
    if (path === '/api/v1/auth/me') return ok(route, member);
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'admin', role: 'ADMIN' });
    if (path.endsWith('/notifications/unread-count')) return ok(route, notices.filter(item => item.unread).length);
    if (path.endsWith('/notifications')) return ok(route, notices);
    if (path === '/api/v1/blog/guestbook' && method === 'GET') return listHandler(route, url);
    return ok(route, null);
  });
});
afterEach(async (context) => {
  if (page && process.env.QA_SCREENSHOT_DIR) {
    await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/guestbook-${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 90)}.png`, fullPage: true });
  }
  await page?.close(); assert.deepEqual(errors, []);
});

test('visitors read entries, secret entries stay masked and writing asks for sign-in', async () => {
  member = null;
  entries = entries.map(item => ({ ...item, mine: false }));
  await visit('guestbook');
  await entryItem(1).waitFor();

  assert.match(await entryItem(1).textContent(), /방명록 인사 1/);
  assert.match(await entryItem(1).locator('.reply').textContent(), /운영자.*와 주셔서 고마워요/s);
  assert.match(await entryItem(2).textContent(), /비밀글입니다\. 쓴 사람과 운영자만 볼 수 있습니다\./);
  await page.getByRole('button', { name: '로그인하고 남기기' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '신고' }).count(), 0);
  assert.equal(await page.locator('.comment-form').count(), 0);
});

test('a member leaves a secret entry and the first page is read again', async () => {
  await visit('guestbook');
  await page.locator('.comment-form__input').fill('  비밀 인사입니다  ');
  await page.getByLabel('비밀글').check();
  listHandler = route => ok(route, pageOf([entry(4, { nickname: '테스터', mine: true, secret: true, content: '비밀 인사입니다' }), ...entries]));
  await page.getByRole('button', { name: '등록' }).click();

  await entryItem(4).waitFor();
  const posted = lastRequest('POST', /^\/api\/v1\/blog\/guestbook$/);
  assert.deepEqual(posted.body, { content: '비밀 인사입니다', secret: true });
  assert.equal(await page.locator('.comment-form__input').inputValue(), '');
  assert.equal(await page.getByLabel('비밀글').isChecked(), false);
  assert.match(await entryItem(4).textContent(), /비밀글/);
});

test('rate limited writes keep the draft and show the server reason', async () => {
  await page.route('**/api/v1/blog/guestbook', route => route.request().method() === 'POST'
    ? fail(route, 429, 'GUESTBOOK_RATE_LIMITED', '방명록을 너무 빠르게 남기고 있습니다. 잠시 후 다시 시도해 주세요.')
    : route.fallback());
  await visit('guestbook');
  await page.locator('.comment-form__input').fill('또 왔어요');
  await page.getByRole('button', { name: '등록' }).click();

  await page.getByRole('alert').filter({ hasText: '너무 빠르게' }).waitFor();
  assert.equal(await page.locator('.comment-form__input').inputValue(), '또 왔어요');
});

test('the writer edits and deletes only their own entry', async () => {
  await visit('guestbook');
  await entryItem(3).waitFor();
  assert.equal(await entryItem(1).getByRole('button', { name: '수정' }).count(), 0);

  await entryItem(3).getByRole('button', { name: '수정' }).click();
  await entryItem(3).getByLabel('방명록 고치기').fill('고친 인사');
  await entryItem(3).getByRole('button', { name: '저장' }).click();
  await entryItem(3).getByText('수정됨').waitFor();
  assert.deepEqual(lastRequest('PUT', /\/guestbook\/3$/).body, { content: '고친 인사', secret: false });

  listHandler = route => ok(route, pageOf(entries.filter(item => item.id !== 3)));
  await entryItem(3).getByRole('button', { name: '삭제' }).click();
  await entryItem(3).getByRole('button', { name: '지우기' }).click();
  await entryItem(3).waitFor({ state: 'detached' });
  assert.ok(lastRequest('DELETE', /\/guestbook\/3$/));
});

test('reporting uses the shared dialog with the guestbook wording', async () => {
  await visit('guestbook');
  await entryItem(1).getByRole('button', { name: '신고' }).click();
  const dialog = page.getByRole('dialog', { name: '방명록 글 신고' });
  await dialog.waitFor();
  assert.match(await dialog.textContent(), /손님1 님의 방명록 글/);
  await dialog.getByLabel('광고·도배').check();
  await dialog.getByRole('button', { name: '신고', exact: true }).click();

  await entryItem(1).getByRole('button', { name: '이미 신고한 방명록 글' }).waitFor();
  assert.deepEqual(lastRequest('POST', /\/guestbook\/1\/reports$/).body, { reason: 'SPAM', detail: null });
  // 읽을 수 없는 비밀글에는 신고 단추가 없다
  assert.equal(await entryItem(2).getByRole('button').count(), 0);
});

test('an entry hidden by the operator stays visible only as a notice without actions', async () => {
  entries = [entry(5, { nickname: '테스터', mine: true, hiddenByAdmin: true })];
  await visit('guestbook');
  await entryItem(5).getByText('운영자가 가린 글입니다. 나에게만 보입니다.').waitFor();
  assert.equal(await entryItem(5).getByRole('button').count(), 0);
});

test('the operator replies inline, edits and removes the reply', async () => {
  member = { id: 1, nickname: '운영자', role: 'ROLE_ADMIN' };
  entries = entries.map(item => item.id === 2 ? { ...item, readable: true, content: '운영자님께만' } : item);
  await visit('guestbook');
  await entryItem(2).getByText('운영자님께만').waitFor();
  assert.equal(await entryItem(1).getByRole('button', { name: '답글' }).count(), 0);

  await entryItem(2).getByRole('button', { name: '답글' }).click();
  await entryItem(2).getByLabel('손님2 님에게 답글 쓰는 중').fill('비밀 답장');
  await entryItem(2).getByRole('button', { name: '답글 등록' }).click();
  await entryItem(2).locator('.reply').getByText('비밀 답장').waitFor();
  assert.deepEqual(lastRequest('PUT', /\/admin\/blog\/guestbook\/2\/reply$/).body, { content: '비밀 답장' });

  await entryItem(2).locator('.reply').getByRole('button', { name: '삭제' }).click();
  await entryItem(2).locator('.reply').getByRole('button', { name: '지우기' }).click();
  await entryItem(2).locator('.reply').waitFor({ state: 'detached' });
  assert.ok(lastRequest('DELETE', /\/admin\/blog\/guestbook\/2\/reply$/));
});

test('pages move through the query string and the server is asked from zero', async () => {
  listHandler = (route, url) => {
    const requested = Number(url.searchParams.get('page'));
    return ok(route, pageOf([entry(requested === 1 ? 11 : 1)], { page: requested, totalElements: 11, totalPages: 2 }));
  };
  await visit('guestbook');
  await entryItem(1).waitFor();
  await page.getByRole('navigation', { name: '방명록 페이지' }).getByRole('link', { name: '2' }).click();
  await entryItem(11).waitFor();
  assert.match(page.url(), /\/guestbook\?page=2$/);
  assert.equal(lastRequest('GET', /^\/api\/v1\/blog\/guestbook$/).query.page, '1');
});

test('the page fits a phone without sideways scrolling', async () => {
  await page.setViewportSize({ width: 360, height: 780 });
  entries.push(entry(9, { content: '띄어쓰기없이아주길게이어지는인사말'.repeat(8) }));
  await visit('guestbook');
  await entryItem(9).waitFor();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert.ok(overflow <= 0, `horizontal overflow ${overflow}px`);
});

test('the site menu leads to the guestbook', async () => {
  // 대문은 다른 API 를 많이 불러서 정적 화면에서 연다. 헤더는 어느 공개 화면이든 같다
  await visit('privacy');
  await page.getByRole('button', { name: '메뉴' }).click();
  await page.getByRole('link', { name: '방명록' }).click();
  await page.waitForURL('**/guestbook');
  await page.getByRole('heading', { name: '방명록' }).waitFor();
});

test('a reply notification opens the page holding my entry and highlights it', async () => {
  notices = [{ id: 1, type: 'GUESTBOOK_REPLIED', actorNickname: '운영자', postId: null, postTitle: null, commentId: null,
    guestbookEntryId: 11, preview: '와 주셔서 고마워요', createdAt: '2026-10-17T22:00:00', unread: true }];
  listHandler = (route, url) => {
    // 서버가 글 번호로 쪽을 골라 준다. 0부터 세므로 1 은 화면의 2쪽이다
    const onSecondPage = url.searchParams.get('entry') === '11' || url.searchParams.get('page') === '1';
    return ok(route, pageOf(onSecondPage
      ? [entry(11, { nickname: '테스터', mine: true, reply: { nickname: '운영자', content: '와 주셔서 고마워요', repliedAt: '2026-10-17T22:00:00' } })]
      : [entry(12)], { page: onSecondPage ? 1 : 0, totalElements: 11, totalPages: 2 }));
  };
  await visit('privacy');
  await page.locator('.notification-bell__trigger').click();
  const item = page.locator('button.notification-bell__item').first();
  await item.waitFor();
  assert.match(await item.textContent(), /운영자 님이 내 방명록에 답글을 남겼습니다/);
  await item.click();

  await page.waitForURL('**/guestbook?page=2#guestbook-11');
  await page.locator('#guestbook-11.guestbook-entry--target').waitFor();
  const lookups = requests.filter(item => item.path === '/api/v1/blog/guestbook');
  assert.equal(lookups[0].query.entry, '11');
  // 쪽을 주소에 적을 때 같은 쪽을 한 번 더 받지 않는다
  assert.equal(lookups.length, 1);
});

test('a notification for an entry that is gone says so instead of showing an unexplained first page', async () => {
  await visit('guestbook?entry=99');
  await page.getByText('알림의 방명록 글을 찾을 수 없습니다. 지워졌거나 가려진 글일 수 있습니다.').waitFor();
  await page.waitForURL(url => !url.search.includes('entry'));
  assert.equal(await page.locator('.guestbook-entry--target').count(), 0);
});

test('a hide notification leads the writer to their own hidden entry', async () => {
  notices = [{ id: 2, type: 'GUESTBOOK_HIDDEN', actorNickname: '테스터', postId: null, postTitle: null, commentId: null,
    guestbookEntryId: 5, preview: '가려진 내 글', createdAt: '2026-10-17T22:30:00', unread: true }];
  entries = [entry(5, { nickname: '테스터', mine: true, hiddenByAdmin: true })];
  await visit('privacy');
  await page.locator('.notification-bell__trigger').click();
  const item = page.locator('button.notification-bell__item').first();
  await item.waitFor();
  assert.match(await item.textContent(), /내 방명록 글이 운영 기준에 따라 가려졌습니다/);
  await item.click();

  await page.waitForURL('**/guestbook#guestbook-5');
  await page.locator('#guestbook-5.guestbook-entry--target')
    .getByText('운영자가 가린 글입니다. 나에게만 보입니다.').waitFor();
  assert.equal(requests.find(item => item.path === '/api/v1/blog/guestbook').query.entry, '5');
});
