import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, cacheDir, apiReachedServer = 0;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const targets = [
	{ page: 'comments', query: 'report', type: 'REPORT_RECEIVED', title: '댓글 상세', listError: '댓글 목록을 불러오지 못했습니다.' },
	{ page: 'guestbook', query: 'entry', type: 'GUESTBOOK_WRITTEN', title: '방명록 글 상세', listError: '방명록 목록을 불러오지 못했습니다.' },
];
const listData = { items: [], statusCounts: { all: 0, reported: 0 }, page: 0, size: 20, totalElements: 0, totalPages: 0 };

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-admin-notification-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	server.middlewares.use((request, response, next) => {
		if (!request.url.startsWith('/api/')) return next();
		apiReachedServer += 1;
		response.statusCode = 500;
		response.end('Unexpected API request outside browser fixtures');
	});
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => {
	await browser?.close(); await server?.close();
	if (cacheDir) await rm(cacheDir, { recursive: true, force: true });
	assert.equal(apiReachedServer, 0);
});

function gate() {
	let deliver;
	const ready = new Promise(resolve => { deliver = resolve; });
	return { ready, deliver };
}

async function usePage(target, { holdList = false, holdDetail = null }, run) {
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	const path = `/admin/${target.page}`;
	const listPath = `/api/v1/admin/blog/${target.page}`;
	const page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(6000);
	const list = gate(), previousDetail = gate();
	const detailIds = [], listQueries = [], writes = [], pageErrors = [];
	page.on('pageerror', error => pageErrors.push(error.message));
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url()), method = request.method();
		if (url.origin !== origin) return route.abort('blockedbyclient');
		if (!url.pathname.startsWith('/api/')) {
			return method === 'GET' || method === 'HEAD' ? route.continue() : route.abort('blockedbyclient');
		}
		if (method !== 'GET') {
			writes.push([method, url.pathname]);
			return method === 'PUT' && url.pathname.endsWith('/notifications/1/read') ? ok(route) : route.abort('blockedbyclient');
		}
		if (url.pathname === '/api/v1/admin/auth/me') return ok(route, { username: 'test-admin', role: 'ROLE_ADMIN' });
		if (url.pathname === '/api/v1/auth/me') return ok(route, { id: 1, nickname: '테스트 운영자', role: 'ROLE_ADMIN' });
		if (url.pathname.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test-fixture' });
		if (url.pathname.endsWith('/notifications/unread-count')) return ok(route, writes.length === 0 ? 1 : 0);
		if (url.pathname.endsWith('/notifications')) {
			return ok(route, [{ id: 1, type: target.type, actorNickname: '테스트 알림 작성자', preview: '테스트 알림',
				commentId: target.query === 'report' ? 73 : null, guestbookEntryId: target.query === 'entry' ? 73 : null,
				postId: target.query === 'report' ? 1 : null, unread: writes.length === 0 }]);
		}
		if (url.pathname === listPath) {
			listQueries.push(Object.fromEntries(url.searchParams));
			if (holdList && listQueries.length === 1) { list.deliver(route); return; }
			return ok(route, listData);
		}
		if (url.pathname.startsWith(`${listPath}/`)) {
			const id = Number(url.pathname.slice(listPath.length + 1));
			detailIds.push(id);
			if (id === holdDetail) { previousDetail.deliver(route); return; }
			return ok(route, detailData(target, id));
		}
		return ok(route);
	});
	const dialog = page.getByRole('dialog', { name: target.title, exact: true });
	const bell = () => page.locator('.notification-bell__trigger').click();
	const choose = () => page.locator('button.notification-bell__item').filter({ hasText: '테스트 알림 작성자' }).click();
	try {
		await run({ page, origin, path, listPath, dialog, bell, choose, list, previousDetail, detailIds, listQueries, writes });
		assert.deepEqual(pageErrors, []);
	} finally {
		await page.close();
	}
}

function detailData(target, id) {
	const entry = { id, content: `테스트 상세 ${id}`, nickname: '테스트 작성자' };
	return target.query === 'report'
		? { ...entry, postId: 1, postTitle: '테스트 글', memberRole: 'USER', reports: [], moderations: [], histories: [] }
		: { entry, reports: [], moderations: [] };
}

function routeTo(page, path) {
	return page.evaluate(value => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push(value), path);
}

async function waitForList(page, url, listPath) {
	const received = page.waitForResponse(response => new URL(response.url()).pathname === listPath);
	await page.goto(url);
	await (await received).finished();
}

