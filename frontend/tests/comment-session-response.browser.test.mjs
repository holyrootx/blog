import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';

const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const failureMessage = '이전 댓글 요청의 실패';
const originalMember = { id: 7, nickname: '작성자 A', role: 'USER' };
const otherMember = { id: 8, nickname: '작성자 B', role: 'USER' };
const content = '늦게 도착할 댓글';
const rawComment = (id, nickname, text, mine = false) => ({
	id, nickname, author: nickname, content: text, mine, createdAt: '2026-11-15T04:00:00',
	deleted: false, likeCount: 0, dislikeCount: 0, likedByMe: false, dislikedByMe: false, replies: [],
});
let server;
let browser;
let page;
let origin;
let member;
let reply;
let created;
let heldCreate;
let createCount;
let logoutFails;
let errors;

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
	const row = rawComment(902, originalMember.nickname, content, member?.id === originalMember.id);
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
	heldCreate = null;
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
		if (path === '/api/v1/blog/posts/1/comments') {
			if (request.method() === 'POST') {
				createCount += 1;
				created = true;
				heldCreate = route;
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
		await page.evaluate(async ({ member, comments }) => {
			await window.fixture.member(member);
			await window.fixture.comments(comments);
		}, { member, comments: comments() });
	} else {
		await page.getByRole('heading', { name: '댓글 세션 검사', exact: true }).waitFor();
		await page.locator('.comment-form__input').waitFor();
	}
	if (reply) await openReply();
	await input().fill(content);
	const request = page.waitForRequest(request => request.method() === 'POST' && request.url().endsWith('/posts/1/comments'));
	await page.locator(reply ? '.reply-form' : '.comment-form').getByRole('button', { name: reply ? '답글 등록' : '등록', exact: true }).click();
	await request;
	assert.ok(heldCreate);
}
async function releaseCreate(fails = false) {
	const response = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/posts/1/comments'));
	if (fails) {
		await heldCreate.fulfill({ status: 500, json: { success: false, message: failureMessage } });
	} else {
		await ok(heldCreate, { id: 902 });
	}
	heldCreate = null;
	await response;
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

for (const isReply of [false, true]) {
	const label = isReply ? 'reply' : 'comment';
	test(`${label}: normal completion inserts one row with the submitting author`, async () => {
		reply = isReply;
		await beginCreate();
		await releaseCreate();
		await assertSingleCreated();
	});
	test(`${label}: late success after logout cannot duplicate the reloaded anonymous list`, async () => {
		reply = isReply;
		await beginCreate();
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await page.getByRole('button', { name: '로그인하고 댓글 쓰기', exact: true }).waitFor();
		await page.locator('#comment-902').waitFor();
		await releaseCreate();
		await assertSingleCreated();
		assert.equal(await page.locator('.comments').getByRole('button', { name: /^(수정|삭제)$/ }).count(), 0);
	});
	test(`${label}: changing accounts and returning ignores the earlier session response`, async () => {
		reply = isReply;
		await beginCreate();
		await changeMember({ ...otherMember });
		await changeMember({ ...originalMember });
		if (reply) await openReply();
		await input().fill('새 세션의 초안');
		await releaseCreate();
		await assertSingleCreated();
		assert.equal(await input().inputValue(), '새 세션의 초안');
		assert.equal(await input().isDisabled(), false);
	});
	test(`${label}: an old failure after account replacement preserves the new draft and shows no old error`, async () => {
		reply = isReply;
		await beginCreate();
		await changeMember({ ...otherMember });
		if (reply) await openReply();
		await input().fill('B의 초안');
		await releaseCreate(true);
		assert.equal(await input().inputValue(), 'B의 초안');
		assert.equal(await page.getByText(failureMessage, { exact: true }).count(), 0);
		assert.equal(await page.locator('.comments').getByRole('button', { name: /^(수정|삭제)$/ }).count(), 0);
	});
	test(`${label}: failed logout retains the draft and lets the pending creation finish`, async () => {
		reply = isReply;
		logoutFails = true;
		await beginCreate();
		await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
		await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
		assert.equal(await input().inputValue(), content);
		await releaseCreate();
		await assertSingleCreated();
	});
	test(`${label}: a row received through the feed while creation is pending is not inserted or counted twice`, async () => {
		reply = isReply;
		await beginCreate(true);
		await page.evaluate(value => window.fixture.comments(value), comments());
		await page.locator('#comment-902').waitFor();
		await releaseCreate();
		await assertSingleCreated();
	});
}

test('a same-account authentication generation change discards the old completion', async () => {
	await beginCreate();
	await page.evaluate(async () => {
		const { advanceAuthenticationGeneration } = await import('/src/shared/api/blogApiClient.js');
		advanceAuthenticationGeneration();
	});
	await input().fill('새 인증의 초안');
	await releaseCreate();
	assert.equal(await page.locator('#comment-902').count(), 0);
	assert.equal((await page.locator('.comments__count').textContent()).trim(), '0');
	assert.equal(await input().inputValue(), '새 인증의 초안');
	assert.equal(await page.locator('.comment-form').getByRole('button', { name: '등록', exact: true }).isDisabled(), false);
});

test('a nickname refresh during a request does not rewrite the captured author', async () => {
	await beginCreate();
	member = { ...originalMember, nickname: '바뀐 닉네임' };
	await page.evaluate(async () => {
		const { refreshMemberSession } = await import('/src/features/member/data/memberAuthStore.js');
		await refreshMemberSession();
	});
	await releaseCreate();
	await assertSingleCreated();
});
