import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const accountA = { id: 7, nickname: '검사 회원 A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '검사 회원 B', role: 'ROLE_USER' };
const logoutError = '로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
let server, browser, page, origin, cacheDir, member, reads, logouts, errors;
const traces = [];
const tick = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
const logout = () => page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true });
const input = () => page.locator('.comment-form__input');

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-comment-signout-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen(); origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	member = { ...accountA }; reads = []; logouts = []; errors = [];
	page = await browser.newPage({ serviceWorkers: 'block' }); page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url()), path = url.pathname;
		if (url.origin !== new URL(origin).origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/v1/')) { return route.continue(); }
		if (path === '/api/v1/auth/me') { return ok(route, member); }
		if (path.endsWith('/csrf')) { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-comment-signout-csrf' }); }
		if (path === '/api/v1/auth/me/nickname') { member = { ...member, nickname: request.postDataJSON().nickname }; return ok(route, member); }
		if (path === '/api/v1/auth/logout') { logouts.push({ route, actor: member?.id ?? null }); return; }
		if (/\/posts\/\d+$/.test(path)) {
			const id = Number(path.split('/').pop());
			return ok(route, { id, title: `댓글 로그아웃 검사 ${id}`, content: '실제 부모와 인증 스토어로 검사합니다.',
				categoryId: 1, categoryName: '검사', publishedAt: '2026-11-17T00:00:00', views: 1 });
		}
		const comments = /^\/api\/v1\/blog\/posts\/(\d+)\/comments$/.exec(path);
		if (comments) {
			const postId = Number(comments[1]); reads.push({ actor: member?.id ?? null, postId });
			const raw = (id, owner) => ({ id, nickname: `작성 회원 ${owner}`,
				content: `글${postId} 회원${member?.id ?? 'anon'} 댓글${id}`, mine: member?.id === owner,
				authorComment: false, createdAt: '2026-11-17T00:00:00', deleted: false,
				likeCount: 5, dislikeCount: 0, likedByMe: member?.id === 8, dislikedByMe: false, replies: [] });
			const root = raw(101, 7); root.replies = [raw(104, 9)];
			return ok(route, { total: 3, items: [root, raw(202, 8)], hasNext: false, nextCursor: null });
		}
		if (path.endsWith('/unread-count')) { return ok(route, 0); }
		if (path.endsWith('/categories') || path.endsWith('/related')) { return ok(route, []); }
		if (path.endsWith('/adjacent')) { return ok(route, {}); }
		return ok(route);
	});
});
afterEach(async context => {
	const layout = await page.evaluate(() => ({ width: innerWidth, documentWidth: document.documentElement.scrollWidth }));
	assert.equal(layout.documentWidth, layout.width, 'The viewport must not overflow horizontally');
	if (process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR,
			`${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 130)}.png`), fullPage: true });
	}
	traces.push({ name: context.name, reads, logoutActors: logouts.map(entry => entry.actor), layout, pageErrors: errors });
	if (process.env.QA_TRACE_PATH) {
		await mkdir(dirname(process.env.QA_TRACE_PATH), { recursive: true });
		await writeFile(process.env.QA_TRACE_PATH, JSON.stringify(traces, null, 2) + '\n');
	}
	await page.close(); assert.deepEqual(errors, []);
});

