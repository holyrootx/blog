import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, cacheDir, errors, requests, records, waiters;
const traces = [];
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const metadata = title => ({ title, intro: '작은 기록을 주제로 모았습니다.', noteBadge: '기록', note: '하나씩 읽어 보세요.' });
const topic = (id, title) => ({ id, title, label: '개발', description: '테스트 주제 설명', keywords: ['기록', '개발'] });
const region = () => page.locator('.home-topics');
const feedback = kind => region().locator(`.home-topics__feedback--${kind}`);
const retryButton = kind => feedback(kind).getByRole('button');
const count = kind => records[kind].length;

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-home-topic-recovery-'));
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
	errors = []; requests = []; records = { section: [], topics: [] }; waiters = new Map();
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
		const kind = url.pathname === '/api/v1/blog/home/topics' ? 'topics'
			: url.pathname === '/api/v1/blog/home/topic-section' ? 'section' : null;
		if (kind) {
			const record = { route, kind, index: count(kind), fulfillAttempts: 0 };
			records[kind].push(record);
			waiters.get(`${kind}:${record.index}`)?.resolve(record);
			return;
		}
		if (url.pathname === '/api/v1/blog/home/hero') {
			return ok(route, { title: '테스트 블로그', subTitle: '개발 기록', intro: '읽고 기록합니다.' });
		}
		if (url.pathname === '/api/v1/blog/home/posts') { return ok(route, []); }
		if (url.pathname === '/api/v1/blog/profile') { return ok(route, { name: '테스트 블로그' }); }
		return ok(route);
	});
});

afterEach(async context => {
	if (process.env.QA_SCREENSHOT_DIR && page && !page.isClosed()) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, `${context.name.replace(/[^a-zA-Z0-9-]+/g, '-').slice(0, 140)}.png`), fullPage: true });
		traces.push({ name: context.name, requests, counts: { section: count('section'), topics: count('topics') }, errors, focus: await page.evaluate(() => ({ tag: document.activeElement.tagName, className: document.activeElement.className })), topicRegions: await region().count() });
		await writeFile(join(process.env.QA_SCREENSHOT_DIR, 'traces.json'), JSON.stringify(traces, null, 2) + '\n');
	}
	for (const waiter of waiters.values()) { waiter.cancel(); }
	waiters.clear();
	await page?.close();
	assert.deepEqual(errors, []);
});

function waitForRecord(kind, index = 0) {
	if (records[kind][index]) { return Promise.resolve(records[kind][index]); }
	const key = `${kind}:${index}`;
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => {
			waiters.delete(key);
			reject(new Error(`${kind} handler ${index} did not arrive`));
		}, 5000);
		waiters.set(key, {
			resolve(record) { clearTimeout(timer); waiters.delete(key); resolve(record); },
			cancel() { clearTimeout(timer); reject(new Error(`${key} was not delivered before teardown`)); },
		});
	});
}

