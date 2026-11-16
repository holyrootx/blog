import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, cacheDir, errors, requests, postRequests, waiters;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const post = (id, title) => ({
	id, title, categoryId: 1, categoryName: '개발', views: 1,
	publishedAt: '2026-01-01T12:00:00', thumbnailImageUrl: null,
});
const section = sort => page.locator('section.home-section').filter({
	has: page.getByRole('heading', { name: sort === 'popular' ? '인기글' : '최근 글', exact: true }),
});
const count = sort => postRequests[sort].length;

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-home-recovery-'));
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen();
	browser = await chromium.launch({
		headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});

after(async () => {
	await browser?.close();
	await server?.close();
	await rm(cacheDir, { recursive: true, force: true });
});

beforeEach(async () => {
	errors = []; requests = []; postRequests = { popular: [], latest: [] }; waiters = new Map();
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.emulateMedia({ reducedMotion: 'reduce' });
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const url = new URL(route.request().url());
		if (url.origin !== origin) { return route.abort('blockedbyclient'); }
		if (!url.pathname.startsWith('/api/v1/')) { return route.continue(); }
		requests.push(url.pathname + url.search);
		if (url.pathname === '/api/v1/blog/home/posts') {
			const sort = url.searchParams.get('sort');
			const index = postRequests[sort].push(route) - 1;
			waiters.get(`${sort}:${index}`)?.(route);
			return;
		}
		if (url.pathname === '/api/v1/blog/home/hero') {
			return ok(route, { title: '테스트 블로그', subTitle: '개발 기록', intro: '읽고 기록합니다.' });
		}
		if (url.pathname === '/api/v1/blog/home/topics') { return ok(route, []); }
		if (url.pathname === '/api/v1/blog/home/topic-section') { return ok(route, {}); }
		if (url.pathname === '/api/v1/blog/profile') { return ok(route, { name: '테스트 블로그' }); }
		return ok(route);
	});
});

afterEach(async (context) => {
	if (process.env.QA_SCREENSHOT_DIR && page && !page.isClosed()) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({
			path: join(process.env.QA_SCREENSHOT_DIR, `${context.name.replace(/[^a-zA-Z0-9-]+/g, '-').slice(0, 130)}.png`),
			fullPage: true,
		});
	}
	await page?.close();
	assert.deepEqual(errors, []);
});

