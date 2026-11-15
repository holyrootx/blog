import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, member, requests, errors, failSave, failPost, startHandler, cacheDir;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const saves = () => requests.filter(request => request.method === 'PUT' && request.path.endsWith('/active-time'));
const starts = () => requests.filter(request => request.method === 'POST' && request.path.endsWith('/active-time'));

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-active-time-test-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	member = null; requests = []; errors = []; failSave = false; failPost = false;
	startHandler = route => ok(route, { token: `page-${starts().length}` });
	page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
	page.setDefaultTimeout(6000);
	page.on('pageerror', error => errors.push(error.message));
	await page.clock.install({ time: new Date('2026-01-01T00:00:00Z') });
	// 페이지 측정이 시작되기 전에 시계를 멈춰 실행 속도에 따른 시각 경합을 없앤다.
	await page.clock.pauseAt(new Date('2026-01-01T01:00:00Z'));
	await page.addInitScript(() => {
		// 응답 도착뿐 아니라 앱이 JSON 본문을 읽고 처리한 뒤에 가상 시계를 진행한다.
		window.apiRequests = [];
		const fetch = window.fetch.bind(window);
		window.fetch = async (input, options) => {
			if (!new URL(input, location.href).pathname.startsWith('/api/v1/')) { return fetch(input, options); }
			const entry = { finished: false };
			window.apiRequests.push(entry);
			try {
				const response = await fetch(input, options);
				const json = response.json.bind(response);
				response.json = async () => {
					try { return await json(); }
					finally { entry.finished = true; }
				};
				return response;
			} catch (error) {
				entry.finished = true;
				throw error;
			}
		};
		window.measurementVisible = true;
		Object.defineProperty(document, 'visibilityState', { get: () => window.measurementVisible ? 'visible' : 'hidden' });
		document.hasFocus = () => window.measurementVisible;
	});
	await page.route('**/api/v1/**', route => {
		const request = route.request();
		const path = new URL(request.url()).pathname;
		requests.push({ path, method: request.method(), body: request.postData() ? request.postDataJSON() : null,
			headers: request.headers() });
		if (path === '/api/v1/auth/me') { return ok(route, member); }
		if (path === '/api/v1/auth/csrf') { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'csrf-test' }); }
		if (path.endsWith('/active-time')) {
			if (request.method() === 'POST') { return startHandler(route); }
			return failSave ? route.fulfill({ status: 500, json: { success: false } }) : ok(route);
		}
		if (/\/blog\/posts\/\d+$/.test(path)) {
			if (failPost) { return route.fulfill({ status: 500, json: { success: false } }); }
			const id = Number(path.split('/').pop());
			return ok(route, { id, title: `글 ${id}`, excerpt: '요약', content: '읽는 본문입니다.\n\n다음 문단입니다.',
				categoryId: 1, categoryName: '개발', publishedAt: '2026-11-14T09:00:00', views: 1 });
		}
		if (path.endsWith('/adjacent')) { return ok(route, { nextPost: { id: 2, title: '글 2' } }); }
		return ok(route);
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function waitForApiIdle() {
	// 별도 브라우저 작업에서 확인하므로 JSON을 받은 앱의 후속 마이크로태스크도 끝난다.
	await page.waitForFunction(() => window.apiRequests.every(request => request.finished));
}

async function openPost({ waitForRequests = true } = {}) {
	await page.goto(`${server.resolvedUrls.local[0]}posts/1`);
	await page.getByRole('heading', { name: '글 1', exact: true }).waitFor();
	if (waitForRequests) { await waitForApiIdle(); }
}

async function advance(milliseconds) {
	await page.clock.fastForward(milliseconds);
	await waitForApiIdle();
}

async function visible(value) {
	await page.evaluate(value => {
		window.measurementVisible = value;
		document.dispatchEvent(new Event('visibilitychange'));
	}, value);
	await waitForApiIdle();
}

