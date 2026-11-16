import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, member, errors, reads, writes, waiters, logoutFails;
const accountA = { id: 7, nickname: '테스트 계정 A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '테스트 계정 B', role: 'ROLE_USER' };
const entry = (id, content, fields = {}) => ({
	id, nickname: '테스트 작성자', content, secret: true, readable: true, mine: true,
	createdAt: '2026-01-01T12:00:00', ...fields,
});
const pageOf = items => ({ items, page: 0, totalElements: items.length, totalPages: 1, maxLength: 500 });
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const guestbook = () => page.locator('.guestbook');
const draft = () => guestbook().locator('.comment-form__input');
const visit = () => page.goto(`${server.resolvedUrls.local[0]}guestbook`);

before(async () => {
	const port = Number(process.env.GUESTBOOK_TEST_PORT ?? 0);
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		cacheDir: 'node_modules/.vite-guestbook-session', plugins: [vue()],
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
	member = accountA; errors = []; reads = []; writes = []; waiters = new Map(); logoutFails = false;
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	// abort를 무시하고 완료되는 통신에서도 이전 계정의 응답을 차단하는지 확인한다.
	await page.addInitScript(() => {
		const original = window.fetch;
		window.fetch = (input, init) => String(input).startsWith('/api/v1/blog/guestbook?')
			? original(input, { ...init, signal: undefined }) : original(input, init);
	});
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const url = new URL(route.request().url());
		if (url.origin !== origin) {
			return route.abort('blockedbyclient');
		}
		if (url.pathname === '/api/v1/auth/me') {
			return ok(route, member);
		}
		if (url.pathname === '/api/v1/auth/logout') {
			if (logoutFails) {
				return route.fulfill({ status: 500, json: { success: false, code: 'LOGOUT_FAILED' } });
			}
			member = null;
			return ok(route);
		}
		if (url.pathname.endsWith('/csrf')) {
			return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-test-token' });
		}
		if (url.pathname === '/api/v1/blog/guestbook') {
			const requests = route.request().method() === 'GET' ? reads : writes;
			const key = `${requests === reads ? 'read' : 'write'}:${requests.length}`;
			requests.push(route);
			waiters.get(key)?.(route);
			return;
		}
		if (url.pathname.startsWith('/api/v1/')) {
			return ok(route, []);
		}
		return route.continue();
	});
});
afterEach(async context => {
	if (page && process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 100)}.png`, fullPage: true });
	}
	await page?.close();
	assert.deepEqual(errors, []);
});

function waitForRequest(kind, index = 0) {
	const requests = kind === 'read' ? reads : writes;
	if (requests[index]) {
		return Promise.resolve(requests[index]);
	}
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${kind} ${index} was not sent`)), 5000);
		waiters.set(`${kind}:${index}`, route => { clearTimeout(timer); resolve(route); });
	});
}