async function open(width, { fixture = false } = {}) {
	await page.setViewportSize({ width, height: 1000 });
	await page.goto(origin + (fixture ? 'tests/editor-comments.html' : 'posts/1'));
	if (fixture) {
		await page.waitForFunction(() => Boolean(window.fixture));
		await page.evaluate(async member => {
			await window.fixture.member(member);
			await window.fixture.comments({ total: 2, items: [{ id: 101, author: '회원', content: 'fixture 댓글',
				mine: true, likedByMe: true, dislikedByMe: false, likeCount: 5, dislikeCount: 0,
				replies: [{ id: 104, author: '답글 회원', content: 'fixture 답글', mine: false,
					likedByMe: false, dislikedByMe: true, likeCount: 0, dislikeCount: 2 }] }], hasNext: false });
		}, member);
	} else {
		await page.locator('#comment-202').getByText('글1 회원7 댓글202', { exact: true }).waitFor();
		await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).ensureMemberSession());
	}
	await input().waitFor(); await tick();
}
async function switchMember(next, { advance = true } = {}) {
	const previous = member?.id ?? null, count = reads.length; member = next;
	await page.evaluate(async advance => {
		if (advance) { (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration(); }
		await (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession();
	}, advance);
	if (previous !== (next?.id ?? null)) {
		await page.locator('#comment-202').getByText(`글1 회원${next?.id ?? 'anon'} 댓글202`, { exact: true }).waitFor();
		assert.ok(reads.length > count, 'The real parent must GET a fresh account-specific list');
	}
	await tick();
}
async function drafts(prefix = 'B') {
	await input().fill(`${prefix} 댓글 초안`);
	await page.locator('#comment-101').getByRole('button', { name: '답글', exact: true }).click();
	await page.locator('.reply-form__input').fill(`${prefix} 답글 초안`);
}
async function start() {
	const request = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/auth/logout');
	await logout().click(); await request; return logouts.at(-1);
}
async function release(entry, { fails = false, clear = true } = {}) {
	const response = page.waitForResponse(response => response.request() === entry.route.request());
	if (fails) { await entry.route.fulfill({ status: 500, json: { success: false, code: 'TEST_LOGOUT_FAILURE' } }); }
	else { if (clear) { member = null; } await ok(entry.route); }
	await (await response).finished(); await tick();
}
async function noError() { assert.equal(await page.getByText(logoutError, { exact: true }).count(), 0); }
async function capture(label, width) {
	if (process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, `${label}-${width}.png`), fullPage: true });
	}
}
async function currentMember() {
	return page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().member.value?.id ?? null);
}
async function toastIds() {
	return page.evaluate(async () => (await import('/src/shared/toast/toastStore.js')).useToasts().toasts.value.map(toast => toast.id));
}
async function navigate(path) {
	await page.evaluate(async path => (await import('/src/app/router/index.js')).default.push(path), path);
	await tick();
}
async function keepDrafts(prefix = 'B') {
	assert.equal(await input().inputValue(), `${prefix} 댓글 초안`);
	assert.equal(await page.locator('.reply-form__input').inputValue(), `${prefix} 답글 초안`);
}

