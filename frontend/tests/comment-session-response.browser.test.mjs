import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const failureMessage = '이전 댓글 요청의 실패';
const createPath = '/api/v1/blog/posts/1/comments';
const isCreateRequest = request => request.method() === 'POST' && new URL(request.url()).pathname === createPath;
const originalMember = { id: 7, nickname: '작성자 A', profileImageUrl: null, role: 'ROLE_USER' };
const otherMember = { id: 8, nickname: '작성자 B', profileImageUrl: null, role: 'ROLE_USER' };
const content = '늦게 도착할 댓글';
const rawComment = (id, nickname, text, fields = {}) => ({
	id, nickname, content: text, mine: false, authorComment: false, createdAt: '2026-11-15T04:00:00',
	deleted: false, likeCount: 0, dislikeCount: 0, likedByMe: false, dislikedByMe: false, replies: [],
	...fields,
});
let server;
let browser;
let page;
let origin;
let member;
let reply;
let created;
let createDelivery;
let createDeliveryGate;
let createCount;
let logoutFails;
let errors;
let createdAuthor;

before(async () => {
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen();
	origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });

function comments() {
	const row = rawComment(902, createdAuthor?.nickname, content, {
		mine: member?.id === createdAuthor?.id,
		authorComment: createdAuthor?.role === 'ROLE_ADMIN',
	});
	let items = created ? [row] : [];
	if (reply) {
		const parent = rawComment(901, '다른 회원', '답글 대상');
		parent.replies = items;
		items = [parent];
	}
	return { total: (reply ? 1 : 0) + (created ? 1 : 0), items, hasNext: false, nextCursor: null };
}

beforeEach(async () => {
	member = { ...originalMember };
	reply = false;
	created = false;
	createdAuthor = null;
	createDelivery = null;
	createDeliveryGate = null;
	createCount = 0;
	logoutFails = false;
	errors = [];
	page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	page.on('console', message => {
		if (message.type() === 'warning' && message.text().includes('[Vue warn]')) errors.push(message.text());
	});
	await page.route('**/*', async route => {
		const request = route.request();
		const url = new URL(request.url());
		if (url.origin !== new URL(origin).origin) return route.abort('blockedbyclient');
		if (!url.pathname.startsWith('/api/v1/')) return route.continue();
		const path = url.pathname;
		if (path === '/api/v1/auth/me') return ok(route, member);
		if (path === '/api/v1/auth/csrf') return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'comment-test-token' });
		if (path === '/api/v1/auth/logout') {
			if (logoutFails) return route.fulfill({ status: 500, json: { success: false, message: '로그아웃 실패' } });
			member = null;
			return ok(route);
		}
		if (path === '/api/v1/blog/posts/1') return ok(route, {
			id: 1, title: '댓글 세션 검사', excerpt: '검사 자료', content: '댓글 응답 순서를 검사합니다.',
			categoryId: 1, categoryName: '검사', publishedAt: '2026-11-14T09:00:00', views: 1,
		});
		if (path === createPath) {
			if (isCreateRequest(request)) {
				const delivery = createDelivery;
				assert.ok(delivery, 'register the route waiter before submitting');
				createDelivery = null;
				createCount += 1;
				createdAuthor = { ...member };
				created = true;
				if (createDeliveryGate) {
					createDeliveryGate.entered.resolve(request);
					await createDeliveryGate.release.promise;
				}
				delivery.resolve({ route, request, responses: 0 });
				return;
			}
			return ok(route, comments());
		}
		if (path.endsWith('/related') || path.endsWith('/categories')) return ok(route, []);
		if (path.endsWith('/adjacent')) return ok(route, {});
		return ok(route);
	});
});
afterEach(async () => {
	await page?.close();
	assert.deepEqual(errors, []);
});