function waitForPost(sort, index = 0) {
	if (postRequests[sort][index]) { return Promise.resolve(postRequests[sort][index]); }
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${sort} request ${index} was not sent`)), 5000);
		waiters.set(`${sort}:${index}`, route => { clearTimeout(timer); resolve(route); });
	});
}

async function finish(route, data, status = 200) {
	const received = page.waitForResponse(response => response.request() === route.request());
	if (status === 200) {
		await ok(route, data);
	} else {
		await route.fulfill({ status, json: { success: false, code: 'TEST_UNAVAILABLE', message: 'fixture detail must stay out of UI' } });
	}
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function navigate(path) {
	await page.evaluate(path => document.querySelector('#app').__vue_app__
		.config.globalProperties.$router.push(path), path);
}

async function openHome(width) {
	await page.setViewportSize({ width, height: 900 });
	await page.goto(server.resolvedUrls.local[0]);
	await waitForPost('popular');
	await waitForPost('latest');
}

async function expectLayout() {
	assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
	for (const sort of ['popular', 'latest']) {
		const titleId = await section(sort).getAttribute('aria-labelledby');
		assert.equal(titleId, `home-posts-${sort}-title`);
		assert.equal(await page.locator(`[id="${titleId}"]`).count(), 1);
		assert.equal(await page.getByRole('region', { name: sort === 'popular' ? '인기글' : '최근 글', exact: true }).count(), 1);
	}
}

for (const width of [1280, 390]) {
	test(`${width}: initial failure has its own retry while the other section is still pending`, async (context) => {
		await openHome(width);
		await finish(await waitForPost('popular'), null, 503);
		context.diagnostic(`popular busy=${await section('popular').getAttribute('aria-busy')}, alerts=${await section('popular').getByRole('alert').count()}, retry buttons=${await section('popular').getByRole('button').count()}; latest busy=${await section('latest').getAttribute('aria-busy')}`);
		assert.equal(await section('popular').getAttribute('aria-busy'), 'false');
		assert.equal(await section('latest').getAttribute('aria-busy'), 'true');
		await section('popular').getByRole('alert').waitFor();
		await section('popular').getByRole('button', { name: '다시 시도', exact: true }).waitFor();
		assert.equal(await page.getByText('fixture detail must stay out of UI', { exact: false }).count(), 0);
		assert.deepEqual({ popular: count('popular'), latest: count('latest') }, { popular: 1, latest: 1 });
		const unrelated = requests.filter(path => path.startsWith('/api/v1/blog/home/') && !path.includes('/posts?'));
		const retry = section('popular').getByRole('button', { name: '다시 시도', exact: true });
		await retry.focus();
		await page.keyboard.press('Enter');
		const pending = await waitForPost('popular', 1);
		const button = section('popular').getByRole('button', { name: '다시 불러오는 중…', exact: true });
		assert.equal(await button.getAttribute('aria-disabled'), 'true');
		await page.keyboard.press('Enter');
		await page.keyboard.press('Space');
		await button.dblclick({ force: true });
		assert.deepEqual({ popular: count('popular'), latest: count('latest') }, { popular: 2, latest: 1 });
		await finish(pending, [post(1, '복구된 인기글')]);
		await section('popular').getByRole('heading', { name: '복구된 인기글', exact: true }).waitFor();
		assert.equal(await section('popular').getByRole('button').count(), 0);
		assert.equal(await section('popular').getByRole('heading', { name: '인기글', exact: true })
			.evaluate(element => document.activeElement === element), true);
		assert.equal(await section('latest').getAttribute('aria-busy'), 'true');
		assert.deepEqual(requests.filter(path => path.startsWith('/api/v1/blog/home/') && !path.includes('/posts?')), unrelated);
		await finish(await waitForPost('latest'), [post(2, '독립된 최근 글')]);
		await section('latest').getByRole('heading', { name: '독립된 최근 글', exact: true }).waitFor();
		await expectLayout();
		context.diagnostic('Enter + Space + pointer dblclick during pending: one retry HTTP request; focus returns to its section title');
	});

	test(`${width}: a latest failure and another failed retry preserve successful popular cards`, async () => {
		await openHome(width);
		await finish(await waitForPost('popular'), [post(1, '유지할 인기글')]);
		await finish(await waitForPost('latest'), null, 503);
		await section('latest').getByRole('alert').waitFor();
		assert.equal(await section('latest').getByText('아직 표시할 글이 없습니다.', { exact: true }).count(), 0);
		const retry = section('latest').getByRole('button', { name: '다시 시도', exact: true });
		await retry.focus();
		await page.keyboard.press('Space');
		await finish(await waitForPost('latest', 1), null, 503);
		await retry.waitFor();
		assert.equal(await retry.evaluate(element => document.activeElement === element), true);
		assert.equal(await retry.getAttribute('aria-disabled'), 'false');
		assert.equal(await section('latest').getAttribute('aria-busy'), 'false');
		assert.equal(await section('popular').getAttribute('aria-busy'), 'false');
		await section('popular').getByRole('heading', { name: '유지할 인기글', exact: true }).waitFor();
		assert.deepEqual({ popular: count('popular'), latest: count('latest') }, { popular: 1, latest: 2 });
		await expectLayout();
	});

	test(`${width}: successful empty lists have their own message without error or retry`, async () => {
		await openHome(width);
		for (const sort of ['popular', 'latest']) {
			await finish(await waitForPost(sort), []);
			await section(sort).getByText('아직 표시할 글이 없습니다.', { exact: true }).waitFor();
			assert.equal(await section(sort).getByRole('alert').count(), 0);
			assert.equal(await section(sort).getByRole('button').count(), 0);
			assert.equal(await section(sort).getAttribute('aria-busy'), 'false');
		}
		await expectLayout();
	});

	test(`${width}: cached cards remain visible during refresh failure and a section-only retry`, async () => {
		await openHome(width);
		await finish(await waitForPost('popular'), [post(1, '캐시된 인기글')]);
		await finish(await waitForPost('latest'), [post(2, '캐시된 최근 글')]);
		await navigate('/privacy');
		await section('popular').waitFor({ state: 'hidden' });
		await navigate('/');
		await waitForPost('popular', 1);
		await waitForPost('latest', 1);
		await section('popular').getByRole('heading', { name: '캐시된 인기글', exact: true }).waitFor();
		await section('latest').getByRole('heading', { name: '캐시된 최근 글', exact: true }).waitFor();
		assert.equal(await section('popular').locator('.post-card--skeleton').count(), 0);
		assert.equal(await section('popular').getAttribute('aria-busy'), 'true');
		await finish(await waitForPost('popular', 1), null, 503);
		await finish(await waitForPost('latest', 1), null, 503);
		for (const sort of ['popular', 'latest']) {
			await section(sort).getByRole('alert').getByText('글 목록을 새로 불러오지 못했습니다. 이전에 불러온 결과를 표시합니다.', { exact: true }).waitFor();
			assert.equal(await section(sort).getAttribute('aria-busy'), 'false');
		}
		await section('popular').getByRole('button', { name: '다시 시도', exact: true }).click();
		const retry = await waitForPost('popular', 2);
		await section('popular').getByRole('heading', { name: '캐시된 인기글', exact: true }).waitFor();
		assert.equal(await section('popular').locator('.post-card--skeleton').count(), 0);
		await finish(retry, [post(3, '새 인기글')]);
		await section('popular').getByRole('heading', { name: '새 인기글', exact: true }).waitFor();
		assert.equal(await section('popular').getByRole('alert').count(), 0);
		await section('latest').getByRole('heading', { name: '캐시된 최근 글', exact: true }).waitFor();
		assert.equal(await section('latest').getByRole('alert').count(), 1);
		assert.deepEqual({ popular: count('popular'), latest: count('latest') }, { popular: 3, latest: 2 });
		await expectLayout();
	});

	test(`${width}: completing a retry preserves focus moved outside the failed section`, async () => {
		await openHome(width);
		await finish(await waitForPost('popular'), null, 503);
		await finish(await waitForPost('latest'), []);
		await section('popular').getByRole('button', { name: '다시 시도', exact: true }).focus();
		await page.keyboard.press('Enter');
		const retry = await waitForPost('popular', 1);
		const outside = section('latest').getByRole('link', { name: '전체 보기', exact: true });
		await outside.focus();
		await finish(retry, []);
		await section('popular').getByText('아직 표시할 글이 없습니다.', { exact: true }).waitFor();
		assert.equal(await outside.evaluate(element => document.activeElement === element), true);
		assert.equal(await section('popular').getByRole('button').count(), 0);
		await expectLayout();
	});

	for (const stale of ['success', 'failure']) {
		test(`${width}: a ${stale} response from an unmounted home cannot alter the new home`, async () => {
			await openHome(width);
			const old = await waitForPost('popular');
			await finish(await waitForPost('latest'), []);
			await navigate('/privacy');
			await section('popular').waitFor({ state: 'hidden' });
			await navigate('/');
			const current = await waitForPost('popular', 1);
			await waitForPost('latest', 1);
			if (stale === 'failure') {
				await finish(old, null, 503);
				assert.equal(await section('popular').getByRole('alert').count(), 0);
				assert.equal(await section('popular').getAttribute('aria-busy'), 'true');
			}
			await finish(current, [post(3, '현재 화면 인기글')]);
			await finish(await waitForPost('latest', 1), []);
			if (stale === 'success') { await finish(old, [post(99, '떠난 화면의 글')]); }
			await section('popular').getByRole('heading', { name: '현재 화면 인기글', exact: true }).waitFor();
			assert.equal(await section('popular').getByRole('heading', { name: '떠난 화면의 글', exact: true }).count(), 0);
			assert.equal(await section('popular').getByRole('alert').count(), 0);
			assert.equal(await section('popular').getAttribute('aria-busy'), 'false');
			await navigate('/privacy');
			await section('popular').waitFor({ state: 'hidden' });
			await navigate('/');
			await waitForPost('popular', 2);
			await section('popular').getByRole('heading', { name: '현재 화면 인기글', exact: true }).waitFor();
			assert.equal(await section('popular').getByRole('heading', { name: '떠난 화면의 글', exact: true }).count(), 0);
			await expectLayout();
		});
	}

	test(`${width}: independent skeletons keep image size and reduced-motion semantics`, async () => {
		await openHome(width);
		for (const sort of ['popular', 'latest']) {
			assert.equal(await section(sort).getAttribute('aria-busy'), 'true');
			assert.equal(await section(sort).locator('.post-card--skeleton').count(), 4);
			assert.equal(await section(sort).locator('.post-grid').getAttribute('aria-hidden'), 'true');
			assert.equal(await section(sort).locator('.ui-skeleton').first()
				.evaluate(element => getComputedStyle(element).animationName), 'none');
		}
		const before = await section('popular').locator('.post-card__image').first().boundingBox();
		await finish(await waitForPost('popular'), [post(1, '첫 인기글'), post(2, '둘째 인기글'), post(3, '셋째 인기글'), post(4, '넷째 인기글')]);
		const after = await section('popular').locator('.post-card__image').first().boundingBox();
		assert.ok(Math.abs(before.width - after.width) < 1);
		assert.ok(Math.abs(before.height - after.height) < 1);
		assert.equal(await section('popular').locator('.post-card--skeleton').count(), 0);
		assert.equal(await section('latest').locator('.post-card--skeleton').count(), 4);
		await finish(await waitForPost('latest'), []);
		assert.equal(await section('popular').getByRole('link', { name: '전체 보기', exact: true }).getAttribute('href'), '/posts?sort=popular');
		assert.equal(await section('latest').getByRole('link', { name: '전체 보기', exact: true }).getAttribute('href'), '/posts');
		await expectLayout();
	});
}
