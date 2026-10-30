import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, requests, summaryHandler, postsHandler;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = route => route.fulfill({ status: 500, json: { success: false, message: '일시적인 오류입니다.' } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const dates = (days, last = '2026-10-17') => Array.from({ length: days }, (_, index) => {
	const date = new Date(`${last}T00:00:00Z`);
	date.setUTCDate(date.getUTCDate() - (days - 1 - index));
	return date.toISOString().slice(0, 10);
});
const summary = (days, fields = {}) => {
	const daily = dates(days).map((date, index) => ({ date, pageViews: index === 1 ? 12 : index % 3,
		memberViews: index === 1 ? 5 : 0, anonymousViews: index === 1 ? 7 : index % 3 }));
	return { days, from: daily[0].date, to: daily.at(-1).date, recordingStartedAt: '2026-09-01T00:00:00Z',
		pageViews: daily.reduce((sum, day) => sum + day.pageViews, 0), memberViews: 5, anonymousViews: 10,
		uniqueMembers: 3, excludedAdminViews: 4, excludedBotViews: 2, daily,
		devices: [{ deviceType: 'MOBILE', pageViews: 6 }, { deviceType: 'TABLET', pageViews: 1 },
			{ deviceType: 'DESKTOP', pageViews: 8 }, { deviceType: 'UNKNOWN', pageViews: 0 }], ...fields };
};
const postRow = (postId, fields = {}) => ({ postId, title: `인기 글 ${postId}`, status: 'PUBLISHED', deleted: false,
	pageViews: 20 - postId, uniqueMembers: 2, anonymousViews: 5, totalViews: 300, commentCount: 4, likeCount: 7, ...fields });
const panel = () => page.locator('.admin-view-stats');
const statsRequests = path => requests.filter(request => request.path === path);

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = []; requests = [];
	summaryHandler = (route, url) => ok(route, summary(Number(url.searchParams.get('days'))));
	postsHandler = (route, url) => {
		const pageIndex = Number(url.searchParams.get('page'));
		const items = pageIndex === 0
			? [postRow(1), postRow(2, { status: 'PRIVATE' }), postRow(3, { deleted: true, title: '영구 삭제된 글' })]
			: [postRow(11)];
		return ok(route, { days: Number(url.searchParams.get('days')), items, page: pageIndex, size: 10,
			totalElements: 11, totalPages: 2 });
	};
	page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, timezoneId: 'America/New_York' });
	page.setDefaultTimeout(6000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/api/v1/**', route => {
		const url = new URL(route.request().url());
		const path = url.pathname;
		requests.push({ path, query: Object.fromEntries(url.searchParams) });
		if (path === '/api/v1/admin/auth/me') {
			return ok(route, { username: 'test', role: 'ADMIN' });
		}
		if (path.endsWith('/csrf')) {
			return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
		}
		if (path === '/api/v1/admin/blog/dashboard') {
			return ok(route, { draftPostCount: 1, totalViews: 900,
				publishedPostCount: 3, postCountThisMonth: 1, reportedCommentCount: 0, categoryShares: [] });
		}
		if (path === '/api/v1/admin/blog/stats/views') {
			return summaryHandler(route, url);
		}
		if (path === '/api/v1/admin/blog/stats/views/posts') {
			return postsHandler(route, url);
		}
		return ok(route, null);
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

const openDashboard = async () => {
	await page.goto(`${server.resolvedUrls.local[0]}admin/dashboard`);
	await panel().waitFor();
};

test('요약 숫자와 날짜별 막대, 기기 비율을 보여 주고 누적 조회와 기준이 다르다고 적는다', async () => {
	await openDashboard();
	await panel().locator('.admin-view-stats__tile').nth(3).getByText('6건').waitFor();

	const tiles = await panel().locator('.admin-view-stats__tile').allInnerTexts();
	assert.match(tiles[0], /조회 기록\s*\d+건/);
	assert.match(tiles[1], /로그인 회원\s*3명\s*회원 조회 5건/);
	assert.match(tiles[2], /비회원 방문\s*10건\s*사람 수가 아니라 방문 건수/);
	assert.match(tiles[3], /통계에서 뺀 조회\s*6건\s*관리자 본인 4건 · 검색 봇 2건/);
	assert.match(await panel().locator('.admin-panel__header').innerText(), /관리자 본인 조회와 검색 봇은 뺐습니다/);
	assert.match(await panel().locator('.admin-panel__header').innerText(), /누적 조회와는 세는 기준이 다릅니다/);
	assert.match(await page.locator('.admin-summary-card').filter({ hasText: '누적 조회' }).innerText(), /기록 시작 전 포함/);

	assert.equal(await panel().locator('.admin-daily-chart__column').count(), 7);
	assert.equal(await panel().locator('.admin-daily-chart__value').innerText(), '12');
	const devices = await panel().locator('.admin-view-stats__devices li').allInnerTexts();
	assert.match(devices[0], /휴대폰\s*6건 · 40%/);
	assert.match(devices[2], /데스크톱\s*8건 · 53%/);
	assert.match(devices[3], /알 수 없음\s*0건 · 0%/);
});

test('막대는 마우스와 키보드 모두로 값을 보여 주고, 같은 값을 표로도 볼 수 있다', async () => {
	await openDashboard();
	const column = panel().locator('.admin-daily-chart__column').nth(1);
	await column.hover();
	const tooltip = panel().locator('.admin-daily-chart__tooltip');
	await tooltip.waitFor();
	assert.match(await tooltip.innerText(), /12건\s*10\.12\s*회원 5 · 비회원 7/);

	await page.mouse.move(0, 0);
	await tooltip.waitFor({ state: 'detached' });
	await panel().locator('.admin-daily-chart__column').nth(0).focus();
	await page.keyboard.press('Tab');
	await tooltip.waitFor();
	assert.match(await tooltip.innerText(), /10\.12/);
	assert.match(await column.getAttribute('aria-label'), /10\.12 조회 기록 12건, 회원 5건, 비회원 7건/);

	await panel().getByText('날짜별 숫자 보기').click();
	const rows = await panel().locator('.admin-daily-chart__table tbody tr').allInnerTexts();
	assert.equal(rows.length, 7);
	assert.match(rows[1], /10\.12\s+12\s+5\s+7/);
});

test('기간을 30일로 바꾸면 두 영역을 모두 다시 받고, 받는 동안 이전 숫자를 흐리게 남긴다', async () => {
	await openDashboard();
	await panel().locator('.admin-daily-chart__column').nth(6).waitFor();

	const hold = deferred();
	summaryHandler = async (route, url) => { await hold.promise; return ok(route, summary(Number(url.searchParams.get('days')))); };
	await panel().getByRole('button', { name: '최근 30일' }).click();

	const area = panel().locator('.admin-view-stats__summary');
	await page.waitForFunction(() => document.querySelector('.admin-view-stats__summary')
		?.classList.contains('admin-view-stats__summary--refreshing'));
	assert.equal(await area.getAttribute('aria-busy'), 'true');
	assert.equal(await panel().locator('.admin-daily-chart__column').count(), 7, '받는 동안 앞의 그림을 지우지 않는다');

	hold.resolve();
	await page.waitForFunction(() => document.querySelectorAll('.admin-daily-chart__column').length === 30);
	assert.equal(await area.getAttribute('aria-busy'), 'false');
	assert.deepEqual(statsRequests('/api/v1/admin/blog/stats/views').map(request => request.query.days), ['7', '30']);
	assert.deepEqual(statsRequests('/api/v1/admin/blog/stats/views/posts').at(-1).query, { days: '30', page: '0', size: '10' });
});

test('글 목록은 순위와 상태를 보이고, 지금 값에는 표시가 붙으며, 쪽을 넘기면 다음 쪽을 받는다', async () => {
	await openDashboard();
	const grid = panel().locator('.admin-view-stats__posts');
	await grid.getByText('인기 글 1', { exact: true }).waitFor();

	const header = await grid.locator('thead').innerText();
	assert.match(header, /누적 조회\*/);
	assert.match(header, /댓글\*/);
	assert.match(header, /좋아요\*/);
	const rows = await grid.locator('tbody tr').allInnerTexts();
	assert.match(rows[0], /^1\s+인기 글 1\s+19\s+2\s+5\s+300\s+4\s+7$/);
	assert.match(rows[1], /인기 글 2\s+비공개/);
	assert.match(rows[2], /영구 삭제된 글\s+삭제됨/);

	await grid.getByRole('button', { name: '2', exact: true }).click();
	await grid.getByText('인기 글 11', { exact: true }).waitFor();
	assert.match(await grid.locator('tbody tr').first().innerText(), /^11\s+인기 글 11/);
	assert.deepEqual(statsRequests('/api/v1/admin/blog/stats/views/posts').at(-1).query, { days: '7', page: '1', size: '10' });
});

