import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, cacheDir, records, waiters, holds, requests, errors;
let serverMember, pending, pendingStatus;
const traces = [];
const member = (id = 7) => ({ id, nickname: `가짜 회원 ${id}`, role: 'ROLE_USER' });
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const keyOf = (kind, index) => `${kind}:${index}`;
const count = kind => (records[kind] ?? []).length;

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-oauth-lifecycle-093-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		cacheDir, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});

after(async () => {
	await browser?.close();
	await server?.close();
	await rm(cacheDir, { recursive: true, force: true });
});

beforeEach(async () => {
	records = {}; waiters = new Map(); holds = new Set(); requests = []; errors = [];
	serverMember = null;
	pending = { loginState: 'SIGNUP_REQUIRED', suggestedNickname: '가짜 제공자 이름' };
	pendingStatus = 200;
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const request = route.request(), path = new URL(request.url()).pathname;
		if (new URL(request.url()).origin !== origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/')) { return route.continue(); }
		requests.push({ method: request.method(), path });
		const kind = path === '/api/v1/auth/me' ? 'me'
			: path === '/api/v1/auth/csrf' ? 'csrf'
				: path.startsWith('/api/v1/auth/oauth/') ? path.split('/').at(-1) : null;
		if (kind) {
			const record = { route, kind, index: count(kind), fulfillAttempts: 0, method: request.method() };
			(records[kind] ??= []).push(record);
			waiters.get(keyOf(kind, record.index))?.resolve(record);
			if (holds.has(keyOf(kind, record.index))) { return; }
			record.fulfillAttempts += 1;
			if (kind === 'me') { return ok(route, serverMember); }
			if (kind === 'csrf') { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-csrf-token' }); }
			if (kind === 'pending') {
				return pendingStatus === 200 ? ok(route, pending)
					: route.fulfill({ status: pendingStatus, json: { success: false, code: pendingStatus === 401 ? 'OAUTH_SESSION_EXPIRED' : 'INTERNAL_SERVER_ERROR', message: '가짜 가입 조회 오류' } });
			}
			return ok(route, member());
		}
		if (path.endsWith('/unread-count')) { return ok(route, 0); }
		if (path === '/api/v1/blog/profile') { return ok(route, { name: '가짜 블로그' }); }
		if (path === '/api/v1/blog/home/posts' || path === '/api/v1/blog/home/topics') { return ok(route, []); }
		return ok(route);
	});
});

afterEach(async context => {
	if (process.env.QA_SCREENSHOT_DIR && !page.isClosed()) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		const snapshot = await state();
		traces.push({ name: context.name, ...snapshot, errors, requests,
			records: Object.fromEntries(Object.entries(records).map(([key, value]) => [key, value.map(r => ({ index: r.index, fulfillAttempts: r.fulfillAttempts, method: r.method }))])) });
		await writeFile(join(process.env.QA_SCREENSHOT_DIR, 'traces.json'), JSON.stringify(traces, null, 2) + '\n');
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, context.name.replace(/[^a-zA-Z0-9-]+/g, '-').slice(0, 150) + '.png') });
	}
	for (const waiter of waiters.values()) { waiter.cancel(); }
	waiters.clear();
	await page.close();
	assert.deepEqual(errors, []);
});

function waitForRecord(kind, index = 0) {
	if (records[kind]?.[index]) { return Promise.resolve(records[kind][index]); }
	const key = keyOf(kind, index);
	return new Promise((resolve, reject) => {
		const timer = setTimeout(() => { waiters.delete(key); reject(new Error(`Missing handler ${key}`)); }, 5000);
		waiters.set(key, {
			resolve(record) { clearTimeout(timer); waiters.delete(key); resolve(record); },
			cancel() { clearTimeout(timer); reject(new Error(`Undelivered handler ${key}`)); },
		});
	});
}