test('anonymous foreground measurement uses CSRF, pauses in background, and resumes', async () => {
	await openPost();
	assert.equal(starts().length, 1);
	await advance(16_000);
	assert.ok(saves().at(-1).body.activeTimeMillis >= 15_000);
	assert.equal(saves().at(-1).headers['x-csrf-token'], 'csrf-test');
	assert.deepEqual(Object.keys(saves().at(-1).body).sort(), ['activeTimeMillis', 'token']);
	await visible(false);
	const before = saves().at(-1).body.activeTimeMillis;
	const count = saves().length;
	await advance(90_000);
	assert.equal(saves().length, count);
	await visible(true);
	await advance(16_000);
	assert.ok(saves().at(-1).body.activeTimeMillis >= before + 15_000);
	assert.ok(saves().at(-1).body.activeTimeMillis < before + 17_000);
});

for (const delay of [0, 250]) test(`a long idle interval contributes at most one minute until the next interaction (start delay ${delay}ms)`, async () => {
	startHandler = async route => {
		// 응답 지연을 주입해 40ms 고정 대기로 재현한 경합을 계속 검사한다.
		if (delay) { await new Promise(resolve => setTimeout(resolve, delay)); }
		return ok(route, { token: 'idle-page' });
	};
	await openPost();
	await advance(180_000);
	assert.equal(saves().at(-1).body.activeTimeMillis, 60_000);
	const count = saves().length;
	await advance(90_000);
	assert.equal(saves().length, count);
	await page.evaluate(() => window.dispatchEvent(new Event('pointerdown')));
	await advance(16_000);
	assert.ok(saves().at(-1).body.activeTimeMillis >= 75_000);
	assert.ok(saves().at(-1).body.activeTimeMillis < 77_000);
});

test('failed transmission retries the cumulative value and navigation uses a new token', async () => {
	failSave = true;
	await openPost();
	await advance(16_000);
	const first = saves().at(-1);
	assert.ok(first.body.activeTimeMillis >= 15_000);
	failSave = false;
	await advance(16_000);
	assert.equal(saves().at(-1).body.token, first.body.token);
	assert.ok(saves().at(-1).body.activeTimeMillis >= 30_000);
	await page.locator('a[href="/posts/2"]').first().click();
	await page.getByRole('heading', { name: '글 2', exact: true }).waitFor();
	await waitForApiIdle();
	await advance(16_000);
	assert.equal(saves().at(-1).path, '/api/v1/blog/posts/2/active-time');
	assert.notEqual(saves().at(-1).body.token, first.body.token);
	assert.ok(saves().at(-1).body.activeTimeMillis < 17_000);
});

test('an old account start response is discarded after sign out', { timeout: 6000 }, async () => {
	member = { id: 7, nickname: '독자', role: 'USER' };
	let held;
	let received;
	const started = new Promise(resolve => { received = resolve; });
	startHandler = route => { held = route; received(); };
	await openPost({ waitForRequests: false });
	await started;
	assert.ok(held);
	startHandler = route => ok(route);
	await page.evaluate(async () => {
		const { clearMemberSession } = await import('/src/features/member/data/memberAuthStore.js');
		clearMemberSession();
	});
	await ok(held, { token: 'old-account' });
	await waitForApiIdle();
	await advance(30_000);
	assert.equal(saves().length, 0);
});

test('administrator reading does not start background telemetry', async () => {
	member = { id: 1, nickname: '관리자', role: 'ADMIN' };
	await openPost();
	await advance(60_000);
	assert.equal(starts().length, 0);
	assert.equal(saves().length, 0);
});

test('failed post loading has no measurement and still shows its retry button', async () => {
	failPost = true;
	await page.goto(`${server.resolvedUrls.local[0]}posts/1`);
	await page.getByRole('heading', { name: '글을 불러오지 못했습니다' }).waitFor();
	await advance(60_000);
	assert.equal(starts().length, 0);
	assert.equal(await page.getByRole('button', { name: '다시 시도', exact: true }).count(), 1);
});