test('요약과 글 목록은 서로 기다리지 않고, 각자 실패하면 각자 다시 시도한다', async () => {
	const hold = deferred();
	let summaryFailed = false;
	summaryHandler = async (route, url) => {
		await hold.promise;
		if (!summaryFailed) { summaryFailed = true; return fail(route); }
		return ok(route, summary(Number(url.searchParams.get('days'))));
	};
	await openDashboard();
	await panel().locator('.admin-view-stats__posts').getByText('인기 글 1', { exact: true }).waitFor();
	assert.equal(await panel().locator('.admin-view-stats__summary').getAttribute('aria-busy'), 'true');
	assert.equal(await panel().locator('.admin-view-stats__summary .ui-skeleton[aria-hidden="true"], .admin-view-stats__summary [aria-hidden="true"] .ui-skeleton').count() > 0, true);

	hold.resolve();
	await panel().getByText('일시적인 오류입니다.').waitFor();
	await panel().locator('.admin-view-stats__error').getByRole('button', { name: '다시 시도' }).click();
	await panel().locator('.admin-daily-chart__column').nth(6).waitFor();

	let postsFailed = false;
	postsHandler = (route, url) => {
		if (!postsFailed) { postsFailed = true; return fail(route); }
		return ok(route, { days: Number(url.searchParams.get('days')), items: [postRow(5)], page: 0, size: 10,
			totalElements: 1, totalPages: 1 });
	};
	await panel().getByRole('button', { name: '최근 30일' }).click();
	await panel().locator('.admin-view-stats__posts').getByText('일시적인 오류입니다.').waitFor();
	await panel().locator('.admin-view-stats__posts').getByRole('button', { name: '다시 시도' }).click();
	await panel().getByText('인기 글 5', { exact: true }).waitFor();
});