async function finish(record, data, status = 200) {
	assert.equal(record.fulfillAttempts, 0, 'Each actual handler record is fulfilled exactly once');
	record.fulfillAttempts += 1;
	const received = page.waitForResponse(response => response.request() === record.route.request());
	await record.route.fulfill(status === 200
		? { json: { success: true, data } }
		: { status, json: { success: false, code: 'TEST_UNAVAILABLE', message: 'fixture detail must stay out of UI' } });
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function openHome(width) {
	await page.setViewportSize({ width, height: 900 });
	await page.goto(server.resolvedUrls.local[0]);
	await waitForRecord('section');
	await waitForRecord('topics');
}

async function retry(kind, key = 'Enter') {
	const delivered = waitForRecord(kind, count(kind));
	await retryButton(kind).focus();
	await page.keyboard.press(key);
	return delivered;
}

async function expectLayout() {
	assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
	assert.equal(await page.getByRole('main').count(), 1);
	assert.equal(await page.getByRole('main', { name: '블로그 홈', exact: true }).count(), 1);
	assert.equal(await page.getByText('fixture detail must stay out of UI', { exact: false }).count(), 0);
	if (await region().count()) {
		const titleId = await region().getAttribute('aria-labelledby');
		if (titleId) {
			assert.equal(await page.locator(`[id="${titleId}"]`).count(), 1);
			assert.ok((await page.locator(`[id="${titleId}"]`).textContent()).trim());
		} else {
			assert.ok((await region().getAttribute('aria-label'))?.trim());
		}
	}
}

async function navigate(path) {
	await page.evaluate(path => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push(path), path);
}

async function seed(width) {
	await openHome(width);
	await finish(await waitForRecord('section'), metadata('캐시된 소개'));
	await finish(await waitForRecord('topics'), [topic(3, '캐시된 주제')]);
	await navigate('/privacy');
	await page.getByRole('main', { name: '블로그 홈', exact: true }).waitFor({ state: 'hidden' });
	await navigate('/');
	await waitForRecord('section', 1);
	await waitForRecord('topics', 1);
}

for (const width of [1280, 390]) {
	test(`${width}: topics 503 stays visible after metadata succeeds and retries independently`, async context => {
		await openHome(width);
		await finish(await waitForRecord('section'), metadata('주제 모음'));
		await finish(await waitForRecord('topics'), null, 503);
		context.diagnostic(`after topics503: regions=${await region().count()}, alerts=${await page.getByRole('alert').count()}`);
		await feedback('topics').getByRole('alert').waitFor();
		const unrelated = requests.filter(path => !path.endsWith('/home/topics'));
		const pending = await retry('topics');
		assert.equal(await retryButton('topics').getAttribute('aria-disabled'), 'true');
		await page.keyboard.press('Enter');
		await page.keyboard.press('Space');
		await retryButton('topics').dblclick({ force: true });
		assert.deepEqual({ section: count('section'), topics: count('topics') }, { section: 1, topics: 2 });
		await finish(pending, [topic(1, '복구된 주제')]);
		await region().getByRole('heading', { name: '복구된 주제', exact: true }).waitFor();
		assert.equal(await region().getByRole('alert').count(), 0);
		assert.equal(await region().getByRole('heading', { name: '주제 모음', exact: true }).evaluate(element => document.activeElement === element), true);
		assert.deepEqual(requests.filter(path => !path.endsWith('/home/topics')), unrelated);
		await expectLayout();
	});

	test(`${width}: metadata 503 preserves pending topics and retries without a response barrier`, async context => {
		await openHome(width);
		await finish(await waitForRecord('section'), null, 503);
		context.diagnostic(`after metadata503: regions=${await region().count()}, alerts=${await page.getByRole('alert').count()}, topicsRequests=${count('topics')}`);
		await feedback('section').getByRole('alert').waitFor();
		assert.equal(await region().locator('.home-topic-card--skeleton').count(), 3);
		await expectLayout();
		const pending = await retry('section', 'Space');
		assert.equal(await retryButton('section').getAttribute('aria-disabled'), 'true');
		await page.keyboard.press('Enter');
		await page.keyboard.press('Space');
		await retryButton('section').dblclick({ force: true });
		assert.equal(count('section'), 2);
		await finish(pending, metadata('복구된 소개'));
		await region().getByRole('heading', { name: '복구된 소개', exact: true }).waitFor();
		assert.equal(await region().getByRole('heading', { name: '복구된 소개', exact: true }).evaluate(element => document.activeElement === element), true);
		assert.equal(await region().getAttribute('aria-busy'), 'true');
		assert.equal(count('topics'), 1);
		await finish(await waitForRecord('topics'), [topic(2, '늦게 온 주제')]);
		await region().getByRole('heading', { name: '늦게 온 주제', exact: true }).waitFor();
		assert.equal(await region().getAttribute('aria-busy'), 'false');
		assert.deepEqual({ section: count('section'), topics: count('topics') }, { section: 2, topics: 1 });
		await expectLayout();
	});

	test(`${width}: a successful empty topics response remains hidden and never forces focus`, async () => {
		await openHome(width);
		const outside = page.getByRole('button', { name: '메뉴', exact: true });
		await outside.focus();
		await finish(await waitForRecord('section'), metadata('주제 모음'));
		await finish(await waitForRecord('topics'), []);
		assert.equal(await region().count(), 0);
		assert.equal(await page.getByRole('alert').count(), 0);
		assert.equal(await outside.evaluate(element => document.activeElement === element), true);
		await expectLayout();
	});

	test(`${width}: topic retry completes while metadata is held and uses the stable main focus target`, async () => {
		await openHome(width);
		await finish(await waitForRecord('topics'), null, 503);
		await feedback('topics').getByRole('alert').waitFor();
		assert.equal(await region().getAttribute('aria-label'), '주제');
		const next = await retry('topics');
		await finish(next, [topic(4, '소개보다 먼저 온 주제')]);
		await region().getByRole('heading', { name: '소개보다 먼저 온 주제', exact: true }).waitFor();
		assert.equal(await page.getByRole('main', { name: '블로그 홈', exact: true }).evaluate(element => document.activeElement === element), true);
		assert.equal(await region().getAttribute('aria-busy'), 'true');
		assert.equal(count('section'), 1);
		await finish(await waitForRecord('section'), metadata('늦게 온 소개'));
		await expectLayout();
	});

	for (const kind of ['topics', 'section']) {
		test(`${width}: ${kind} cached empty data stays hidden during refresh and does not claim old visible content after failure`, async () => {
			await openHome(width);
			await finish(await waitForRecord('section'), {});
			await finish(await waitForRecord('topics'), []);
			assert.equal(await region().count(), 0);
			await navigate('/privacy');
			await page.getByRole('main', { name: '블로그 홈', exact: true }).waitFor({ state: 'hidden' });
			await navigate('/');
			await waitForRecord('section', 1);
			await waitForRecord('topics', 1);
			assert.equal(await region().count(), 0);
			await finish(await waitForRecord(kind, 1), null, 503);
			await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics', 1), kind === 'topics' ? {} : []);
			await feedback(kind).getByRole('alert').waitFor();
			assert.equal(await feedback(kind).getByText('이전에 불러온', { exact: false }).count(), 0);
			const next = await retry(kind);
			await finish(next, kind === 'topics' ? [] : {});
			assert.equal(await region().count(), 0);
			assert.equal(await page.getByRole('main', { name: '블로그 홈', exact: true }).evaluate(element => document.activeElement === element), true);
			await expectLayout();
		});

		test(`${width}: ${kind} retry to a normal empty section focuses the named main only after its own button disappears`, async () => {
			await openHome(width);
			await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics'), kind === 'topics' ? metadata('주제 모음') : []);
			await finish(await waitForRecord(kind), null, 503);
			await feedback(kind).getByRole('alert').waitFor();
			const next = await retry(kind);
			assert.equal(await retryButton(kind).evaluate(element => document.activeElement === element), true);
			await finish(next, kind === 'topics' ? [] : {});
			assert.equal(await region().count(), 0);
			assert.equal(await page.getByRole('main', { name: '블로그 홈', exact: true }).evaluate(element => document.activeElement === element), true);
			assert.deepEqual({ section: count('section'), topics: count('topics') }, kind === 'topics' ? { section: 1, topics: 2 } : { section: 2, topics: 1 });
			await expectLayout();
		});

		test(`${width}: ${kind} retry success preserves focus moved outside before completion`, async () => {
			await openHome(width);
			await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics'), kind === 'topics' ? metadata('주제 모음') : [topic(5, '유지할 주제')]);
			await finish(await waitForRecord(kind), null, 503);
			const next = await retry(kind);
			const outside = page.getByRole('button', { name: '메뉴', exact: true });
			await outside.focus();
			await finish(next, kind === 'topics' ? [] : metadata('복구된 소개'));
			assert.equal(await outside.evaluate(element => document.activeElement === element), true);
			await expectLayout();
		});

		test(`${width}: ${kind} cache refresh failure retains previous content and retries only that request`, async () => {
			await seed(width);
			await region().getByRole('heading', { name: '캐시된 소개', exact: true }).waitFor();
			await region().getByRole('heading', { name: '캐시된 주제', exact: true }).waitFor();
			assert.equal(await region().locator('.home-topic-card--skeleton').count(), 0);
			assert.equal(await region().getAttribute('aria-busy'), 'true');
			await finish(await waitForRecord(kind, 1), null, 503);
			const text = kind === 'topics' ? '주제 목록을 새로 불러오지 못했습니다. 이전에 불러온 주제를 표시합니다.'
				: '주제 소개를 새로 불러오지 못했습니다. 이전에 불러온 소개를 표시합니다.';
			await feedback(kind).getByText(text, { exact: true }).waitFor();
			const untouched = requests.filter(path => !path.endsWith(kind === 'topics' ? '/home/topics' : '/home/topic-section'));
			const next = await retry(kind);
			await region().getByRole('heading', { name: '캐시된 주제', exact: true }).waitFor();
			assert.equal(await region().locator('.home-topic-card--skeleton').count(), 0);
			await finish(next, kind === 'topics' ? [topic(6, '새 주제')] : metadata('새 소개'));
			assert.equal(await region().getByRole('alert').count(), 0);
			assert.equal(await region().getAttribute('aria-busy'), 'true');
			assert.deepEqual(requests.filter(path => !path.endsWith(kind === 'topics' ? '/home/topics' : '/home/topic-section')), untouched);
			await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics', 1), kind === 'topics' ? metadata('현재 소개') : [topic(7, '현재 주제')]);
			assert.equal(await region().getAttribute('aria-busy'), 'false');
			await expectLayout();
		});

		for (const stale of ['success', 'failure']) {
			test(`${width}: ${kind} stale ${stale} cannot change a remounted home or its cached result`, async () => {
				await openHome(width);
				const old = await waitForRecord(kind);
				await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics'), kind === 'topics' ? metadata('초기 소개') : [topic(8, '초기 주제')]);
				await navigate('/privacy');
				await page.getByRole('main', { name: '블로그 홈', exact: true }).waitFor({ state: 'hidden' });
				await navigate('/');
				const current = await waitForRecord(kind, 1);
				await waitForRecord(kind === 'topics' ? 'section' : 'topics', 1);
				if (stale === 'failure') {
					await finish(old, null, 503);
					assert.equal(await region().getByRole('alert').count(), 0);
					assert.equal(await region().getAttribute('aria-busy'), 'true');
				}
				await finish(current, kind === 'topics' ? [topic(9, '현재 주제')] : metadata('현재 소개'));
				await finish(await waitForRecord(kind === 'topics' ? 'section' : 'topics', 1), kind === 'topics' ? metadata('현재 소개') : [topic(9, '현재 주제')]);
				if (stale === 'success') { await finish(old, kind === 'topics' ? [topic(99, '이전 주제')] : metadata('이전 소개')); }
				await region().getByRole('heading', { name: '현재 주제', exact: true }).waitFor();
				await region().getByRole('heading', { name: '현재 소개', exact: true }).waitFor();
				assert.equal(await region().getByText(/이전 (주제|소개)/).count(), 0);
				assert.equal(await region().getByRole('alert').count(), 0);
				await navigate('/privacy');
				await page.getByRole('main', { name: '블로그 홈', exact: true }).waitFor({ state: 'hidden' });
				await navigate('/');
				await waitForRecord(kind, 2);
				await region().getByRole('heading', { name: '현재 주제', exact: true }).waitFor();
				await region().getByRole('heading', { name: '현재 소개', exact: true }).waitFor();
				await expectLayout();
			});
		}
	}

	test(`${width}: two errors and pending retries remain independent across another failure`, async () => {
		await openHome(width);
		await finish(await waitForRecord('section'), null, 503);
		await finish(await waitForRecord('topics'), null, 503);
		assert.equal(await region().getByRole('alert').count(), 2);
		const sectionRetry = await retry('section');
		const topicsRetry = await retry('topics');
		assert.equal(await retryButton('section').getAttribute('aria-disabled'), 'true');
		assert.equal(await retryButton('topics').getAttribute('aria-disabled'), 'true');
		await finish(sectionRetry, null, 503);
		assert.equal(await retryButton('section').getAttribute('aria-disabled'), 'false');
		assert.equal(await retryButton('topics').getAttribute('aria-disabled'), 'true');
		assert.equal(await region().getAttribute('aria-busy'), 'true');
		await finish(topicsRetry, [topic(10, '소개 실패와 무관한 주제')]);
		assert.equal(await region().getByRole('alert').count(), 1);
		await region().getByRole('heading', { name: '소개 실패와 무관한 주제', exact: true }).waitFor();
		assert.equal(await region().getByRole('heading', { name: '주제', exact: true }).evaluate(element => document.activeElement === element), true);
		assert.equal(await region().getAttribute('aria-busy'), 'false');
		await expectLayout();
	});

	for (const status of [200, 503]) {
		test(`${width}: completing an old retry after leaving home with HTTP${status} cannot move outside focus`, async () => {
			await openHome(width);
			await finish(await waitForRecord('section'), metadata('주제 모음'));
			await finish(await waitForRecord('topics'), null, 503);
			const oldRetry = await retry('topics');
			await navigate('/privacy');
			await page.getByRole('main', { name: '블로그 홈', exact: true }).waitFor({ state: 'hidden' });
			const outside = page.getByRole('button', { name: '메뉴', exact: true });
			await outside.focus();
			await finish(oldRetry, [], status);
			assert.equal(await outside.evaluate(element => document.activeElement === element), true);
			assert.equal(await page.getByRole('alert').count(), 0);
			assert.equal(new URL(page.url()).pathname, '/privacy');
		});
	}

	test(`${width}: blank metadata has a valid heading and skeleton accessibility stays unchanged`, async () => {
		await openHome(width);
		assert.equal(await region().getAttribute('aria-busy'), 'true');
		assert.equal(await region().getAttribute('aria-label'), '주제');
		assert.equal(await region().locator('.home-topic-grid').getAttribute('aria-hidden'), 'true');
		assert.equal(await region().locator('.home-topic-card--skeleton').count(), 3);
		assert.equal(await region().locator('.ui-skeleton').first().evaluate(element => getComputedStyle(element).animationName), 'none');
		const before = await region().locator('.home-topic-card').first().boundingBox();
		await finish(await waitForRecord('section'), { title: '   ' });
		await finish(await waitForRecord('topics'), [topic(11, '제목 없는 소개의 주제')]);
		await region().getByRole('heading', { name: '주제', exact: true }).waitFor();
		const after = await region().locator('.home-topic-card').first().boundingBox();
		assert.ok(Math.abs(before.width - after.width) < 1);
		assert.equal(await region().getAttribute('aria-busy'), 'false');
		await expectLayout();
	});
}
