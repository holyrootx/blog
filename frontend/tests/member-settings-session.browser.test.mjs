import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const accountA = { id: 7, nickname: '로컬 회원 A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '로컬 회원 B', role: 'ROLE_USER' };
const oldError = '가짜 이전 계정 요청 오류';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
async function bounded(promise) {
	let timer;
	try { return await Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('The actual handler record was not delivered')), 7000); })]); }
	finally { clearTimeout(timer); }
}
let server, browser, cacheDir, page, errors, serverMember, serverSession, requests, waiting;
let holdCsrf, csrfArrived;
let logoutFails;
const traces = [];
const input = () => page.locator('#settings-nickname');
const save = () => page.locator('.member-settings__profile button');
const frames = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-settings-091-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	errors = []; requests = []; waiting = []; serverMember = { ...accountA }; serverSession = 1;
	holdCsrf = false; csrfArrived = deferred();
	logoutFails = false;
	page = await browser.newPage({ serviceWorkers: 'block' }); page.setDefaultTimeout(7000);
	page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url()), path = url.pathname;
		if (url.origin !== origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/')) { return route.continue(); }
		if (path === '/api/v1/auth/me') { return ok(route, serverMember); }
		if (path === '/api/v1/auth/csrf') {
			if (holdCsrf) { holdCsrf = false; csrfArrived.resolve(route); return; }
			return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-settings-token' });
		}
		if (path === '/api/v1/auth/me/nickname' || path === '/api/v1/auth/withdraw') {
			const record = { route, request, actor: serverMember?.id, member: { ...serverMember }, session: serverSession,
				kind: path.endsWith('/nickname') ? 'nickname' : 'withdraw', body: request.postDataJSON(), completions: 0 };
			requests.push(record); const next = waiting.shift(); assert.ok(next, 'A handler waiter must be registered before the action');
			next.resolve(record); return;
		}
		if (path === '/api/v1/auth/logout') {
			if (logoutFails) { return route.fulfill({ status: 500, json: { success: false, message: '가짜 로그아웃 실패' } }); }
			serverMember = null; serverSession += 1; return ok(route);
		}
		if (path.endsWith('/recent-posts')) { return ok(route, { items: [], page: 0, totalPages: 0, totalElements: 0, size: 10 }); }
		if (path.endsWith('/comments') || path.endsWith('/topics') || path.endsWith('/home/posts')) { return ok(route, []); }
		if (path.endsWith('/notifications/unread-count')) { return ok(route, 0); }
		if (path === '/api/v1/blog/profile') { return ok(route, { id: 1, name: '로컬 검사 블로그', intro: '가짜 소개', job: '기록' }); }
		return ok(route);
	});
});
afterEach(async context => {
	try {
		let state = null;
		try { state = await snapshot(); } catch { /* 닫힌 페이지는 별도 상태를 만들지 않는다. */ }
		traces.push({ name: context.name, requests: requests.map(({ actor, kind, body, completions }) => ({ actor, kind, body, completions })), state, errors });
		if (state?.routeObservers) {
			assert.equal(state.routeObservers.active, 0, 'Each action route observer is removed');
			assert.ok(state.routeObservers.stops.every(count => count === 1), 'Each observer is removed exactly once');
		}
		if (process.env.QA_TRACE_PATH) {
			await mkdir(dirname(process.env.QA_TRACE_PATH), { recursive: true });
			await writeFile(process.env.QA_TRACE_PATH, JSON.stringify(traces, null, 2) + '\n');
		}
		if (process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 130) + '.png') });
		}
	} finally { await page.close(); }
	assert.deepEqual(errors, []);
});