async function finish(route, data, status = 200) {
	const received = page.waitForResponse(response => response.url() === route.request().url()
		&& response.request().method() === route.request().method());
	await route.fulfill({ status, json: status === 200 ? { success: true, data } : { success: false, code: 'REQUEST_FAILED' } });
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function changeAccount(next) {
	member = next;
	await page.evaluate(async () => {
		const auth = await import('/src/features/member/data/memberAuthStore.js');
		await auth.refreshMemberSession();
	});
}

test('logout during the initial list request cannot display the previous readable secret response', async () => {
	await visit();
	const old = await waitForRequest('read');
	await guestbook().getByRole('button', { name: '로그아웃', exact: true }).click();
	await guestbook().getByRole('button', { name: '로그인하고 남기기' }).waitFor();
	await finish(old, pageOf([entry(1, '이전 계정의 테스트 비밀 본문')]));
	assert.equal(await guestbook().getByText('이전 계정의 테스트 비밀 본문', { exact: true }).count(), 0);
	await finish(await waitForRequest('read', 1), pageOf([entry(1, null, { readable: false, mine: false })]));
	await guestbook().getByText('비밀글입니다. 쓴 사람과 운영자만 볼 수 있습니다.', { exact: true }).waitFor();
});

test('account replacement during the initial list request cannot apply the previous account response', async () => {
	await visit();
	const old = await waitForRequest('read');
	await changeAccount(accountB);
	await finish(old, pageOf([entry(1, 'A 전용 테스트 본문')]));
	assert.equal(await guestbook().getByText('A 전용 테스트 본문', { exact: true }).count(), 0);
	await finish(await waitForRequest('read', 1), pageOf([entry(2, 'B 전용 테스트 본문')]));
	await guestbook().getByText('B 전용 테스트 본문', { exact: true }).waitFor();
});

test('already readable secrets disappear immediately on logout even when the next list fails', async () => {
	await visit();
	await finish(await waitForRequest('read'), pageOf([entry(1, 'A 전용 테스트 본문')]));
	await draft().fill('A의 테스트 초안');
	await guestbook().getByRole('button', { name: '로그아웃', exact: true }).click();
	await guestbook().getByRole('button', { name: '로그인하고 남기기' }).waitFor();
	assert.equal(await guestbook().getByText('A 전용 테스트 본문', { exact: true }).count(), 0);
	await finish(await waitForRequest('read', 1), null, 500);
	await guestbook().getByRole('alert').filter({ hasText: '방명록을 불러오지 못했습니다' }).waitFor();
	assert.equal(await guestbook().getByText('A 전용 테스트 본문', { exact: true }).count(), 0);
});

test('a failed logout preserves the current account secret, draft and edit form', async () => {
	logoutFails = true;
	await visit();
	await finish(await waitForRequest('read'), pageOf([entry(1, 'A 전용 테스트 본문')]));
	await draft().fill('남겨야 하는 테스트 초안');
	await guestbook().getByLabel('비밀글', { exact: true }).check();
	await page.locator('#guestbook-1').getByRole('button', { name: '수정', exact: true }).click();
	await page.getByLabel('방명록 고치기').fill('편집 중인 테스트 초안');
	await guestbook().getByRole('button', { name: '로그아웃', exact: true }).click();
	await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
	assert.equal(await draft().inputValue(), '남겨야 하는 테스트 초안');
	assert.equal(await page.getByLabel('방명록 고치기').inputValue(), '편집 중인 테스트 초안');
	assert.equal(reads.length, 1);
});

test('an old create response cannot clear a replacement account draft', async () => {
	await visit();
	await finish(await waitForRequest('read'), pageOf([]));
	await draft().fill('A의 제출할 테스트 초안');
	await guestbook().getByRole('button', { name: '등록', exact: true }).click();
	const old = await waitForRequest('write');
	await changeAccount(accountB);
	await draft().fill('B의 새 테스트 초안');
	await finish(old, null);
	assert.equal(await draft().inputValue(), 'B의 새 테스트 초안');
});

test('A to B to A keeps only the newest list while older success and failure arrive', async () => {
	await visit();
	const oldA = await waitForRequest('read');
	await changeAccount(accountB);
	const oldB = await waitForRequest('read', 1);
	await changeAccount(accountA);
	const currentA = await waitForRequest('read', 2);
	await finish(currentA, pageOf([entry(3, '현재 A 테스트 본문')]));
	await finish(oldA, pageOf([entry(1, '이전 A 테스트 본문')]));
	await finish(oldB, null, 500);
	await guestbook().getByText('현재 A 테스트 본문', { exact: true }).waitFor();
	assert.equal(await guestbook().getByText('이전 A 테스트 본문', { exact: true }).count(), 0);
	assert.equal(await guestbook().getByRole('alert').count(), 0);
	assert.equal(await guestbook().getAttribute('aria-busy'), 'false');
});

for (const width of [1280, 390]) {
	test(`${width}px account change clears secret content and drafts while the new list is delayed`, async () => {
		await page.setViewportSize({ width, height: 900 });
		await visit();
		await finish(await waitForRequest('read'), pageOf([entry(1, 'A 전용 테스트 본문')]));
		await draft().fill('A의 테스트 비공개 초안');
		await guestbook().getByLabel('비밀글', { exact: true }).check();
		await changeAccount(accountB);
		const current = await waitForRequest('read', 1);
		assert.equal(await guestbook().getByText('A 전용 테스트 본문', { exact: true }).count(), 0);
		assert.equal(await draft().inputValue(), '');
		assert.equal(await guestbook().getByLabel('비밀글', { exact: true }).isChecked(), false);
		assert.equal(await guestbook().getAttribute('aria-busy'), 'true');
		assert.equal(await guestbook().locator('.comments__row-skeleton').count(), 3);
		assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
		await finish(current, null, 500);
		await guestbook().getByRole('alert').filter({ hasText: '방명록을 불러오지 못했습니다' }).waitFor();
		assert.equal(await guestbook().getByText('A 전용 테스트 본문', { exact: true }).count(), 0);
	});
}

test('an unmounted write response cannot clear a remounted account draft or show a success toast', async () => {
	await visit();
	await finish(await waitForRequest('read'), pageOf([]));
	await draft().fill('A의 이전 테스트 초안');
	await guestbook().getByRole('button', { name: '등록', exact: true }).click();
	const old = await waitForRequest('write');
	await page.getByRole('link', { name: '개인정보처리방침', exact: true }).click();
	await page.waitForURL('**/privacy');
	await guestbook().waitFor({ state: 'hidden' });
	await changeAccount(accountB);
	await page.goBack();
	await finish(await waitForRequest('read', 1), pageOf([]));
	await draft().fill('새 화면의 테스트 초안');
	await finish(old, null);
	assert.equal(await draft().inputValue(), '새 화면의 테스트 초안');
	assert.equal(await page.getByText('방명록을 남겼습니다.', { exact: true }).count(), 0);
	assert.equal(reads.length, 2);
});