async function finish(record, data, status = 200, code = 'INTERNAL_SERVER_ERROR') {
	assert.equal(record.fulfillAttempts, 0);
	record.fulfillAttempts += 1;
	const received = page.waitForResponse(response => response.request() === record.route.request());
	await record.route.fulfill(status === 200 ? { json: { success: true, data } }
		: { status, json: { success: false, code, message: '가짜 이전 요청 오류' } });
	await (await received).finished();
	await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function open(width, result = 'success', cb = 'first') {
	await page.setViewportSize({ width, height: 900 });
	await page.goto(`${server.resolvedUrls.local[0]}oauth/callback?result=${result}&_cb=${cb}`);
}

async function navigate(path) {
	await page.evaluate(path => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push(path), path);
}

async function remember(path = '/privacy?new-flow') {
	await page.evaluate(async path => (await import('/src/features/member/data/memberReturnPath.js')).rememberReturnPath(path), path);
}

async function switchMember(id = 8, advance = true) {
	serverMember = id === null ? null : member(id);
	await page.evaluate(async advance => {
		if (advance) { (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration(); }
		await (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession();
	}, advance);
}

async function state() {
	return page.evaluate(async () => {
		const auth = await import('/src/features/member/data/memberAuthStore.js');
		return { path: location.pathname + location.search, memberId: auth.useMemberAuth().member.value?.id ?? null,
			returnPath: sessionStorage.getItem('blog.member.returnPath') };
	});
}

async function form(width, action = 'signup') {
	if (action !== 'signup') { pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '가짜 이전 이름' }; }
	await open(width, action === 'signup' ? 'signup-required' : 'reactivation-required');
	if (action === 'rejoin') { await page.getByRole('button', { name: /새로 만들기/ }).click(); }
	if (action !== 'reactivate') { await page.getByLabel('닉네임', { exact: true }).fill('현재 작성한 이름'); }
	return action === 'signup' ? page.getByRole('button', { name: '시작하기', exact: true })
		: action === 'rejoin' ? page.getByRole('button', { name: '새로 만들기', exact: true })
			: page.getByRole('button', { name: /예전 계정 쓰기/ });
}

async function submitHeld(button, kind) {
	holds.add(keyOf(kind, count(kind)));
	const delivered = waitForRecord(kind, count(kind));
	await button.click();
	return delivered;
}

for (const width of [1280, 390]) {


	for (const kind of ['me', 'pending']) {
		test(`${width}: stale initial ${kind} leaves an explicit working recheck rather than endless loading`, async () => {
			holds.add(keyOf(kind, kind === 'me' ? 1 : 0));
			await open(width, kind === 'me' ? 'success' : 'signup-required');
			const old = await waitForRecord(kind, kind === 'me' ? 1 : 0); await switchMember(8, false);
			await finish(old, kind === 'me' ? member() : pending);
			const retry = page.getByRole('button', { name: '다시 확인', exact: true }); await retry.waitFor();
			await retry.click(); await page.getByRole('heading', { name: '닉네임을 정해 주세요', exact: true }).waitFor();
			assert.equal((await state()).memberId, 8);
		});
	}
	for (const status of [200, 409, 500]) {
		test(`${width}: submit HTTP${status} after unmount cannot navigate, consume return path or recheck pending`, async () => {
			const button = await form(width), old = await submitHeld(button, 'signup');
			await navigate('/privacy'); await remember(); const reads = count('pending');
			await finish(old, member(), status, status === 409 ? 'RESOURCE_BUSY' : 'INTERNAL_SERVER_ERROR');
			assert.equal((await state()).path, '/privacy'); assert.equal((await state()).returnPath, '/privacy?new-flow');
			assert.equal(count('pending'), reads); assert.equal(await page.getByText('가짜 이전 요청 오류', { exact: true }).count(), 0);
		});
	}
	for (const action of ['home', 'retry']) {
		test(`${width}: a fresh notice ${action} click works after a new member session`, async () => {
			await open(width, 'login-failed');
			await page.getByRole('heading', { name: '로그인하지 못했습니다', exact: true }).waitFor();
			await switchMember(); await remember();
			await page.getByRole('button', { name: action === 'home' ? '블로그로 돌아가기' : '다시 로그인', exact: true }).click();
			await page.waitForURL(action === 'home' ? '**/privacy?new-flow' : '**/login');
			assert.equal((await state()).memberId, 8);
			assert.equal((await state()).returnPath, action === 'home' ? null : '/privacy?new-flow');
		});
	}

	for (const action of ['choose', 'back', 'submit']) {
		test(`${width}: a fresh ${action} action rechecks stale form context without an automatic write`, async () => {
			const button = await form(width, action === 'back' ? 'rejoin' : action === 'submit' ? 'signup' : 'reactivate');
			await button.waitFor();
			await switchMember();
			pending = { loginState: 'SIGNUP_REQUIRED', suggestedNickname: '현재 서버 제안' };
			const reads = count('pending');
			const target = action === 'choose' ? page.getByRole('button', { name: /새로 만들기/ })
				: action === 'back' ? page.getByRole('button', { name: '뒤로', exact: true }) : button;
			const read = waitForRecord('pending', reads);
			await target.click(); await read;
			await page.waitForFunction(() => !document.querySelector('.member-auth__actions button[type=submit]')?.disabled);
			await page.getByRole('heading', { name: '닉네임을 정해 주세요', exact: true }).waitFor();
			assert.equal(count('pending'), reads + 1);
			assert.equal(count('signup') + count('reactivate') + count('rejoin'), 0);
			if (action === 'submit') { assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '현재 작성한 이름'); }
		});
	}

	test(`${width}: private member to anonymous ABA cannot revive a held pending response`, async () => {
		const button = await form(width), post = await submitHeld(button, 'signup');
		holds.add(keyOf('pending', count('pending')));
		const delivered = waitForRecord('pending', count('pending'));
		await finish(post, null, 409, 'RESOURCE_BUSY'); const old = await delivered;
		await switchMember(8, false); await switchMember(null, false);
		await page.getByLabel('닉네임', { exact: true }).fill('ABA 이후 현재 이름');
		await finish(old, { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '이전 응답' });
		await page.getByRole('heading', { name: '닉네임을 정해 주세요', exact: true }).waitFor();
		assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), 'ABA 이후 현재 이름');
		assert.equal((await state()).memberId, null);
	});
	for (const leave of [true, false]) {
		for (const status of [200, 500]) {
			test(`${width}: private session refresh alone protects B from old HTTP${status} leave=${leave}`, async () => {
				holds.add('me:1');
				await open(width);
				const old = await waitForRecord('me', 1);
				if (leave) { await navigate('/privacy'); }
				await switchMember(8, false);
				await remember();
				const before = await state();
				assert.equal(before.memberId, 8);
				await finish(old, member(), status);
				assert.deepEqual(await state(), before);
			});
		}
	}

	for (const leave of [true, false]) {
		for (const status of [200, 500]) {
			test(`${width}: old session HTTP${status} preserves B after new authentication leave=${leave}`, async context => {
				holds.add('me:1');
				await open(width);
				const old = await waitForRecord('me', 1);
				if (leave) { await navigate('/privacy'); }
				await switchMember();
				await remember();
				const before = await state();
				assert.equal(before.memberId, 8);
				await finish(old, member(), status);
				const after = await state();
				context.diagnostic(JSON.stringify({ before, after }));
				assert.deepEqual(after, before);
			});
		}
	}

	test(`${width}: an old session success after unmount cannot consume the new return path`, async context => {
		holds.add('me:1');
		await open(width);
		const old = await waitForRecord('me', 1);
		await navigate('/privacy');
		await remember();
		await finish(old, member());
		const after = await state();
		context.diagnostic(JSON.stringify(after));
		assert.equal(after.path, '/privacy');
		assert.equal(after.returnPath, '/privacy?new-flow');
	});

	for (const status of [200, 500]) {
		test(`${width}: a current existing login HTTP${status} retains its normal completion or notice`, async () => {
			holds.add('me:1');
			await open(width);
			const current = await waitForRecord('me', 1);
			await remember('/privacy?original');
			await finish(current, member(), status);
			if (status === 200) {
				await page.waitForURL('**/privacy?original');
				assert.equal((await state()).memberId, 7);
				assert.equal((await state()).returnPath, null);
			} else {
				await page.getByRole('heading', { name: '로그인 정보를 확인하지 못했습니다', exact: true }).waitFor();
				assert.equal((await state()).memberId, null);
				assert.equal((await state()).returnPath, '/privacy?original');
			}
		});
	}

	for (const action of ['signup', 'reactivate', 'rejoin']) {
		test(`${width}: normal ${action} keeps its own two authentication advances and original return route`, async () => {
			const button = await form(width, action);
			await remember('/privacy?original');
			await button.click();
			await page.waitForURL('**/privacy?original');
			assert.equal((await state()).memberId, 7);
			assert.equal((await state()).returnPath, null);
			assert.equal(await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).getAuthenticationGeneration()), 2);
			assert.equal(count(action), 1);
		});
	}

	test(`${width}: leaving during pending CSRF stops the callback from requesting pending data`, async () => {
		holds.add('csrf:0');
		await open(width, 'signup-required');
		const old = await waitForRecord('csrf');
		await navigate('/privacy');
		await remember();
		await finish(old, { headerName: 'X-CSRF-TOKEN', token: 'fake-csrf-token' });
		assert.equal(count('pending'), 0);
		assert.equal((await state()).returnPath, '/privacy?new-flow');
		assert.equal((await state()).path, '/privacy');
	});

	for (const advance of [false, true]) {
		test(`${width}: an old callback CSRF failure after B cannot clear the new session advance=${advance}`, async () => {
			holds.add('me:1'); holds.add('csrf:1');
			await open(width);
			await finish(await waitForRecord('me', 1), member());
			const old = await waitForRecord('csrf', 1);
			await switchMember(8, advance);
			await remember();
			const before = await state();
			await finish(old, null, 503);
			assert.deepEqual(await state(), before);
		});
	}

	for (const status of [200, 409, 500]) {
		test(`${width}: old signup HTTP${status} after B cannot consume return path or start a409 followup`, async () => {
			const button = await form(width);
			const old = await submitHeld(button, 'signup');
			await switchMember();
			await remember();
			const before = await state(), reads = count('pending');
			await finish(old, member(), status, status === 409 ? 'RESOURCE_BUSY' : 'INTERNAL_SERVER_ERROR');
			assert.deepEqual(await state(), before);
			assert.equal(count('pending'), reads);
			assert.equal(await page.getByRole('alert').count(), 0);
		});
	}

	for (const status of [200, 500]) {
		for (const next of ['ABA', 'new-pending']) {
			test(`${width}: stale pending HTTP${status} preserves current input and request after ${next}`, async () => {
				const button = await form(width);
				const post = await submitHeld(button, 'signup');
				pendingStatus = 503;
				await finish(post, null, 409, 'RESOURCE_BUSY');
				const retry = page.getByRole('button', { name: '다시 확인', exact: true });
				await retry.waitFor();
				const old = await submitHeld(retry, 'pending');
				const original = new URL(page.url()).pathname + new URL(page.url()).search;
				await navigate('/oauth/callback?result=signup-required&_cb=second');
				if (next === 'ABA') { await navigate(original); }
				const fresh = await submitHeld(retry, 'pending');
				await page.getByLabel('닉네임', { exact: true }).fill('새 요청이 보존할 이름');
				await finish(old, { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '이전 제안' }, status);
				assert.equal(await page.getByRole('button', { name: '확인 중…', exact: true }).isDisabled(), true);
				assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '새 요청이 보존할 이름');
				await page.getByRole('heading', { name: '닉네임을 정해 주세요', exact: true }).waitFor();
				await finish(fresh, pending);
				assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '새 요청이 보존할 이름');
				assert.equal(await button.isDisabled(), false);
				assert.equal(count('signup'), 1);
			});
		}
	}

	test(`${width}: getter-only authentication change blocks old local navigation after a submitted store action`, async () => {
		const button = await form(width), old = await submitHeld(button, 'signup');
		await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration());
		await remember();
		await finish(old, member());
		const after = await state();
		assert.equal(after.memberId, 7, 'Already started store work is not globally cancelled');
		assert.equal(after.returnPath, '/privacy?new-flow');
		assert.equal(new URL(page.url()).pathname, '/oauth/callback');
	});

	test(`${width}: an already-started shared authentication chain may finish after unmount without old page navigation`, async () => {
		const button = await form(width);
		holds.add(keyOf('csrf', count('csrf')));
		const arrived = waitForRecord('csrf', count('csrf'));
		await button.click();
		const old = await arrived;
		await navigate('/privacy');
		await remember();
		await finish(old, { headerName: 'X-CSRF-TOKEN', token: 'fake-csrf-token' });
		await page.waitForFunction(async () => (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().member.value?.id === 7);
		const after = await state();
		assert.equal(count('signup'), 1);
		assert.equal(after.path, '/privacy');
		assert.equal(after.returnPath, '/privacy?new-flow');
	});
}
