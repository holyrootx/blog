import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const memberA = { id: 1, nickname: '검사 회원 A', role: 'USER' };
const memberB = { id: 2, nickname: '검사 회원 B', role: 'USER' };
const oldError = '이전 좋아요 요청의 오류';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
let server, browser, page, origin, member, errors, initialLiked, logoutFails;
let heldDetails, heldReactions, onReaction, holdPostTwo;
const like = () => page.locator('.post-reactions .ui-button--like');
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
const view = async () => ({ pressed: await like().getAttribute('aria-pressed'), disabled: await like().isDisabled(), count: await like().locator('strong').textContent() });

before(async () => {
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen();
	origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});

after(async () => {
	try { await browser?.close(); } finally { await server?.close(); }
});

beforeEach(async () => {
	member = { ...memberA };
	errors = [];
	initialLiked = false;
	logoutFails = false;
	holdPostTwo = false;
	heldDetails = [];
	heldReactions = [];
	onReaction = null;
	page = await browser.newPage({ serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/*', async route => {
		const request = route.request();
		const url = new URL(request.url()), path = url.pathname;
		if (url.origin !== new URL(origin).origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/')) { return route.continue(); }
		if (path === '/api/v1/auth/me') { return ok(route, member); }
		if (path === '/api/v1/auth/csrf') { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test-only-csrf' }); }
		if (path === '/api/v1/auth/logout') {
			if (logoutFails) { return route.fulfill({ status: 500, json: { success: false, message: '테스트 로그아웃 실패' } }); }
			member = null;
			return ok(route);
		}
		if (path === '/api/v1/auth/me/nickname') {
			member = { ...member, nickname: request.postDataJSON().nickname };
			return ok(route, member);
		}
		if (/\/posts\/\d+\/reaction$/.test(path)) {
			const entry = { route, method: request.method() };
			heldReactions.push(entry);
			onReaction?.(entry);
			onReaction = null;
			return;
		}
		if (/\/posts\/\d+$/.test(path)) {
			const id = Number(path.split('/').pop());
			const data = { id, title: `반응 검사 글 ${id}`, content: '응답과 화면의 주체를 확인합니다.', excerpt: '가짜 검사 자료',
				categoryId: 1, categoryName: '검사', publishedAt: '2026-11-16T09:00:00', views: 1,
				likeCount: id === 2 ? 20 : 3, dislikeCount: 2, likedByMe: id === 1 && initialLiked, dislikedByMe: false };
			if (id === 2 && holdPostTwo) { heldDetails.push({ route, data }); return; }
			return ok(route, data);
		}
		if (path.endsWith('/comments')) { return ok(route, { total: 0, items: [], hasNext: false, nextCursor: null }); }
		if (path.endsWith('/categories') || path.endsWith('/related')) { return ok(route, []); }
		if (path.endsWith('/adjacent')) { return ok(route, {}); }
		return ok(route);
	});
});

afterEach(async (context) => {
	try {
		if (page && process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 120)}.png`, fullPage: true });
		}
	} finally { await page?.close(); }
	assert.deepEqual(errors, []);
});

async function openPost(width, id = 1) {
	await page.setViewportSize({ width, height: 900 });
	await page.goto(`${origin}posts/${id}`);
	await page.getByRole('heading', { name: `반응 검사 글 ${id}`, exact: true }).waitFor();
	await page.waitForFunction(async () => (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().member.value?.id === 1);
	await settled();
}

async function beginReaction() {
	const captured = new Promise(resolve => { onReaction = resolve; });
	await like().click();
	return captured;
}

async function release(entry, fails = false, liked = true) {
	const received = page.waitForResponse(response => response.request() === entry.route.request());
	if (fails) {
		await entry.route.fulfill({ status: 500, json: { success: false, message: oldError } });
	} else {
		await ok(entry.route, { likeCount: liked ? 4 : 3, dislikeCount: 2, likedByMe: liked, dislikedByMe: false });
	}
	await (await received).finished();
	await settled();
}

async function changeMember(next, advance = false) {
	member = next;
	await page.evaluate(async advance => {
		if (advance) { (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration(); }
		await (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession();
	}, advance);
	await settled();
}

async function navigate(path) {
	await page.evaluate(async path => {
		await (await import('/src/app/router/index.js')).default.push(path);
	}, path);
	await settled();
}

async function assertNoOldError() {
	assert.equal(await page.getByText(oldError, { exact: true }).count(), 0);
}

for (const width of [1280, 390]) {
	test(`${width}: normal toggle and remove retain their server results`, async () => {
		await openPost(width);
		const added = await beginReaction();
		assert.equal(added.method, 'PUT');
		await release(added);
		assert.deepEqual(await view(), { pressed: 'true', disabled: false, count: '4' });
		const removed = await beginReaction();
		assert.equal(removed.method, 'DELETE');
		await release(removed, false, false);
		assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '3' });
	});

	for (const fails of [false, true]) {
		test(`${width}: anonymous session ignores late ${fails ? 'failure' : 'success'} and releases old pending`, async () => {
			await openPost(width);
			const pending = await beginReaction();
			await changeMember(null);
			const before = await view();
			await release(pending, fails);
			assert.equal(before.disabled, false);
			assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '3' });
			await assertNoOldError();
		});

		test(`${width}: old ${fails ? 'failure' : 'success'} cannot release the new member request`, async () => {
			await openPost(width);
			const old = await beginReaction();
			await changeMember({ ...memberB });
			assert.equal(await like().isDisabled(), false);
			const next = await beginReaction();
			await release(old, fails);
			assert.equal(await like().isDisabled(), true);
			assert.equal(await like().getAttribute('aria-pressed'), 'false');
			await assertNoOldError();
			await release(next);
			assert.deepEqual(await view(), { pressed: 'true', disabled: false, count: '4' });
		});
	}

	test(`${width}: A to B to A cannot revive the first A response`, async () => {
		await openPost(width);
		const old = await beginReaction();
		await changeMember({ ...memberB });
		await changeMember({ ...memberA });
		await release(old);
		assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '3' });
	});

	test(`${width}: same ID with a new authentication generation protects a newer pending request`, async () => {
		await openPost(width);
		const old = await beginReaction();
		await changeMember({ ...memberA }, true);
		assert.equal(await like().isDisabled(), false);
		const next = await beginReaction();
		await release(old);
		assert.equal(await like().isDisabled(), true);
		await release(next);
		assert.equal(await like().getAttribute('aria-pressed'), 'true');
	});

	test(`${width}: authentication generation alone is checked at response completion`, async () => {
		await openPost(width);
		const old = await beginReaction();
		await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration());
		await release(old);
		assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '3' });
	});

	test(`${width}: changing member clears existing own reaction and error without changing totals`, async () => {
		initialLiked = true;
		await openPost(width);
		await page.evaluate(async () => {
			(await import('/src/shared/toast/toastStore.js')).notifyError('다른 작업의 오류');
		});
		const old = await beginReaction();
		await release(old, true);
		assert.equal(await page.locator('.post-reactions__error').textContent(), oldError);
		await changeMember({ ...memberB });
		assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '3' });
		assert.equal(await page.locator('.post-reactions__error').count(), 0);
		await assertNoOldError();
		assert.equal(await page.getByText('다른 작업의 오류', { exact: true }).count(), 1);
		const next = await beginReaction();
		assert.equal(next.method, 'PUT');
		await release(next);
	});

	test(`${width}: failed logout and nickname refresh preserve a valid request`, async () => {
		await openPost(width);
		const pending = await beginReaction();
		logoutFails = true;
		await page.evaluate(async () => {
			const auth = await import('/src/features/member/data/memberAuthStore.js');
			await auth.signOutMember().catch(() => null);
			await auth.changeNickname('갱신한 닉네임');
		});
		assert.equal(await like().isDisabled(), true);
		await release(pending);
		assert.deepEqual(await view(), { pressed: 'true', disabled: false, count: '4' });
	});

	test(`${width}: cached post reuse rejects the previous post response`, async (context) => {
		await openPost(width, 2);
		await navigate('/posts/1');
		await page.getByRole('heading', { name: '반응 검사 글 1', exact: true }).waitFor();
		const pending = await beginReaction();
		const oldInstance = await page.locator('.post-article').evaluate(element => element.__vueParentComponent.uid);
		holdPostTwo = true;
		await navigate('/posts/2');
		await page.getByRole('heading', { name: '반응 검사 글 2', exact: true }).waitFor();
		const nextInstance = await page.locator('.post-article').evaluate(element => element.__vueParentComponent.uid);
		assert.equal(nextInstance, oldInstance);
		context.diagnostic('cached 전환에서 PostArticle 인스턴스 재사용 확인');
		await release(pending);
		assert.deepEqual(await view(), { pressed: 'false', disabled: false, count: '20' });
		for (const held of heldDetails) { await ok(held.route, held.data); }
	});

	test(`${width}: completion after unmount does not show an old error on another screen`, async () => {
		await openPost(width);
		const pending = await beginReaction();
		await navigate('/privacy');
		assert.equal(await page.locator('.post-article').count(), 0);
		await release(pending, true);
		await assertNoOldError();
	});
}