const tick = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
const input = () => page.locator(reply ? '.reply-form textarea' : '.comment-form__input');
async function openReply() {
	await page.locator('#comment-901').getByRole('button', { name: '답글', exact: true }).click();
	await input().waitFor();
}
async function beginCreate(useFixture = false) {
	await page.goto(origin + (useFixture ? 'tests/editor-comments.html' : 'posts/1'));
	if (useFixture) {
		await page.waitForFunction(() => typeof window.fixture?.comments === 'function');
		await page.evaluate(async member => {
			await window.fixture.member(member);
		}, member);
		await refreshFixtureComments();
	} else {
		await page.getByRole('heading', { name: '댓글 세션 검사', exact: true }).waitFor();
		await page.locator('.comment-form__input').waitFor();
	}
	if (reply) await openReply();
	await input().fill(content);
	assert.equal(createDelivery, null);
	const delivery = Promise.withResolvers();
	createDelivery = delivery;
	const request = page.waitForRequest(isCreateRequest);
	await page.locator(reply ? '.reply-form' : '.comment-form').getByRole('button', { name: reply ? '답글 등록' : '등록', exact: true }).click();
	createDeliveryGate?.clicked.resolve();
	const [sent, creation] = await Promise.all([request, delivery.promise]);
	assert.equal(creation.request, sent);
	assert.equal(creation.route.request(), sent);
	return creation;
}
async function releaseCreate(creation, fails = false) {
	const { route, request } = creation;
	assert.equal(route.request(), request);
	assert.equal(creation.responses, 0);
	const response = page.waitForResponse(response => response.request() === request);
	creation.responses += 1;
	if (fails) {
		await route.fulfill({ status: 500, json: { success: false, message: failureMessage } });
	} else {
		await ok(route, { id: 902 });
	}
	const received = await response;
	assert.equal(received.status(), fails ? 500 : 200);
	await received.finished();
	assert.equal(createCount, 1);
	await tick();
}

async function changeMember(next) {
	member = next;
	await page.evaluate(async () => {
		const { refreshMemberSession } = await import('/src/features/member/data/memberAuthStore.js');
		await refreshMemberSession();
	});
	await page.locator('.comment-form__input').waitFor();
	await page.locator('[id="comment-902"]').waitFor();
}
async function assertSingleCreated() {
	assert.equal(await page.locator('[id="comment-902"]').count(), 1);
	assert.equal(await page.locator('#comment-902 .comment__author').textContent(), originalMember.nickname);
	assert.equal((await page.locator('.comments__count').textContent()).trim(), reply ? '2' : '1');
	assert.equal(createCount, 1);
}
async function refreshFixtureComments() {
	await page.evaluate(async () => {
		const { getPostComments } = await import('/src/features/post/api/postApi.js');
		await window.fixture.comments(await getPostComments(1));
	});
}