test('기록이 없으면 0 과 빈 목록을 보여 주고 "아직 기록이 없다" 고 말한다', async () => {
	summaryHandler = (route, url) => ok(route, summary(Number(url.searchParams.get('days')), {
		recordingStartedAt: null, pageViews: 0, memberViews: 0, anonymousViews: 0, uniqueMembers: 0, excludedAdminViews: 0,
		excludedBotViews: 0,
		daily: dates(7).map(date => ({ date, pageViews: 0, memberViews: 0, anonymousViews: 0 })),
		devices: ['MOBILE', 'TABLET', 'DESKTOP', 'UNKNOWN'].map(deviceType => ({ deviceType, pageViews: 0 })),
	}));
	postsHandler = (route, url) => ok(route, { days: Number(url.searchParams.get('days')), items: [], page: 0, size: 10,
		totalElements: 0, totalPages: 0 });
	await openDashboard();
	await panel().getByText('아직 조회 기록이 없습니다.').waitFor();
	await panel().getByText('이 기간에 조회 기록이 있는 글이 없습니다.').waitFor();
	assert.equal(await panel().locator('.admin-daily-chart__value').count(), 0, '0 인 날에는 꼭대기 숫자를 붙이지 않는다');
	assert.match(await panel().locator('.admin-view-stats__tile').first().innerText(), /0건/);
});