for (const target of targets) {
	test(`${target.page}: an alert opens the detail on the already mounted list`, { timeout: 15000 }, async () => {
		await usePage(target, {}, async ({ page, origin, path, listPath, dialog, bell, choose, detailIds, listQueries, writes }) => {
			await waitForList(page, `${origin}${path}?status=REPORTED`, listPath);
			const read = page.waitForResponse(response => response.request().method() === 'PUT' && new URL(response.url()).pathname.endsWith('/notifications/1/read'));
			await bell(); await choose();
			await (await read).finished();
			await dialog.getByText('테스트 상세 73', { exact: true }).waitFor();
			await page.waitForURL(url => url.pathname === path && !url.searchParams.has(target.query));
			assert.deepEqual(detailIds, [73]);
			assert.deepEqual(listQueries.map(query => query.status), ['REPORTED']);
			assert.deepEqual(writes, [['PUT', '/api/v1/auth/me/notifications/1/read']]);
		});
	});

	test(`${target.page}: an alert opens before the list responds and stays open after a list 503`, { timeout: 15000 }, async () => {
		await usePage(target, { holdList: true }, async ({ page, origin, path, dialog, bell, choose, list, detailIds, listQueries }) => {
			await page.goto(`${origin}/privacy`);
			await bell(); await choose();
			const response = await list.ready;
			await dialog.getByText('테스트 상세 73', { exact: true }).waitFor();
			await page.waitForURL(url => url.pathname === path && !url.searchParams.has(target.query));
			assert.deepEqual(detailIds, [73]);
			assert.equal(listQueries.length, 1);
			await response.fulfill({ status: 503, json: { success: false, code: 'TEST_UNAVAILABLE', message: '테스트 목록 오류' } });
			await page.getByText(target.listError, { exact: true }).waitFor();
			assert.equal(await dialog.getByText('테스트 상세 73', { exact: true }).isVisible(), true);
			assert.deepEqual(detailIds, [73]);
		});
	});

	test(`${target.page}: newer detail and unrelated query/hash survive a late previous response`, { timeout: 15000 }, async () => {
		await usePage(target, { holdDetail: 73 }, async ({ page, origin, path, listPath, dialog, previousDetail, detailIds, listQueries }) => {
			await waitForList(page, `${origin}${path}?status=REPORTED`, listPath);
			const first = page.waitForRequest(request => new URL(request.url()).pathname === `${listPath}/73`);
			await routeTo(page, `${path}?status=REPORTED&keep=first&${target.query}=73#first`);
			await first;
			const held = await previousDetail.ready;
			await routeTo(page, `${path}?status=REPORTED&keep=latest&${target.query}=84#latest`);
			await dialog.getByText('테스트 상세 84', { exact: true }).waitFor();
			await page.waitForURL(url => url.pathname === path && !url.searchParams.has(target.query)
				&& url.searchParams.get('keep') === 'latest' && url.searchParams.get('status') === 'REPORTED' && url.hash === '#latest');
			const prior = page.waitForResponse(response => response.request() === held.request());
			await ok(held, detailData(target, 73)); await (await prior).finished();
			await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
			assert.equal(await dialog.getByText('테스트 상세 84', { exact: true }).isVisible(), true);
			assert.equal(await dialog.getByText('테스트 상세 73', { exact: true }).count(), 0);
			assert.deepEqual(detailIds, [73, 84]);
			assert.deepEqual(listQueries.map(query => query.status), ['REPORTED']);
		});
	});

	test(`${target.page}: invalid targets stay untouched and a later navigation wins query cleanup`, { timeout: 15000 }, async () => {
		await usePage(target, {}, async ({ page, origin, path, listPath, detailIds }) => {
			await waitForList(page, origin + path, listPath);
			const invalid = `${path}?${target.query}=73&${target.query}=84#invalid`;
			await routeTo(page, invalid);
			assert.equal(new URL(page.url()).pathname + new URL(page.url()).search + new URL(page.url()).hash, invalid);
			assert.deepEqual(detailIds, []);

			const failed = await page.evaluate(({ currentPath, query }) => {
				const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router;
				return new Promise((resolve, reject) => {
					const detach = router.afterEach((to, _from, failure) => {
						if (failure || to.path !== currentPath || to.query[query] !== '73') return;
						detach();
						router.push(`/privacy?${query}=84#latest`).then(result => resolve(Boolean(result)), reject);
					});
					router.push(`${currentPath}?${query}=73`).catch(reject);
				});
			}, { currentPath: path, query: target.query });
			assert.equal(failed, false);
			await page.waitForURL(url => url.pathname === '/privacy' && url.searchParams.get(target.query) === '84' && url.hash === '#latest');
			assert.ok(!detailIds.includes(84));
		});
	});
}
