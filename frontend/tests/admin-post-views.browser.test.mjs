import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, viewRequests, viewsHandler;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const posts = [
	{ id: 1, title: '조회 기록 검증 글', categoryId: 1, categoryName: '개발', status: 'PUBLISHED',
		publishedAt: '2026-10-15T09:00:00', createdAt: '2026-10-15T09:00:00', views: 41 },
	{ id: 2, title: '아무도 안 본 글', categoryId: 1, categoryName: '개발', status: 'PUBLISHED',
		publishedAt: '2026-10-16T09:00:00', createdAt: '2026-10-16T09:00:00', views: 0 },
];
const view = (id, viewerType, fields = {}) => ({ id, viewerType, viewedAt: '2026-10-17T03:30:00Z',
	memberId: null, nickname: null, memberStatus: null, memberStatusName: null, ...fields });
const fullResult = (query) => {
	const viewer = query.get('viewer');
	const all = [
		view(5, 'ANONYMOUS', { viewedAt: '2026-10-17T15:10:00Z' }),
		view(4, 'ADMIN', { memberId: 1, nickname: '블로그주인', memberStatus: 'ACTIVE', memberStatusName: '활동' }),
		view(3, 'MEMBER', { memberId: 9, nickname: '떠난다람쥐', memberStatus: 'WITHDRAWN', memberStatusName: '탈퇴' }),
		view(2, 'MEMBER', { memberId: 8, nickname: '읽는너구리', memberStatus: 'ACTIVE', memberStatusName: '활동' }),
		view(1, 'ANONYMOUS'),
		view(0, 'BOT', { viewedAt: '2026-10-17T01:00:00Z' }),
	];
	const items = viewer ? all.filter(item => item.viewerType === viewer) : all;
	const page = Number(query.get('page') ?? 0);
	const size = Number(query.get('size') ?? 20);
	return { postId: 1, postTitle: '조회 기록 검증 글', totalViews: 41, recordingStartedAt: '2026-10-16T15:00:00Z',
		counts: { all: 6, member: 2, admin: 1, anonymous: 2, bot: 1 },
		items: items.slice(page * size, (page + 1) * size), page, size,
		totalElements: items.length, totalPages: Math.ceil(items.length / size) };
};
const emptyResult = () => ({ postId: 2, postTitle: '아무도 안 본 글', totalViews: 0, recordingStartedAt: null,
	counts: { all: 0, member: 0, admin: 0, anonymous: 0 }, items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
const dialog = () => page.getByRole('dialog', { name: '조회 기록' });
const openViews = async (title) => {
	await page.getByRole('button', { name: `${title} 조회 기록` }).click();
	await dialog().waitFor();
};

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = []; viewRequests = [];
	viewsHandler = (route, postId, query) => ok(route, postId === 1 ? fullResult(query) : emptyResult());
	// 관리자 PC 가 다른 시간대여도 서울 시각으로 보여야 한다
	page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, timezoneId: 'America/New_York' });
	page.setDefaultTimeout(6000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/api/v1/**', async route => {
		const url = new URL(route.request().url());
		const path = url.pathname;
		if (path === '/api/v1/admin/auth/me') {
			return ok(route, { username: 'test', role: 'ADMIN' });
		}
		if (path.endsWith('/csrf')) {
			return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
		}
		if (path.endsWith('/categories')) {
			return ok(route, [{ id: 1, name: '개발', sortOrder: 1 }]);
		}
		if (path === '/api/v1/admin/blog/posts') {
			return ok(route, { items: posts, totalElements: posts.length, totalPages: 1,
				statusCounts: { all: posts.length, published: posts.length }, page: 0, size: 20 });
		}
		const views = /^\/api\/v1\/admin\/blog\/posts\/(\d+)\/views$/.exec(path);
		if (views) {
			viewRequests.push(Object.fromEntries(url.searchParams));
			return viewsHandler(route, Number(views[1]), url.searchParams);
		}
		return ok(route, null);
	});
	await page.goto(`${server.resolvedUrls.local[0]}admin/posts`);
	await page.getByRole('row').filter({ hasText: '조회 기록 검증 글' }).waitFor();
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('조회 기록은 누적 조회수와 기준을 나눠 보여 주고, 시각은 서울 기준으로 적는다', async () => {
	await openViews('조회 기록 검증 글');

	assert.equal(page.url().endsWith('/admin/posts'), true, '버튼을 눌러도 편집 화면으로 넘어가지 않는다');
	await page.waitForFunction(() => document.querySelector('.admin-post-views__figure dd')?.textContent.trim() === '41');
	const summary = await dialog().locator('.admin-post-views__summary').innerText();
	assert.match(summary, /누적 조회수\s*41/);
	assert.match(summary, /조회 기록\s*6건/);
	assert.match(summary, /30분 안에 다시 열면 한 번/);
	assert.match(await dialog().locator('.admin-post-views__since').innerText(), /2026\.10\.17 00:00부터 기록했습니다/);

	const rows = await dialog().locator('tbody tr').allInnerTexts();
	assert.equal(rows.length, 6);
	assert.match(rows[0], /2026\.10\.18 00:10\s+비회원\s+비회원 방문/);
	assert.match(rows[1], /관리자\s+블로그주인\s+#1/);
	assert.match(rows[2], /떠난다람쥐\s+#9\s+탈퇴/);
	assert.doesNotMatch(rows[3], /활동/, '활동 중인 회원에게는 상태 표시를 붙이지 않는다');
	assert.match(rows[4], /2026\.10\.17 12:30/);
	assert.match(rows[5], /검색 봇\s+검색 봇 방문/);
	await dialog().getByRole('button', { name: '검색 봇 1' }).waitFor();
	assert.deepEqual(viewRequests, [{ page: '0', size: '20' }]);
});

test('구분을 고르면 첫 쪽부터 다시 받고, 쪽을 넘기면 서버 기준 쪽 번호로 요청한다', async () => {
	viewsHandler = (route, postId, query) => {
		const result = fullResult(query);
		return ok(route, { ...result, totalPages: 2 });
	};
	await openViews('조회 기록 검증 글');
	await dialog().locator('tbody tr').nth(5).waitFor();

	await dialog().getByRole('button', { name: '2', exact: true }).click();
	await page.waitForFunction(() => document.querySelectorAll('.admin-post-views tbody tr').length >= 1);
	assert.deepEqual(viewRequests.at(-1), { page: '1', size: '20' });

	await dialog().getByRole('button', { name: '비회원 2' }).click();
	await page.waitForFunction(() => document.querySelector('.admin-post-views [aria-pressed="true"]')?.textContent.includes('비회원'));
	assert.deepEqual(viewRequests.at(-1), { viewer: 'ANONYMOUS', page: '0', size: '20' });
});

test('기록이 없으면 빈 상태를 말하고, 실패하면 다시 시도할 수 있다', async () => {
	await openViews('아무도 안 본 글');
	await dialog().getByText('아직 이 글의 조회 기록이 없습니다.').waitFor();
	assert.match(await dialog().locator('.admin-post-views__since').innerText(), /아직 블로그 전체에 조회 기록이 없습니다/);
	await dialog().getByRole('button', { name: '닫기' }).click();
	await dialog().waitFor({ state: 'detached' });

	let failed = false;
	viewsHandler = (route, postId, query) => {
		if (!failed) {
			failed = true;
			return route.fulfill({ status: 500, json: { success: false, message: '조회 기록을 불러오지 못했습니다.' } });
		}
		return ok(route, fullResult(query));
	};
	await openViews('조회 기록 검증 글');
	await dialog().getByText('조회 기록을 불러오지 못했습니다.').waitFor();
	await dialog().getByRole('button', { name: '다시 시도' }).click();
	await dialog().locator('tbody tr').nth(4).waitFor();
});

test('응답 전에는 숫자 자리를 뼈대로 잡아 두고 바쁨을 알린다', async () => {
	const hold = deferred();
	viewsHandler = async (route, postId, query) => { await hold.promise; return ok(route, fullResult(query)); };
	await openViews('조회 기록 검증 글');

	const summary = dialog().locator('.admin-post-views__summary');
	assert.equal(await summary.getAttribute('aria-busy'), 'true');
	assert.equal(await summary.locator('.ui-skeleton[aria-hidden="true"]').count(), 2);
	const before = await summary.boundingBox();

	hold.resolve();
	await page.waitForFunction(() => document.querySelector('.admin-post-views__summary')?.getAttribute('aria-busy') === 'false');
	const afterBox = await summary.boundingBox();
	assert.ok(Math.abs(afterBox.height - before.height) <= 2, `요약 높이가 바뀌지 않아야 한다: ${before.height} → ${afterBox.height}`);
});
