import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, listRequests, heldLists, heldRecommendations, recommendationWaiters;
const ok = (route, data) => route.fulfill({ json: { success: true, data } });
const emptyResult = { items: [], totalElements: 0, totalPages: 0 };
const post = (id, title) => ({
	id, title, categoryId: 1, categoryName: '개발', views: 1,
	publishedAt: '2026-01-01T12:00:00', thumbnailImageUrl: null,
});
const list = () => page.locator('.post-list');
const fallback = () => page.locator('.post-list__fallback');

before(async () => {
	const port = Number(process.env.POST_LIST_TEST_PORT ?? 0);
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		cacheDir: 'node_modules/.vite-post-list-loading', plugins: [vue()],
		server: { host: '127.0.0.1', port, strictPort: port !== 0 },
	});
	await server.listen();
	browser = await chromium.launch({
		headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = []; listRequests = []; heldLists = new Map();
	heldRecommendations = []; recommendationWaiters = new Map();
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const url = new URL(route.request().url());
		if (url.origin !== origin) {
			return route.abort('blockedbyclient');
		}
		if (url.pathname === '/api/v1/blog/posts') {
			const keyword = url.searchParams.get('q') ?? '';
			listRequests.push(keyword);
			if (heldLists.has(keyword)) {
				heldLists.get(keyword)(route);
				return;
			}
			return ok(route, emptyResult);
		}
		if (url.pathname === '/api/v1/blog/home/posts') {
			const index = heldRecommendations.push(route) - 1;
			recommendationWaiters.get(index)?.(route);
			return;
		}
		if (url.pathname.startsWith('/api/v1/')) {
			return ok(route, []);
		}
		return route.continue();
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

function waitForRecommendation(index = 0) {
	if (heldRecommendations[index]) {
		return Promise.resolve(heldRecommendations[index]);
	}
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`recommendation request ${index} was not sent`)), 5000);
		recommendationWaiters.set(index, route => { clearTimeout(timer); resolve(route); });
	});
}

async function navigate(path) {
	await page.evaluate(path => document.querySelector('#app').__vue_app__
		.config.globalProperties.$router.push(path), path);
}

function holdNextList(keyword) {
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error('list refresh was not sent')), 5000);
		heldLists.set(keyword, route => { clearTimeout(timer); resolve(route); });
	});
}