for (const width of [1280, 390]) {
	for (const advance of [false, true]) {
		test(`${width}: a separate anonymous refresh suppresses the previous logout failure (auth advance=${advance})`, async context => {
			await open(width); const old = await start();
			await switchMember(null, { advance });
			await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
			assert.deepEqual(reads.map(read => read.actor), [7, null]);
			await noError();
			await release(old, { fails: true, clear: false });
			assert.equal(await currentMember(), null); await noError(); assert.equal(logouts.length, 1);
			context.diagnostic(`real refreshMemberSession GET actors=7,null; auth advance=${advance}; old logout requests=1`);
		});
	}

	test(`${width}: actual store stale resolve keeps B drafts and current personal reaction flags`, async context => {
		await open(width); const old = await start();
		await switchMember({ ...accountB }); await drafts();
		await capture('B-drafts-before-old-resolve', width);
		await release(old, { clear: false });
		const currentId = await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().member.value?.id);
		context.diagnostic(`MemberAuthStore kept member=${currentId}; GET actors=${reads.map(read => read.actor)}`);
		assert.equal(currentId, 8); await keepDrafts();
		assert.equal(await page.locator('#comment-101 button[aria-label="좋아요"]').first().getAttribute('aria-pressed'), 'true');
		assert.equal(await page.locator('#comment-104 button[aria-label="좋아요"]').getAttribute('aria-pressed'), 'true');
		await noError();
	});

	test(`${width}: B logout starts while A is pending and A finally cannot release the new pending`, async () => {
		await open(width); const old = await start();
		await switchMember({ ...accountB }); await drafts();
		await logout().click(); await tick(); assert.equal(logouts.length, 2);
		const current = logouts[1];
		await release(old, { fails: true, clear: false }); await keepDrafts(); await noError();
		await logout().click(); await tick(); assert.equal(logouts.length, 2);
		await release(current);
		await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
	});

	test(`${width}: A logout 500 does not show a current B logout error`, async () => {
		await open(width); const old = await start();
		await switchMember({ ...accountB }); await drafts();
		await release(old, { fails: true, clear: false }); await keepDrafts(); await noError();
	});

	for (const fails of [false, true]) {
		test(`${width}: old ${fails ? 'failure' : 'success'} preserves a newer B pending logout and its later error`, async () => {
			await open(width); const old = await start(); await switchMember({ ...accountB }); await drafts();
			const current = await start(); assert.equal(logouts.length, 2);
			await release(old, { fails, clear: false }); await keepDrafts(); await noError();
			await logout().dblclick(); await tick(); assert.equal(logouts.length, 2);
			await release(current, { fails: true, clear: false });
			await page.getByText(logoutError, { exact: true }).waitFor(); await keepDrafts();
		});

		test(`${width}: A to B to A cannot revive the first logout ${fails ? 'failure' : 'success'}`, async () => {
			await open(width); const old = await start();
			await switchMember({ ...accountB }); await switchMember({ ...accountA }); await drafts('새 A');
			const current = await start();
			await release(old, { fails, clear: false }); await keepDrafts('새 A'); await noError();
			await logout().click(); await tick(); assert.equal(logouts.length, 2);
			await release(current); await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
		});

		for (const boundary of ['post', 'unmount']) {
			test(`${width}: ${boundary} ignores an old logout ${fails ? 'failure' : 'success'} after a real B session`, async () => {
				await open(width); const old = await start(); await switchMember({ ...accountB });
				await navigate(boundary === 'post' ? '/posts/2' : '/privacy');
				if (boundary === 'post') {
					await page.locator('#comment-202').getByText('글2 회원8 댓글202', { exact: true }).waitFor();
					await drafts();
				}
				await release(old, { fails, clear: false }); await noError(); assert.equal(await currentMember(), 8);
				if (boundary === 'post') { await keepDrafts(); }
				else { assert.equal(await input().count(), 0); }
			});
		}
	}

	test(`${width}: same ID with a new authentication generation separates the next logout`, async () => {
		await open(width); const old = await start(); await switchMember({ ...accountA }); await drafts('새 A');
		const current = await start(); assert.equal(logouts.length, 2);
		await release(old, { clear: false }); await keepDrafts('새 A'); await noError();
		await logout().click(); await tick(); assert.equal(logouts.length, 2);
		await release(current); await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
	});

	test(`${width}: getter-only change is checked before the next action and on an old failure`, async () => {
		await open(width); const old = await start();
		await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration());
		await drafts('새 A'); const current = await start(); assert.equal(logouts.length, 2);
		await release(old, { fails: true, clear: false }); await keepDrafts('새 A'); await noError();
		await logout().click(); await tick(); assert.equal(logouts.length, 2);
		await release(current); await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
	});

	test(`${width}: a same-account session refresh keeps drafts when the store returns without logout`, async () => {
		await open(width); await drafts('A'); const old = await start();
		await switchMember({ ...accountA }, { advance: false });
		await logout().click(); await tick(); assert.equal(logouts.length, 1);
		await release(old, { clear: false }); await keepDrafts('A'); await noError(); assert.equal(await currentMember(), 7);
		const retry = await start(); assert.equal(logouts.length, 2); await release(retry);
		await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
	});

	for (const fails of [false, true]) {
		test(`${width}: nickname refresh preserves a valid logout ${fails ? 'failure and retry' : 'success'}`, async () => {
			await open(width); await drafts('A'); const pending = await start();
			await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).changeNickname('새 닉네임'));
			await logout().dblclick(); await tick(); assert.equal(logouts.length, 1);
			await release(pending, { fails });
			if (fails) {
				await page.getByText(logoutError, { exact: true }).waitFor(); await keepDrafts('A'); assert.equal(await currentMember(), 7);
				const retry = await start(); await release(retry);
			}
			await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor(); await noError();
		});
	}

	test(`${width}: editor fixture retains explicit draft reply and personal reaction cleanup after confirmed logout`, async () => {
		await open(width, { fixture: true }); await drafts('A');
		const pending = await start(); await release(pending);
		await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
		assert.equal(await page.locator('.reply-form__input').count(), 0);
		assert.equal(await page.locator('#comment-101 button[aria-label="좋아요"]').first().getAttribute('aria-pressed'), 'false');
		assert.equal(await page.locator('#comment-104 button[aria-label="싫어요"]').getAttribute('aria-pressed'), 'false');
		await page.evaluate(async member => window.fixture.member(member), accountA);
		assert.equal(await input().inputValue(), ''); await noError();
	});

	test(`${width}: only this logout error is dismissed on identity change`, async () => {
		await open(width); const pending = await start(); await release(pending, { fails: true });
		await page.getByText(logoutError, { exact: true }).waitFor();
		await page.evaluate(async () => (await import('/src/shared/toast/toastStore.js')).notifyError('다른 기능 알림'));
		await switchMember({ ...accountB }); await noError();
		await page.getByText('다른 기능 알림', { exact: true }).waitFor(); assert.equal((await toastIds()).length, 1);
	});

	test(`${width}: an old completion keeps the finished newer B logout error toast`, async () => {
		await open(width); const old = await start(); await switchMember({ ...accountB });
		const current = await start(); await release(current, { fails: true });
		await page.getByText(logoutError, { exact: true }).waitFor(); const ids = await toastIds();
		await release(old, { fails: true, clear: false }); assert.deepEqual(await toastIds(), ids);
		assert.equal(await currentMember(), 8); assert.equal(logouts.length, 2);
	});
}
