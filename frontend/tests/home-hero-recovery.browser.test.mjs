import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, cacheDir, records, waiters, errors, requests;
const traces = [];
const hero = (title = '복구된 블로그 소개') => ({ title, subTitle: '가짜 개발 기록', intro: '가짜 소개 본문', heroImageUrl: '/images/hero-fixture.svg' });
const profile = { name: '가짜 블로그 주인', job: '가짜 개발자', intro: '프로필은 독립 조회', avatarImageUrl: '/images/avatar-fixture.svg' };
const region = () => page.locator('.home-hero');
const count = kind => records[kind].length;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-home-hero-098-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	records = { hero: [], profile: [] }; waiters = new Map(); errors = []; requests = [];
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block', reducedMotion: 'reduce' });
	page.setDefaultTimeout(5000); page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url());
		if (url.origin !== origin) { return route.abort('blockedbyclient'); }
		if (url.pathname.startsWith('/images/') && url.pathname.endsWith('-fixture.svg')) {
			return route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="96" height="96" fill="#bccaad"/></svg>' });
		}
		if (!url.pathname.startsWith('/api/')) { return route.continue(); }
		requests.push({ path: url.pathname + url.search, method: request.method() });
		const kind = url.pathname === '/api/v1/blog/home/hero' ? 'hero' : url.pathname === '/api/v1/blog/profile' ? 'profile' : null;
		if (kind) {
			const record = { route, kind, index: count(kind), fulfilled: 0 };
			records[kind].push(record); waiters.get(`${kind}:${record.index}`)?.deliver(record); return;
		}
		if (url.pathname === '/api/v1/blog/home/topic-section') { return ok(route, { title: '독립 주제 소개' }); }
		if (url.pathname === '/api/v1/blog/home/topics') { return ok(route, [{ id: 1, title: '독립 주제 카드', label: '기록', description: '별도 주제' }]); }
		if (url.pathname === '/api/v1/blog/home/posts') { return ok(route, [{ id: 1, title: '독립 게시글', categoryName: '기록', views: 1 }]); }
		if (url.pathname.endsWith('/unread-count')) { return ok(route, 0); }
		return ok(route);
	});
});
afterEach(async context => {
	const snapshot = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
		focus: { tag: document.activeElement.tagName, className: document.activeElement.className }, title: document.querySelector('.home-hero__headline')?.textContent ?? null }));
	if (process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		traces.push({ name: context.name, ...snapshot, errors, requests, counts: { hero: count('hero'), profile: count('profile') } });
		await writeFile(join(process.env.QA_SCREENSHOT_DIR, 'traces.json'), JSON.stringify(traces, null, 2) + '\n');
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, context.name.replace(/[^a-zA-Z0-9-]+/g, '-').slice(0, 145) + '.png'), fullPage: true });
	}
	for (const waiter of waiters.values()) { waiter.cancel(); }
	waiters.clear(); await page.close(); assert.deepEqual(errors, []); assert.equal(snapshot.width, snapshot.scrollWidth);
});
function take(kind, index = 0) {
	if (records[kind][index]) { return Promise.resolve(records[kind][index]); }
	return new Promise((resolve, reject) => {
		const key = `${kind}:${index}`, timer = setTimeout(() => { waiters.delete(key); reject(new Error(`Actual ${key} handler did not arrive`)); }, 5000);
		waiters.set(key, { deliver(record) { clearTimeout(timer); waiters.delete(key); resolve(record); }, cancel() { clearTimeout(timer); resolve(null); } });
	});
}
async function finish(record, data, status = 200) {
	assert.ok(record); assert.equal(record.fulfilled, 0); record.fulfilled += 1;
	const received = page.waitForResponse(response => response.request() === record.route.request());
	await record.route.fulfill(status === 200 ? { json: { success: true, data } }
		: { status, json: { success: false, code: 'TEST_UNAVAILABLE', message: 'private fixture detail must stay out of UI' } });
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}
async function open(width) {
	await page.setViewportSize({ width, height: 900 }); await page.goto(server.resolvedUrls.local[0]); await take('hero'); await take('profile');
}
async function navigate(path) { await page.evaluate(path => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push(path), path); }
async function retry() {
	const delivered = take('hero', count('hero'));
	await region().getByRole('button', { name: '소개 다시 시도', exact: true }).click(); return delivered;
}