async function finish(route, data, status = 200) {
	const received = page.waitForResponse(response => response.url() === route.request().url());
	if (status === 200) {
		await ok(route, data);
	} else {
		await route.fulfill({ status, json: { success: false, code: 'REQUEST_FAILED', message: '조회 실패' } });
	}
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function expectEmpty(keyword) {
	await list().getByText(`‘${keyword}’ 검색 결과가 없습니다.`, { exact: true }).waitFor();
	assert.equal(await list().getAttribute('aria-busy'), 'false');
	assert.equal(await list().locator(':scope > .post-grid .post-card--skeleton').count(), 0);
	assert.equal(await page.locator('.post-list__count').textContent(), '0편');
}

for (const width of [1280, 390]) {
	test(`${width}px empty search finishes while the recommendation HTTP response is held`, async () => {
		await page.setViewportSize({ width, height: 900 });
		await page.emulateMedia({ reducedMotion: 'reduce' });
		await page.goto(`${server.resolvedUrls.local[0]}posts?q=missing`);
		const held = await waitForRecommendation();
		await expectEmpty('missing');
		assert.equal(await fallback().getAttribute('aria-busy'), 'true');
		assert.equal(await fallback().locator('.post-card--skeleton').count(), 4);
		assert.equal(await fallback().locator('.post-grid').getAttribute('aria-hidden'), 'true');
		assert.equal(await fallback().locator('.ui-skeleton').first()
			.evaluate(element => getComputedStyle(element).animationName), 'none');
		assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
		if (process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/empty-search-${width}.png`, fullPage: true });
		}
		await ok(held, [post(9, '추천 글')]);
		await fallback().getByRole('heading', { name: '추천 글', exact: true }).waitFor();
		assert.equal(await fallback().getAttribute('aria-busy'), 'false');
		assert.equal(await fallback().locator('.post-card--skeleton').count(), 0);
	});
}

test('a failed recommendation HTTP response does not turn an empty search into an error', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=missing`);
	const held = await waitForRecommendation();
	await expectEmpty('missing');
	await finish(held, null, 500);
	await fallback().waitFor({ state: 'hidden' });
	await expectEmpty('missing');
	assert.equal(await list().getByRole('alert').count(), 0);
});

for (const order of ['before', 'after']) {
	test(`an old search recommendation arriving ${order} the current one cannot replace it`, async () => {
		await page.goto(`${server.resolvedUrls.local[0]}posts?q=first`);
		const old = await waitForRecommendation();
		await navigate('/posts?q=second');
		const current = await waitForRecommendation(1);
		await expectEmpty('second');
		if (order === 'before') {
			await finish(old, [post(99, '이전 추천')]);
			assert.equal(await fallback().getAttribute('aria-busy'), 'true');
			assert.equal(await list().getByRole('heading', { name: '이전 추천', exact: true }).count(), 0);
		}
		await finish(current, [post(10, '현재 추천')]);
		if (order === 'after') {
			await finish(old, [post(99, '이전 추천')]);
		}
		await fallback().getByRole('heading', { name: '현재 추천', exact: true }).waitFor();
		assert.equal(await list().getByRole('heading', { name: '이전 추천', exact: true }).count(), 0);
		assert.equal(await fallback().getAttribute('aria-busy'), 'false');
	});
}

test('back navigation restores cached empty results while refreshing and ignores both older recommendations', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=first`);
	const first = await waitForRecommendation();
	await navigate('/posts?q=second');
	const second = await waitForRecommendation(1);
	const listRefresh = holdNextList('first');
	await page.goBack();
	const current = await waitForRecommendation(2);
	const refreshing = await listRefresh;
	await expectEmpty('first');
	await finish(current, [post(10, '돌아온 검색의 추천')]);
	await finish(first, [post(98, '첫 요청의 늦은 추천')]);
	await finish(second, [post(99, '다른 검색의 늦은 추천')]);
	await finish(refreshing, emptyResult);
	await fallback().getByRole('heading', { name: '돌아온 검색의 추천', exact: true }).waitFor();
	assert.equal(await list().getByRole('heading', { name: /늦은 추천/ }).count(), 0);
	assert.deepEqual(listRequests, ['first', 'second', 'first']);
	assert.equal(heldRecommendations.length, 3);
});

test('a recommendation from an unmounted list cannot enter a remounted cached search', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=missing`);
	const old = await waitForRecommendation();
	await page.getByRole('link', { name: '개인정보처리방침', exact: true }).click();
	await page.waitForURL('**/privacy');
	await list().waitFor({ state: 'hidden' });
	assert.equal(await list().count(), 0);
	const listRefresh = holdNextList('missing');
	await page.goBack();
	const current = await waitForRecommendation(1);
	const refreshing = await listRefresh;
	await expectEmpty('missing');
	await finish(old, [post(99, '떠난 화면의 추천')]);
	assert.equal(await fallback().getAttribute('aria-busy'), 'true');
	assert.equal(await list().getByRole('heading', { name: '떠난 화면의 추천', exact: true }).count(), 0);
	await finish(current, [post(10, '새 화면의 추천')]);
	await finish(refreshing, emptyResult);
	await fallback().getByRole('heading', { name: '새 화면의 추천', exact: true }).waitFor();
	assert.equal(heldRecommendations.length, 2);
});

test('cached search and recommendations remain visible when both refresh requests fail', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=missing`);
	await finish(await waitForRecommendation(), [post(9, '캐시된 추천')]);
	await page.getByRole('link', { name: '개인정보처리방침', exact: true }).click();
	await page.waitForURL('**/privacy');
	await list().waitFor({ state: 'hidden' });
	const listRefresh = holdNextList('missing');
	await page.goBack();
	const recommendation = await waitForRecommendation(1);
	const refreshing = await listRefresh;
	await expectEmpty('missing');
	assert.equal(await fallback().getAttribute('aria-busy'), 'false');
	assert.equal(await fallback().locator('.post-card--skeleton').count(), 0);
	await fallback().getByRole('heading', { name: '캐시된 추천', exact: true }).waitFor();
	await finish(refreshing, null, 500);
	await finish(recommendation, null, 500);
	await expectEmpty('missing');
	await fallback().getByRole('heading', { name: '캐시된 추천', exact: true }).waitFor();
	assert.equal(await list().getByRole('alert').count(), 0);
});

test('nonempty search and an ordinary empty list do not request recommendations', async () => {
	await page.route('**/api/v1/blog/posts?*', route => ok(route, {
		items: [post(1, '검색된 글')], totalElements: 1, totalPages: 1,
	}));
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=found`);
	await list().getByRole('heading', { name: '검색된 글', exact: true }).waitFor();
	assert.equal(await fallback().count(), 0);
	assert.equal(heldRecommendations.length, 0);
	await page.unroute('**/api/v1/blog/posts?*');
	await page.getByRole('button', { name: '검색 지우기', exact: true }).click();
	await list().getByText('아직 글이 없습니다.', { exact: true }).waitFor();
	assert.equal(await fallback().count(), 0);
	assert.equal(heldRecommendations.length, 0);
});