for (const isReply of [false, true]) {
	const label = isReply ? 'reply' : 'comment';

	test(`${label}: a POST request event cannot stand in for its route handler`, { timeout: 10000 }, async (context) => {
		reply = isReply;
		const gate = { entered: Promise.withResolvers(), clicked: Promise.withResolvers(), release: Promise.withResolvers() };
		createDeliveryGate = gate;
		const requestEvent = page.waitForRequest(isCreateRequest);
		let settled = false;
		const starting = beginCreate().then(value => { settled = true; return { value }; }, error => { settled = true; return { error }; });
		let returnedWhileHeld;
		let request;
		try {
			const [sent, entered] = await Promise.all([requestEvent, gate.entered.promise, gate.clicked.promise]);
			request = sent;
			assert.equal(entered, request);
			assert.deepEqual(request.postDataJSON(), { content, parentId: isReply ? 901 : null });
			// Drain the caller's microtasks after the click exits while delivery is still held.
			await new Promise(resolve => setImmediate(resolve));
			returnedWhileHeld = settled;
			context.diagnostic(JSON.stringify({ reply: isReply, requestCount: createCount, returnedWhileHeld }));
		} finally {
			gate.release.resolve();
		}
		const result = await starting;
		if ('error' in result) throw result.error;
		assert.equal(returnedWhileHeld, false);
		assert.equal(result.value.request, request);
		assert.equal(result.value.route.request(), request);
		await releaseCreate(result.value);
		assert.equal(result.value.responses, 1);
		await assertSingleCreated();
	});
	test(`${label}: normal completion inserts one row with the submitting author`, async () => {
		reply = isReply;
		const creation = await beginCreate();
		await releaseCreate(creation);
		await assertSingleCreated();
	});
	for (const role of ['ROLE_ADMIN', 'ROLE_USER']) {
		test(`${label}: ${role} has the same author badge immediately and after a server reload`, async (t) => {
			reply = isReply;
			member = { ...originalMember, role };
			const creation = await beginCreate();
			await releaseCreate(creation);
			await assertSingleCreated();
			const immediate = await page.locator('#comment-902 .comment__badge').count();
			await page.reload();
			await page.locator('#comment-902').waitFor();
			const reloaded = await page.locator('#comment-902 .comment__badge').count();
			t.diagnostic(JSON.stringify({ role, reply: isReply, immediate, reloaded }));
			assert.equal(reloaded, role === 'ROLE_ADMIN' ? 1 : 0);
			assert.equal(immediate, reloaded, 'created rows must agree with authorComment from the server');
			await assertSingleCreated();
		});
	}
	test(`${label}: late success after logout cannot duplicate the reloaded anonymous list`, async () => {
		reply = isReply;
		const creation = await beginCreate();
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
		await page.locator('#comment-902').waitFor();
		await releaseCreate(creation);
		await assertSingleCreated();
		assert.equal(await page.locator('.comments').getByRole('button', { name: /^(수정|삭제)$/ }).count(), 0);
	});
	for (const role of ['ROLE_ADMIN', 'ROLE_USER']) {
		test(`${label}: changing ${role} accounts and returning ignores the earlier session response`, async () => {
			reply = isReply;
			member = { ...originalMember, role };
			const creation = await beginCreate();
			await changeMember({ ...otherMember });
			await changeMember({ ...originalMember, role });
			if (reply) await openReply();
			await input().fill('새 세션의 초안');
			await releaseCreate(creation);
			await assertSingleCreated();
			assert.equal(await page.locator('#comment-902 .comment__badge').count(), role === 'ROLE_ADMIN' ? 1 : 0);
			assert.equal(await input().inputValue(), '새 세션의 초안');
			assert.equal(await input().isDisabled(), false);
		});
	}
	test(`${label}: an old failure after account replacement preserves the new draft and shows no old error`, async () => {
		reply = isReply;
		const creation = await beginCreate();
		await changeMember({ ...otherMember });
		if (reply) await openReply();
		await input().fill('B의 초안');
		await releaseCreate(creation, true);
		assert.equal(await input().inputValue(), 'B의 초안');
		assert.equal(await page.getByText(failureMessage, { exact: true }).count(), 0);
		assert.equal(await page.locator('.comments').getByRole('button', { name: /^(수정|삭제)$/ }).count(), 0);
	});
	test(`${label}: failed logout retains the draft and lets the pending creation finish`, async () => {
		reply = isReply;
		logoutFails = true;
		const creation = await beginCreate();
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
		assert.equal(await input().inputValue(), content);
		await releaseCreate(creation);
		await assertSingleCreated();
	});
	test(`${label}: a row received through the feed while creation is pending is not inserted or counted twice`, async () => {
		reply = isReply;
		const creation = await beginCreate(true);
		await refreshFixtureComments();
		await page.locator('#comment-902').waitFor();
		await releaseCreate(creation);
		await assertSingleCreated();
	});
}

test('a same-account authentication generation change discards the old completion', async () => {
	const creation = await beginCreate();
	await page.evaluate(async () => {
		const { advanceAuthenticationGeneration } = await import('/src/shared/api/blogApiClient.js');
		advanceAuthenticationGeneration();
	});
	await input().fill('새 인증의 초안');
	await releaseCreate(creation);
	assert.equal(await page.locator('#comment-902').count(), 0);
	assert.equal((await page.locator('.comments__count').textContent()).trim(), '0');
	assert.equal(await input().inputValue(), '새 인증의 초안');
	assert.equal(await page.locator('.comment-form').getByRole('button', { name: '등록', exact: true }).isDisabled(), false);
});

for (const isReply of [false, true]) {
	for (const role of ['ROLE_ADMIN', 'ROLE_USER']) {
		test(`${role} ${isReply ? 'reply' : 'comment'}: a nickname refresh does not rewrite the captured author`, async () => {
			reply = isReply;
			member = { ...originalMember, role };
			const creation = await beginCreate();
			member = { ...member, nickname: '바뀐 닉네임' };
			await page.evaluate(async () => {
				const { refreshMemberSession } = await import('/src/features/member/data/memberAuthStore.js');
				await refreshMemberSession();
			});
			await releaseCreate(creation);
			await assertSingleCreated();
			assert.equal(await page.locator('#comment-902 .comment__badge').count(), role === 'ROLE_ADMIN' ? 1 : 0);
		});
	}
}
