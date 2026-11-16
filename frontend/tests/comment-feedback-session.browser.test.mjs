import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const accountA = { id: 7, nickname: '검사 회원 A', profileImageUrl: null, role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '검사 회원 B', profileImageUrl: null, role: 'ROLE_USER' };
const oldError = 'A의 이전 댓글 오류';
const currentError = '현재 계정의 댓글 오류';
const otherError = '다른 기능의 오류';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const reaction = { likeCount: 6, dislikeCount: 0, likedByMe: true, dislikedByMe: false };
let server, browser, page, origin, member, calls, reads, nextCall, logoutFails, errors;
const target = (family, postId = 1) => (family === 'report' ? 303 : 101) + (postId - 1) * 1000;
const row = id => page.locator(`#comment-${id}`);
const like = id => row(id).getByRole('button', { name: '좋아요', exact: true });
const refresh = () => page.getByRole('button', { name: '댓글 새로고침', exact: true });
const marker = (postId, id) => `계정${member?.id ?? 'anon'} 글${postId} 댓글${id}`;
const tick = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen(); origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	member = { ...accountA }; calls = []; reads = []; nextCall = null; logoutFails = false; errors = [];
	page = await browser.newPage({ serviceWorkers: 'block' }); page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	page.on('console', message => { if (message.type() === 'warning' && message.text().includes('[Vue warn]')) errors.push(message.text()); });
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url()), path = url.pathname;
		if (url.origin !== new URL(origin).origin) return route.abort('blockedbyclient');
		if (!path.startsWith('/api/v1/')) return route.continue();
		if (path === '/api/v1/auth/me') return ok(route, member);
		if (path === '/api/v1/auth/csrf') return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-comment-feedback-token' });
		if (path === '/api/v1/auth/logout') {
			if (logoutFails) return route.fulfill({ status: 500, json: { success: false, message: '가짜 로그아웃 실패' } });
			member = null; return ok(route);
		}
		const comments = /^\/api\/v1\/blog\/posts\/(\d+)\/comments$/.exec(path);
		if (comments && request.method() === 'GET') {
			const postId = Number(comments[1]); reads.push({ actor: member?.id ?? null, postId });
			const item = (id, owner) => ({ id, nickname: `작성 회원 ${owner}`, content: marker(postId, id), authorComment: false,
				createdAt: '2026-11-17T00:00:00', mine: member?.id === owner, deleted: false, edited: false, hiddenByAdmin: false,
				reportedByMe: false, likeCount: 5, dislikeCount: 0, likedByMe: false, dislikedByMe: false, replies: [], replyHasNext: false, replyNextCursor: null });
			const offset = (postId - 1) * 1000;
			return ok(route, { total: 3, items: [item(101 + offset, 7), item(202 + offset, 8), item(303 + offset, 9)], hasNext: false, nextCursor: null });
		}
		const mutation = /^\/api\/v1\/blog\/comments\/(\d+)\/(reaction|reports)$/.exec(path);
		if (mutation) {
			const call = { route, request, actor: member?.id ?? null, id: Number(mutation[1]), family: mutation[2] === 'reports' ? 'report' : 'reaction', body: request.postDataJSON() };
			calls.push(call); const resolve = nextCall; nextCall = null; resolve?.(call); return;
		}
		if (/\/posts\/\d+$/.test(path)) return ok(route, { id: Number(path.split('/').pop()), title: '댓글 세션 경계 검사', content: '실제 부모의 계정별 댓글 GET과 폼을 검증합니다.', categoryId: 1, categoryName: '검사', publishedAt: '2026-11-17T00:00:00', views: 1 });
		if (path.endsWith('/categories') || path.endsWith('/related') || path.endsWith('/notifications')) return ok(route, []);
		if (path.endsWith('/unread-count')) return ok(route, 0);
		return ok(route);
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function open(width) {
	await page.setViewportSize({ width, height: 1000 }); await page.goto(origin + 'posts/1');
	await row(303).getByText(marker(1, 303), { exact: true }).waitFor();
	assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
}
async function session(next, { authenticate = false } = {}) {
	const previousId = member?.id, previousReads = reads.length; member = next;
	await page.evaluate(async authenticate => {
		if (authenticate) { const { advanceAuthenticationGeneration } = await import('/src/shared/api/blogApiClient.js'); advanceAuthenticationGeneration(); }
		const { refreshMemberSession } = await import('/src/features/member/data/memberAuthStore.js'); await refreshMemberSession();
	}, authenticate);
	if (previousId !== next?.id) {
		await row(303).getByText(marker(1, 303), { exact: true }).waitFor();
		assert.ok(reads.length > previousReads); assert.equal(reads.at(-1).actor, next?.id ?? null);
	} else { await tick(); }
}
async function start(family, { postId = 1, id = target(family, postId), detail = '현재 신고 초안' } = {}) {
	const requested = new Promise(resolve => { nextCall = resolve; });
	if (family === 'reaction') { await like(id).click(); }
	else {
		await row(id).getByRole('button', { name: '신고', exact: true }).click();
		const dialog = page.getByRole('dialog');
		await dialog.getByLabel('기타', { exact: true }).check();
		await dialog.getByRole('textbox', { name: '신고 사유 설명' }).fill(detail);
		await dialog.getByRole('button', { name: '신고', exact: true }).click();
	}
	return requested;
}
async function finish(call, { error = null, data = reaction } = {}) {
	const response = page.waitForResponse(response => response.request() === call.request);
	if (error) await call.route.fulfill({ status: 500, json: { success: false, message: error } });
	else await ok(call.route, call.family === 'reaction' ? data : null);
	await response; await tick();
}
async function pending(family, id = target(family)) {
	return family === 'reaction' ? like(id).isDisabled() : page.getByRole('dialog').getByRole('button', { name: '보내는 중…', exact: true }).isDisabled();
}
async function noOldError() {
	assert.equal(await page.getByText(oldError, { exact: true }).count(), 0);
}
async function shot(name) {
	if (process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/${name}.png`, fullPage: true });
	}
}

for (const width of [1280, 390]) {
	for (const family of ['reaction', 'report']) {
		for (const fails of [false, true]) {
			test(`${width}px ${family}: B makes its own request and old ${fails ? '500' : 'success'} cannot clear B pending`, async () => {
				await open(width); const old = await start(family); await session({ ...accountB });
				if (family === 'report') assert.equal(await page.getByRole('dialog').count(), 0);
				assert.equal(await refresh().isEnabled(), true);
				const current = await start(family, { detail: 'B 새 신고 초안' });
				assert.deepEqual(calls.map(call => call.actor), [7, 8]);
				await finish(old, { error: fails ? oldError : null });
				assert.equal(await pending(family), true); await noOldError();
				if (family === 'reaction') { assert.equal(await refresh().isDisabled(), true); assert.equal(await like(101).getAttribute('aria-pressed'), 'false'); }
				else assert.equal(await page.getByRole('textbox', { name: '신고 사유 설명' }).inputValue(), 'B 새 신고 초안');
				await shot(`${width}-${family}-${fails ? 'old-error' : 'old-success'}-B-pending`);
				await finish(current);
				if (family === 'reaction') { assert.equal(await like(101).getAttribute('aria-pressed'), 'true'); assert.equal((await like(101).textContent()).trim(), '6'); }
				else await page.getByRole('button', { name: '이미 신고한 댓글', exact: true }).waitFor();
				assert.equal(await refresh().isEnabled(), true);
			});
		}
		test(`${width}px ${family}: an old failure cannot remove the new account's current error and input`, async () => {
			await open(width); const old = await start(family); await session({ ...accountB }); const current = await start(family, { detail: 'B 오류 후 보존할 초안' });
			await finish(current, { error: currentError }); await finish(old, { error: oldError }); await noOldError();
			assert.equal(await page.locator('.app-toast__message').getByText(currentError, { exact: true }).count(), 1);
			if (family === 'report') assert.equal(await page.getByRole('textbox', { name: '신고 사유 설명' }).inputValue(), 'B 오류 후 보존할 초안');
		});
		test(`${width}px ${family}: A to B to A creates a new request epoch`, async () => {
			await open(width); const old = await start(family); await session({ ...accountB }); await session({ ...accountA });
			const current = await start(family); assert.deepEqual(reads.slice(-2).map(read => read.actor), [8, 7]);
			await finish(old, { error: oldError }); assert.equal(await pending(family), true); await noOldError(); await finish(current);
		});
		test(`${width}px ${family}: a same-id new authentication allows a new request and isolates its pending`, async () => {
			await open(width); const old = await start(family); const count = reads.length;
			await session({ ...accountA }, { authenticate: true }); assert.equal(reads.length, count);
			const current = await start(family); assert.equal(calls.length, 2);
			await finish(old, { error: oldError }); assert.equal(await pending(family), true); await noOldError(); await finish(current);
		});
		test(`${width}px ${family}: getter-only changes are checked at the response boundary`, async () => {
			await open(width); const old = await start(family);
			await page.evaluate(async () => { const { advanceAuthenticationGeneration } = await import('/src/shared/api/blogApiClient.js'); advanceAuthenticationGeneration(); });
			await finish(old, { error: oldError }); await noOldError();
			assert.equal(await refresh().isEnabled(), true); assert.equal(await page.getByRole('dialog').count(), 0);
			const current = await start(family); await finish(current);
		});
		test(`${width}px ${family}: nickname and same-account recheck preserve pending and input`, async () => {
			await open(width); const current = await start(family, { detail: '같은 회원의 초안' }); const count = reads.length;
			await session({ ...accountA, nickname: 'A 새 닉네임' }); await session({ ...member });
			assert.equal(reads.length, count); assert.equal(await pending(family), true); assert.equal(calls.length, 1);
			if (family === 'report') assert.equal(await page.getByRole('textbox', { name: '신고 사유 설명' }).inputValue(), '같은 회원의 초안');
			await finish(current);
		});
		test(`${width}px ${family}: account change dismisses only this feedback's persistent error toast`, async () => {
			await open(width);
			await page.evaluate(async message => { const { notifyError } = await import('/src/shared/toast/toastStore.js'); notifyError(message); }, otherError);
			const failed = await start(family); await finish(failed, { error: currentError });
			assert.equal(await page.locator('.app-toast__message').getByText(currentError, { exact: true }).count(), 1);
			await session({ ...accountB });
			assert.equal(await page.getByText(currentError, { exact: true }).count(), 0);
			assert.equal(await page.locator('.app-toast__message').getByText(otherError, { exact: true }).count(), 1);
		});
		for (const leave of ['post', 'unmount']) {
			test(`${width}px ${family}: ${leave} replacement ignores the previous failure`, async () => {
				await open(width); const old = await start(family);
				await page.evaluate(async leave => { const { default: router } = await import('/src/app/router/index.js'); await router.push(leave === 'post' ? '/posts/2' : '/posts'); }, leave);
				if (leave === 'post') await row(1303).getByText(marker(2, 1303), { exact: true }).waitFor();
				else await page.locator('.comments').waitFor({ state: 'hidden' });
				await finish(old, { error: oldError }); await noOldError();
				if (leave === 'post') { const current = await start(family, { postId: 2 }); await finish(current); }
			});
		}
	}
	test(`${width}px reaction: successful logout clears pending and drops a late error`, async () => {
		await open(width); const old = await start('reaction');
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await row(303).getByText(marker(1, 303), { exact: true }).waitFor();
		assert.equal(await like(101).isEnabled(), true); assert.equal(await refresh().isEnabled(), true);
		await finish(old, { error: oldError }); await noOldError(); assert.equal(reads.at(-1).actor, null);
	});
	test(`${width}px reaction: failed logout retains the valid request and old account state`, async () => {
		await open(width); const current = await start('reaction'); const count = reads.length; logoutFails = true;
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
		assert.equal(reads.length, count); assert.equal(await like(101).isDisabled(), true);
		await finish(current); assert.equal(await like(101).getAttribute('aria-pressed'), 'true'); assert.equal(await refresh().isEnabled(), true);
	});
	test(`${width}px counter: B can refresh independently while A is held`, async () => {
		await open(width); const old = await start('reaction'); await session({ ...accountB }); const before = reads.length;
		assert.equal(await refresh().isEnabled(), true);
		const response = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/posts/1/comments'));
		await refresh().click(); await response; await row(303).getByText(marker(1, 303), { exact: true }).waitFor();
		assert.equal(reads.length, before + 1); assert.equal(reads.at(-1).actor, 8);
		await finish(old, { error: oldError }); await noOldError(); assert.equal(await refresh().isEnabled(), true);
	});
	test(`${width}px counter: independent same-account writes keep refresh disabled until both finish`, async () => {
		await open(width); const first = await start('reaction'); const second = await start('reaction', { id: 303 });
		assert.equal(await refresh().isDisabled(), true); await finish(first); assert.equal(await refresh().isDisabled(), true);
		await finish(second); assert.equal(await refresh().isEnabled(), true); assert.equal(calls.length, 2);
	});
	test(`${width}px counter: a getter-only change is detected before the next distinct operation`, async () => {
		await open(width); const old = await start('reaction');
		await page.evaluate(async () => { const { advanceAuthenticationGeneration } = await import('/src/shared/api/blogApiClient.js'); advanceAuthenticationGeneration(); });
		const current = await start('reaction', { id: 303 });
		await finish(old, { error: oldError }); assert.equal(await like(303).isDisabled(), true); assert.equal(await refresh().isDisabled(), true); await noOldError();
		await finish(current); assert.equal(await refresh().isEnabled(), true);
	});
}