for (const width of [1280, 390]) {
	test(`${width}: cold503 exposes a retry while profile stays independently pending`, async context => {
		await open(width); await finish(await take('hero'), null, 503);
		context.diagnostic(`alerts=${await region().getByRole('alert').count()}, retry=${await region().getByRole('button').count()}, title=${await region().locator('h1').textContent().catch(() => '')}`);
		await region().getByRole('alert').waitFor();
		assert.equal(await region().getAttribute('aria-busy'), 'false');
		assert.equal(await region().locator('.home-hero__profile').getAttribute('aria-busy'), 'true');
		await page.getByRole('heading', { name: '독립 주제 카드', exact: true }).waitFor();
		assert.equal(await page.getByRole('heading', { name: '독립 게시글', exact: true }).count(), 2);
		assert.equal(await region().getByText('private fixture detail must stay out of UI', { exact: false }).count(), 0);
		const unrelated = requests.filter(r => !r.path.startsWith('/api/v1/blog/home/hero'));
		const button = region().getByRole('button', { name: '소개 다시 시도', exact: true }); await button.focus();
		const delivered = take('hero', 1); await page.keyboard.press('Enter'); const held = await delivered;
		assert.equal(await button.getAttribute('aria-disabled'), 'true');
		assert.equal(await region().getAttribute('aria-busy'), 'false');
		await page.keyboard.press('Enter'); await page.keyboard.press('Space'); await button.dblclick({ force: true });
		assert.equal(count('hero'), 2); await finish(held, hero());
		assert.equal(await region().getByRole('heading', { name: hero().title, exact: true }).evaluate(el => document.activeElement === el), true);
		assert.equal(await region().getByRole('button').count(), 0); assert.equal(count('hero'), 2);
		assert.deepEqual(requests.filter(r => !r.path.startsWith('/api/v1/blog/home/hero')), unrelated);
		await finish(await take('profile'), profile); await region().getByText(profile.name, { exact: true }).waitFor();
	});
	test(`${width}: a normal empty DTO has no error retry or forced focus`, async () => {
		await open(width); const outside = page.getByRole('button', { name: '메뉴', exact: true }); await outside.focus();
		await finish(await take('hero'), {}); await finish(await take('profile'), profile);
		assert.equal(await region().getByRole('alert').count(), 0); assert.equal(await region().getByRole('button').count(), 0);
		assert.equal(await outside.evaluate(el => document.activeElement === el), true);
	});
	for (const empty of [false, true]) {
		test(`${width}: retry success empty=${empty} keeps only its own focus and the independent profile`, async () => {
			await open(width); await finish(await take('profile'), profile);
			await region().getByText(profile.name, { exact: true }).waitFor(); assert.equal(await region().locator('.home-hero__copy-skeleton').count(), 1);
			await finish(await take('hero'), null, 503); const button = region().getByRole('button', { name: '소개 다시 시도', exact: true }); await button.focus();
			const delivered = take('hero', 1); await page.keyboard.press('Space'); const held = await delivered;
			await finish(held, empty ? {} : hero());
			const target = empty ? page.getByRole('main', { name: '블로그 홈', exact: true }) : region().getByRole('heading', { name: hero().title, exact: true });
			assert.equal(await target.evaluate(el => document.activeElement === el), true); assert.equal(count('profile'), 1);
			await region().getByText(profile.name, { exact: true }).waitFor();
		});
	}
	test(`${width}: failed retry retains its button and later success does not steal outside focus`, async () => {
		await open(width); await finish(await take('profile'), profile); await finish(await take('hero'), null, 503);
		const held = await retry(); const button = region().getByRole('button', { name: '소개 다시 시도', exact: true });
		await finish(held, null, 503); assert.equal(await button.evaluate(el => document.activeElement === el), true);
		const next = await retry(), outside = page.getByRole('button', { name: '메뉴', exact: true }); await outside.focus(); await finish(next, hero());
		assert.equal(await outside.evaluate(el => document.activeElement === el), true); assert.equal(count('hero'), 3);
	});
	for (const empty of [false, true]) {
		test(`${width}: cached empty=${empty} refresh failure retains content and retries only hero`, async () => {
			await open(width); await finish(await take('profile'), profile); await finish(await take('hero'), empty ? {} : hero('캐시된 소개'));
			await navigate('/privacy'); await navigate('/'); const refresh = await take('hero', 1);
			assert.equal(await region().locator('.home-hero__copy-skeleton').count(), 0);
			if (!empty) { await region().getByRole('heading', { name: '캐시된 소개', exact: true }).waitFor(); }
			await finish(refresh, null, 503); await region().getByRole('alert').waitFor();
			assert.equal((await region().getByRole('alert').textContent()).includes('이전에 불러온'), !empty);
			if (!empty) { assert.equal(await region().locator('.home-hero__image').getAttribute('src'), hero().heroImageUrl); }
			const unrelated = requests.filter(r => !r.path.startsWith('/api/v1/blog/home/hero')), held = await retry();
			if (!empty) { await region().getByRole('heading', { name: '캐시된 소개', exact: true }).waitFor(); }
			await finish(held, hero()); assert.equal(await region().getByRole('alert').count(), 0);
			assert.deepEqual(requests.filter(r => !r.path.startsWith('/api/v1/blog/home/hero')), unrelated);
		});
	}
	for (const status of [200, 503]) {
		test(`${width}: late HTTP${status} from the previous home cannot change current content or cache`, async () => {
			await open(width); await finish(await take('profile'), profile); const old = await take('hero');
			await navigate('/privacy'); await navigate('/'); const current = await take('hero', 1); await finish(current, hero('현재 화면 소개'));
			await finish(old, hero('이전 화면 소개'), status); await region().getByRole('heading', { name: '현재 화면 소개', exact: true }).waitFor();
			assert.equal(await region().getByRole('alert').count(), 0);
			await navigate('/privacy'); await navigate('/'); await take('hero', 2);
			await region().getByRole('heading', { name: '현재 화면 소개', exact: true }).waitFor();
		});
		test(`${width}: retry HTTP${status} after unmount leaves the next route and its focus alone`, async () => {
			await open(width); await finish(await take('profile'), profile); await finish(await take('hero'), null, 503); const old = await retry();
			await navigate('/privacy'); const outside = page.locator('.public-footer').getByRole('link', { name: '서비스 이용약관', exact: true }); await outside.focus();
			await finish(old, hero(), status); assert.equal(new URL(page.url()).pathname, '/privacy');
			assert.equal(await outside.evaluate(el => document.activeElement === el), true); assert.equal(await region().count(), 0);
		});
	}
	test(`${width}: independent skeletons reserve media space and support reduced motion`, async () => {
		await open(width);
		assert.equal(await region().getAttribute('aria-busy'), 'true');
		assert.equal(await region().locator('.home-hero__copy-skeleton').getAttribute('aria-hidden'), 'true');
		assert.equal(await region().locator('.home-hero__media').getAttribute('aria-hidden'), 'true');
		const before = await region().locator('.home-hero__media').boundingBox();
		assert.ok(before.height > 0); assert.equal(await region().locator('.home-hero__media').evaluate(el => getComputedStyle(el).animationName), 'none');
		await finish(await take('hero'), hero()); await region().getByRole('heading', { name: hero().title, exact: true }).waitFor();
		assert.equal(await region().locator('.home-hero__profile').getAttribute('aria-busy'), 'true');
		await finish(await take('profile'), profile); assert.equal(await region().getAttribute('aria-busy'), 'false');
		const after = await region().locator('.home-hero__media').boundingBox();
		assert.equal(after.width, before.width);
		// 실제 이미지의 비율에 따라 더 높아질 수 있지만 이미 확보한 공간은 줄지 않는다.
		assert.ok(after.height >= before.height);
	});
}