async function open(width) {
	await page.setViewportSize({ width, height: 1000 });
	await page.goto(server.resolvedUrls.local[0] + 'settings'); await input().waitFor();
	await page.evaluate(async () => {
		const router = (await import('/src/app/router/index.js')).default;
		window.__settingsNavigation = [];
		router.afterEach(to => window.__settingsNavigation.push(to.fullPath));
		const afterEach = router.afterEach;
		window.__settingsRouteObservers = [];
		router.afterEach = callback => {
			const record = { active: true, stops: 0 }; window.__settingsRouteObservers.push(record);
			const stop = afterEach(callback);
			return () => { record.stops += 1; record.active = false; stop(); };
		};
	});
}
async function navigate(path) {
	await page.evaluate(async path => (await import('/src/app/router/index.js')).default.push(path), path);
	await frames();
}
async function advanceGetter() {
	await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration());
}
async function switchAccount(member, { advance = true } = {}) {
	serverMember = member === null ? null : { ...member };
	if (advance) { serverSession += 1; }
	await page.evaluate(async advance => {
		if (advance) { (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration(); }
		await (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession();
	}, advance);
	await frames();
}
async function startNickname(value) {
	await input().fill(value); const arrived = deferred(); waiting.push(arrived);
	await save().click(); return bounded(arrived.promise);
}
async function startWithdraw() {
	const arrived = deferred(); waiting.push(arrived);
	await page.getByRole('button', { name: '탈퇴하기', exact: true }).click();
	await page.getByRole('button', { name: '탈퇴', exact: true }).click(); return bounded(arrived.promise);
}
async function complete(record, { fails = false, message = oldError } = {}) {
	assert.equal(record.completions, 0, 'Each HTTP record completes once'); record.completions += 1;
	const received = page.waitForResponse(response => response.request() === record.request);
	if (fails) { await record.route.fulfill({ status: 500, json: { success: false, code: 'INTERNAL_SERVER_ERROR', message } }); }
	else if (record.kind === 'nickname') {
		const result = { ...record.member, nickname: record.body.nickname.trim() };
		if (record.session === serverSession && record.actor === serverMember?.id) { serverMember = result; }
		await ok(record.route, result);
	} else {
		if (record.session === serverSession && record.actor === serverMember?.id) { serverMember = null; }
		await ok(record.route);
	}
	await (await received).finished(); await frames();
	record.httpCompleted?.resolve();
}
async function completeWithdrawalAtLogin(record) {
	const navigationStart = await page.evaluate(() => window.__settingsNavigation.length);
	// HTTP와 프레임 완료로는 비동기 라우트 완료를 알 수 없다. 응답 전달 전에 실제 관찰을 등록한다.
	const csrf = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/csrf'
		&& response.request().method() === 'GET');
	const navigation = page.waitForFunction(start => location.pathname === '/login'
		&& window.__settingsNavigation.slice(start).includes('/login'), navigationStart);
	await Promise.all([
		complete(record),
		csrf.then(async response => { assert.equal(response.status(), 200); await response.finished(); }),
		navigation.then(handle => handle.dispose()),
	]);
}
async function snapshot() {
	return page.evaluate(async () => {
		const member = (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().member.value;
		return { path: location.pathname + location.search + location.hash, memberId: member?.id ?? null,
			nickname: member?.nickname ?? null, draft: document.querySelector('#settings-nickname')?.value,
			pending: document.querySelector('.member-settings__profile button')?.textContent,
			alerts: [...document.querySelectorAll('[role="alert"],.ui-toast')].map(element => element.textContent),
			done: document.querySelector('.member-settings__done')?.textContent,
			navigation: window.__settingsNavigation, noOverflow: document.documentElement.scrollWidth <= innerWidth,
			routeObservers: { active: (window.__settingsRouteObservers ?? []).filter(item => item.active).length,
				stops: (window.__settingsRouteObservers ?? []).map(item => item.stops) } };
	});
}
async function releaseCsrf(route, { fails = false } = {}) {
	const received = page.waitForResponse(response => response.request() === route.request());
	if (fails) { await route.fulfill({ status: 500, json: { success: false, message: '가짜 CSRF 실패' } }); }
	else { await ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-after-withdraw' }); }
	await (await received).finished(); await frames();
}

for (const width of [1280, 390]) {
	for (const renamed of [false, true]) {
		for (const fails of [false, true]) {
			test(`${width}: same identity ${renamed ? 'nickname refresh' : 'session refresh'} preserves a valid nickname action (${fails})`, async () => {
				await open(width); const current = await startNickname('현재 계정의 요청');
				await switchAccount({ ...accountA, nickname: renamed ? '같은 회원의 새 표시명' : accountA.nickname }, { advance: false });
				assert.equal(await save().textContent(), '저장 중…'); assert.equal(await input().inputValue(), '현재 계정의 요청');
				await complete(current, { fails, message: '가짜 현재 계정 오류' }); const state = await snapshot();
				assert.equal(state.memberId, 7); assert.equal(state.path, '/settings'); assert.equal(state.draft, '현재 계정의 요청');
				if (fails) { assert.ok(state.alerts.some(text => text.includes('가짜 현재 계정 오류'))); }
				else { assert.ok(state.done); }
			});
		}
	}
	test(`${width}: failed logout preserves the valid nickname action and draft`, async () => {
		await open(width); const current = await startNickname('로그아웃 실패 후 초안'); logoutFails = true;
		const failure = await page.evaluate(async () => {
			try { await (await import('/src/features/member/data/memberAuthStore.js')).signOutMember(); }
			catch (error) { return error.message; }
		});
		assert.equal(failure, '가짜 로그아웃 실패'); assert.equal(await save().textContent(), '저장 중…');
		await complete(current); const state = await snapshot();
		assert.equal(state.memberId, 7); assert.equal(state.draft, '로그아웃 실패 후 초안'); assert.ok(state.done);
	});
	for (const fails of [false, true]) {
		test(`${width}: old nickname ${fails ? 'failure' : 'success'} cannot report into B settings`, async () => {
			await open(width); const old = await startNickname('A 변경 요청');
			await switchAccount(accountB); await input().fill('B 새 초안');
			await complete(old, { fails }); const state = await snapshot();
			assert.equal(state.memberId, 8); assert.equal(state.draft, 'B 새 초안');
			assert.equal(state.done, undefined); assert.ok(state.alerts.every(text => !text.includes(oldError)));
		});
		test(`${width}: B sends a real nickname request while A is held and old finally cannot release it (${fails})`, async () => {
			await open(width); const old = await startNickname('A 변경 요청');
			await switchAccount(accountB); await input().fill('B 새 초안');
			assert.equal(await save().isEnabled(), true, 'B must not inherit A pending');
			const current = await startNickname('B 새 초안'); assert.deepEqual(requests.map(item => item.actor), [7, 8]);
			await complete(old, { fails }); assert.equal(await save().textContent(), '저장 중…');
			assert.equal(await input().inputValue(), 'B 새 초안');
			await complete(current); assert.equal((await snapshot()).memberId, 8);
			await page.locator('.member-settings__done').waitFor();
		});
		test(`${width}: old withdrawal ${fails ? 'failure' : 'success'} cannot move or report into B settings`, async () => {
			await open(width); const old = await startWithdraw();
			await switchAccount(accountB); await input().fill('B 탈퇴 아님');
			await complete(old, { fails }); const state = await snapshot();
			assert.equal(state.path, '/settings'); assert.equal(state.memberId, 8); assert.equal(state.draft, 'B 탈퇴 아님');
			assert.ok(state.alerts.every(text => !text.includes(oldError)));
		});
	}
	test(`${width}: normal nickname success remains visible and a newer draft survives`, async () => {
		await open(width); const current = await startNickname('새 닉네임'); await input().fill('더 최신 초안');
		await complete(current); const state = await snapshot();
		assert.equal(state.nickname, '새 닉네임'); assert.equal(state.draft, '더 최신 초안');
		assert.ok(state.done); assert.equal(state.path, '/settings');
	});
	test(`${width}: own successful withdrawal still finishes at home after the anonymous login transition`, async () => {
		await open(width); const current = await startWithdraw(); holdCsrf = true;
		await complete(current); const csrf = await bounded(csrfArrived.promise);
		const during = await snapshot(); assert.equal(during.memberId, null);
		await ok(csrf, { headerName: 'X-CSRF-TOKEN', token: 'fake-after-withdraw' });
		await page.waitForURL(url => url.pathname === '/');
		assert.equal((await snapshot()).memberId, null);
	});
	test(`${width}: failed own withdrawal keeps the account and draft`, async () => {
		await open(width); await input().fill('실패 후 보존할 초안'); const current = await startWithdraw();
		await complete(current, { fails: true, message: '가짜 탈퇴 실패' }); const state = await snapshot();
		assert.equal(state.memberId, 7); assert.equal(state.path, '/settings');
		assert.equal(state.draft, '실패 후 보존할 초안'); assert.ok(state.alerts.some(text => text.includes('가짜 탈퇴 실패')));
	});
	test(`${width}: external logout followed by an old resolved withdrawal stays on login`, async () => {
		await open(width); const old = await startWithdraw();
		await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).signOutMember());
		await page.waitForURL(url => url.pathname === '/login');
		await complete(old); assert.equal((await snapshot()).path, '/login');
	});
	for (const releaseFirst of ['csrf', 'route']) {
		test(`${width}: route ABA withdrawal completion waits with ${releaseFirst} released first`, async () => {
			await open(width); const old = await startWithdraw(); await navigate('/terms');
			await navigate('/settings'); await input().waitFor();
			await input().fill('새 설정 화면 초안');
			await page.evaluate(async () => {
				const router = (await import('/src/app/router/index.js')).default;
				window.__settingsLoginGateEntered = false;
				const gate = new Promise(resolve => { window.__releaseSettingsLoginGate = resolve; });
				window.__stopSettingsLoginGate = router.beforeResolve(async to => {
					if (to.name === 'member-login') { window.__settingsLoginGateEntered = true; await gate; }
				});
			});
			holdCsrf = true; old.httpCompleted = deferred();
			let settled = false, csrf, completionError, csrfCompletions = 0, routeReleases = 0;
			const completion = completeWithdrawalAtLogin(old).then(() => { settled = true; return null; }, error => { settled = true; return error; });
			const finishCsrf = async () => {
				assert.equal(csrfCompletions, 0); csrfCompletions += 1; await releaseCsrf(csrf);
			};
			const finishRoute = async () => {
				assert.equal(routeReleases, 0); routeReleases += 1;
				await page.evaluate(() => window.__releaseSettingsLoginGate());
				await page.waitForURL(url => url.pathname === '/login');
				await page.waitForFunction(() => window.__settingsNavigation.includes('/login'));
			};
			try {
				csrf = await bounded(csrfArrived.promise); await bounded(old.httpCompleted.promise);
				await page.waitForFunction(() => window.__settingsLoginGateEntered);
				const held = await snapshot();
				assert.equal(held.memberId, null); assert.equal(held.path, '/settings');
				assert.equal(held.routeObservers.active, 0);
				assert.equal(settled, false, 'HTTP completion and animation frames do not complete the pending navigation');
				if (releaseFirst === 'csrf') { await finishCsrf(); }
				else { await finishRoute(); }
				await snapshot();
				assert.equal(settled, false, 'The remaining real completion boundary is still held');
			} finally {
				if (csrf && csrfCompletions === 0) { await finishCsrf(); }
				if (routeReleases === 0) { await finishRoute(); }
				await page.evaluate(() => window.__stopSettingsLoginGate());
				completionError = await completion;
			}
			if (completionError) { throw completionError; }
			const state = await snapshot();
			assert.equal(state.path, '/login'); assert.equal(state.memberId, null);
			assert.ok(!state.navigation.includes('/')); assert.equal(state.routeObservers.active, 0);
			assert.ok(state.alerts.every(text => !text.includes(oldError)));
			assert.equal(old.completions, 1); assert.equal(csrfCompletions, 1); assert.equal(routeReleases, 1);
			assert.deepEqual(requests.map(item => item.actor), [7]);
		});
	}
	for (const fails of [false, true]) {
		for (const boundary of ['ABA', 'same-id authentication', 'getter only', 'route', 'route ABA', 'unmount']) {
			test(`${width}: nickname ${boundary} drops old ${fails ? 'error' : 'success'} feedback`, async () => {
				await open(width); const old = await startNickname('이전 닉네임 요청');
				if (boundary === 'ABA') { await switchAccount(accountB); await switchAccount(accountA); }
				if (boundary === 'same-id authentication') { await switchAccount({ ...accountA, nickname: '새 로그인 A' }); }
				if (boundary === 'getter only') { await advanceGetter(); }
				if (boundary === 'route' || boundary === 'route ABA') { await navigate('/settings?view=other'); }
				if (boundary === 'route ABA') { await navigate('/settings'); }
				if (boundary === 'unmount') { await navigate('/terms'); }
				if (boundary !== 'unmount') { await input().fill('현재 화면의 새 초안'); }
				await complete(old, { fails }); const state = await snapshot();
				if (boundary !== 'unmount') { assert.equal(state.draft, '현재 화면의 새 초안'); assert.equal(state.done, undefined); }
				else { assert.equal(state.path, '/terms'); }
				assert.ok(state.alerts.every(text => !text.includes(oldError) && !text.includes('닉네임을 바꿨습니다.')));
			});
		}
		test(`${width}: B withdrawal starts while A is held and A finally preserves B pending (${fails})`, async () => {
			await open(width); const old = await startWithdraw(); await switchAccount(accountB);
			await input().fill('B가 확인 중인 초안');
			await page.getByRole('button', { name: '탈퇴하기', exact: true }).waitFor();
			const current = await startWithdraw(); assert.deepEqual(requests.map(item => item.actor), [7, 8]);
			await complete(old, { fails });
			assert.equal((await snapshot()).path, '/settings'); assert.equal(await input().inputValue(), 'B가 확인 중인 초안');
			assert.equal(await page.getByRole('button', { name: '처리 중…', exact: true }).isDisabled(), true);
			assert.equal((await snapshot()).routeObservers.active, 1);
			await complete(current); await page.waitForURL(url => url.pathname === '/');
			assert.equal((await snapshot()).memberId, null);
		});
		for (const boundary of ['other route', 'route ABA']) {
			test(`${width}: withdrawal ${boundary} cannot force old action home (${fails}) and cleans its observer`, async () => {
				await open(width); const old = await startWithdraw(); await navigate('/terms');
				if (boundary === 'route ABA') { await navigate('/settings'); await input().waitFor(); await input().fill('새 설정 화면 초안'); }
				assert.equal((await snapshot()).routeObservers.active, 0);
				if (boundary === 'route ABA' && !fails) { await completeWithdrawalAtLogin(old); }
				else { await complete(old, { fails }); }
				const state = await snapshot();
				// 같은 세션의 실제 탈퇴는 store를 정리할 수 있다. 새 설정 화면의 기존 login 이동은 유지한다.
				const expected = boundary === 'route ABA' ? (fails ? '/settings' : '/login') : '/terms';
				assert.equal(state.path, expected); assert.ok(!state.navigation.includes('/'));
				if (boundary === 'route ABA' && fails) { assert.equal(state.draft, '새 설정 화면 초안'); }
				assert.ok(state.alerts.every(text => !text.includes(oldError)));
			});
		}
	}
	for (const boundary of ['route ABA', 'getter only']) {
		test(`${width}: ${boundary} during own post-clear CSRF prevents the previous final home`, async () => {
			await open(width); const current = await startWithdraw(); holdCsrf = true; await complete(current);
			const csrf = await bounded(csrfArrived.promise); await page.waitForURL(url => url.pathname === '/login');
			assert.equal((await snapshot()).routeObservers.active, 1);
			if (boundary === 'route ABA') { await navigate('/terms'); await navigate('/login'); }
			else { await advanceGetter(); }
			await releaseCsrf(csrf); assert.equal((await snapshot()).path, '/login');
		});
	}
	test(`${width}: B login and a new nickname action during old CSRF remain on B settings`, async () => {
		await open(width); const old = await startWithdraw(); holdCsrf = true; await complete(old);
		const csrf = await bounded(csrfArrived.promise); await page.waitForURL(url => url.pathname === '/login');
		await switchAccount(accountB); assert.equal((await snapshot()).routeObservers.active, 0);
		await navigate('/settings'); await input().waitFor();
		const current = await startNickname('B의 실제 새 요청'); await releaseCsrf(csrf);
		assert.equal((await snapshot()).path, '/settings'); assert.equal(await save().textContent(), '저장 중…');
		assert.equal(await input().inputValue(), 'B의 실제 새 요청');
		await complete(current); assert.equal((await snapshot()).memberId, 8);
	});
	test(`${width}: CSRF refresh failure after own withdrawal keeps the original final home contract`, async () => {
		await open(width); const current = await startWithdraw(); holdCsrf = true; await complete(current);
		const csrf = await bounded(csrfArrived.promise); await releaseCsrf(csrf, { fails: true });
		await page.waitForURL(url => url.pathname === '/'); assert.equal((await snapshot()).memberId, null);
	});
	test(`${width}: identity reset dismisses only page-owned nickname and withdrawal error toasts`, async () => {
		await open(width); await page.evaluate(async () => (await import('/src/shared/toast/toastStore.js')).notifyError('다른 기능의 오류'));
		const nickname = await startNickname('현재 닉네임 오류'); await complete(nickname, { fails: true, message: '페이지 닉네임 오류' });
		const withdrawal = await startWithdraw(); await complete(withdrawal, { fails: true, message: '페이지 탈퇴 오류' });
		await switchAccount(accountB); const state = await snapshot();
		assert.ok(state.alerts.some(text => text.includes('다른 기능의 오류')));
		assert.ok(state.alerts.every(text => !text.includes('페이지 닉네임 오류') && !text.includes('페이지 탈퇴 오류')));
	});
	for (const boundary of ['ABA', 'same-id authentication', 'getter only']) {
		for (const fails of [false, true]) {
			test(`${width}: withdrawal ${boundary} drops local completion without promising global cancellation (${fails})`, async () => {
				await open(width); const old = await startWithdraw();
				if (boundary === 'ABA') { await switchAccount(accountB); await switchAccount(accountA); }
				if (boundary === 'same-id authentication') { await switchAccount({ ...accountA, nickname: '다시 인증한 A' }); }
				if (boundary === 'getter only') { await advanceGetter(); }
				await input().fill('현재 설정의 초안'); await complete(old, { fails }); const state = await snapshot();
				const expected = boundary === 'getter only' && !fails ? '/login' : '/settings';
				assert.equal(state.path, expected); assert.ok(!state.navigation.includes('/'));
				if (expected === '/settings') { assert.equal(state.draft, '현재 설정의 초안'); }
				assert.ok(state.alerts.every(text => !text.includes(oldError)));
			});
		}
	}
}
