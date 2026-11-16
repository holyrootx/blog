import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const memberA = { id: 7, nickname: '검사 회원 A', role: 'ROLE_USER' };
const memberB = { id: 8, nickname: '검사 회원 B', role: 'ROLE_USER' };
const oldError = '이전 회원의 댓글 관리 오류';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
let server, browser, page, cacheDir, origin, member, reads, writes, errors, logoutFails;
const traces = [];
const row = id => page.locator(`#comment-${id}`);
const marker = (id, postId = 1) => `글${postId} 회원${member?.id ?? 'anon'} 댓글${id}`;
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-comment-management-'));
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen(); origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	member = { ...memberA }; reads = []; writes = []; errors = []; logoutFails = false;
	page = await browser.newPage({ serviceWorkers: 'block' }); page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/*', route => {
		const request = route.request(), url = new URL(request.url()), path = url.pathname;
		if (url.origin !== new URL(origin).origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/v1/')) { return route.continue(); }
		if (path === '/api/v1/auth/me') { return ok(route, member); }
		if (path.endsWith('/csrf')) { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'fake-management-csrf' }); }
		if (path === '/api/v1/auth/logout') {
			if (logoutFails) { return route.fulfill({ status: 500, json: { success: false, message: '가짜 로그아웃 실패' } }); }
			member = null; return ok(route);
		}
		if (/\/posts\/\d+$/.test(path)) {
			const id = Number(path.split('/').pop());
			return ok(route, { id, title: `댓글 관리 검사 ${id}`, content: '실제 부모와 계정별 새 댓글 응답을 검사합니다.',
				categoryId: 1, categoryName: '검사', publishedAt: '2026-11-17T00:00:00', views: 1 });
		}
		const comments = /^\/api\/v1\/blog\/posts\/(\d+)\/comments$/.exec(path);
		if (comments && request.method() === 'GET') {
			const postId = Number(comments[1]); reads.push({ memberId: member?.id ?? null, postId });
			const raw = (id, owner) => ({ id, nickname: `작성 회원 ${owner}`, content: marker(id, postId),
				mine: member?.id === owner, authorComment: false, createdAt: '2026-11-17T00:00:00',
				deleted: false, edited: false, likeCount: 0, dislikeCount: 0, likedByMe: false,
				dislikedByMe: false, replies: [], replyHasNext: false });
			const offset = postId === 1 ? 0 : 1000;
			return ok(route, { total: 3, items: [raw(offset + 101, 7), raw(offset + 202, 8), raw(offset + 303, 9)], hasNext: false, nextCursor: null });
		}
		const mutation = /^\/api\/v1\/blog\/comments\/(\d+)$/.exec(path);
		if (mutation && ['PUT', 'DELETE'].includes(request.method())) {
			writes.push({ route, memberId: member?.id ?? null, id: Number(mutation[1]), method: request.method(),
				body: request.postData() ? request.postDataJSON() : null });
			return;
		}
		if (path.endsWith('/categories') || path.endsWith('/related')) { return ok(route, []); }
		if (path.endsWith('/adjacent')) { return ok(route, {}); }
		return ok(route);
	});
});
afterEach(async context => {
	if (process.env.QA_SCREENSHOT_DIR) {
		await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
		await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR,
			`${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 130)}.png`), fullPage: true });
	}
	const count = page.locator('.comments__count');
	traces.push({ name: context.name, reads, writes: writes.map(entry => ({
		memberId: entry.memberId, id: entry.id, method: entry.method, body: entry.body,
	})), total: await count.count() ? await count.textContent() : null, pageErrors: errors });
	if (process.env.QA_TRACE_PATH) {
		await mkdir(dirname(process.env.QA_TRACE_PATH), { recursive: true });
		await writeFile(process.env.QA_TRACE_PATH, JSON.stringify(traces, null, 2) + '\n');
	}
	await page.close(); assert.deepEqual(errors, []);
});

async function openPost(width, id = 1) {
	await page.setViewportSize({ width, height: 900 }); await page.goto(`${origin}posts/${id}`);
	const targetId = id === 1 ? 303 : 1303;
	await row(targetId).getByText(marker(targetId, id), { exact: true }).waitFor();
}
async function changeMember(next, advance = false) {
	const previousId = member?.id ?? null, previousReads = reads.length; member = next;
	await page.evaluate(async advance => {
		if (advance) { (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration(); }
		await (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession();
	}, advance);
	if ((next?.id ?? null) !== previousId) {
		await row(303).getByText(marker(303), { exact: true }).waitFor();
		assert.ok(reads.length > previousReads, 'The actual parent must GET and replace the account-specific comment list');
		assert.equal(reads.at(-1).memberId, next?.id ?? null);
	}
	await settled();
}
async function edit(id, content) {
	await row(id).getByRole('button', { name: '수정', exact: true }).click();
	await row(id).getByRole('textbox', { name: '댓글 고치기' }).fill(content);
	return row(id).locator('.comment-edit button[type="submit"]');
}
async function deletion(id) {
	await row(id).getByRole('button', { name: '삭제', exact: true }).click();
	return row(id).getByRole('button', { name: /^(지우기|지우는 중…)$/ });
}
async function send(button) {
	const request = page.waitForRequest(request => /\/blog\/comments\/\d+$/.test(new URL(request.url()).pathname)
		&& ['PUT', 'DELETE'].includes(request.method()));
	await button.click(); await request; return writes.at(-1);
}
async function release(entry, fails = false, message = oldError) {
	const response = page.waitForResponse(response => response.request() === entry.route.request());
	if (fails) { await entry.route.fulfill({ status: 500, json: { success: false, message } }); }
	else { await ok(entry.route); }
	await (await response).finished(); await settled();
}
async function noOldError() { assert.equal(await page.getByText(oldError, { exact: true }).count(), 0); }
async function begin(kind, id, content = '현재 저장할 내용') {
	return send(kind === 'edit' ? await edit(id, content) : await deletion(id));
}
function pendingButton(kind, id) {
	return kind === 'edit' ? row(id).locator('.comment-edit button[type="submit"]')
		: row(id).getByRole('button', { name: /^(지우기|지우는 중…)$/ });
}
async function navigate(path) {
	await page.evaluate(async path => (await import('/src/app/router/index.js')).default.push(path), path);
	await settled();
}

for (const width of [1280, 390]) {
	test(`${width}: B can send a new edit after the actual parent GET while A remains pending`, async context => {
		await openPost(width); const old = await send(await edit(101, 'A의 저장 내용'));
		await changeMember({ ...memberB });
		const button = await edit(202, 'B의 새 수정 초안');
		context.diagnostic(`parent GET actors=${reads.map(read => read.memberId)}, B edit disabled=${await button.isDisabled()}`);
		assert.equal(await button.isDisabled(), false);
		const current = await send(button); assert.equal(writes.length, 2);
		await release(old, true);
		assert.equal(await button.isDisabled(), true);
		assert.equal(await row(202).getByRole('textbox', { name: '댓글 고치기' }).inputValue(), 'B의 새 수정 초안');
		await noOldError();
		await release(current);
		await row(202).getByText('B의 새 수정 초안', { exact: true }).waitFor();
		assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
	});

	test(`${width}: old delete success cannot filter B replacement items or decrement the new total`, async context => {
		await openPost(width); const old = await send(await deletion(101));
		await changeMember({ ...memberB }); await release(old);
		context.diagnostic(`parent GET actors=${reads.map(read => read.memberId)}, total=${await page.locator('.comments__count').textContent()}`);
		assert.equal(await row(101).count(), 1);
		assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
	});

	test(`${width}: old delete failure cannot enter B errors or persistent toast after the parent GET`, async () => {
		await openPost(width); const old = await send(await deletion(101));
		await changeMember({ ...memberB }); await release(old, true);
		await noOldError();
		assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
	});

	for (const kind of ['edit', 'delete']) {
		test(`${width}: normal ${kind} preserves count and content policy and submits once`, async () => {
			await openPost(width); const request = await begin(kind, 101);
			await pendingButton(kind, 101).evaluate(button => { button.click(); button.click(); });
			assert.equal(writes.length, 1);
			await release(request);
			if (kind === 'edit') { await row(101).getByText('현재 저장할 내용', { exact: true }).waitFor(); }
			else { await row(101).waitFor({ state: 'detached' }); }
			assert.equal((await page.locator('.comments__count').textContent()).trim(), kind === 'edit' ? '3' : '2');
			assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
		});

		for (const fails of [false, true]) {
			test(`${width}: ${kind} ABA ignores old ${fails ? 'failure' : 'success'} and preserves a new A request`, async () => {
				await openPost(width); const old = await begin(kind, 101, '이전 A 내용');
				await changeMember({ ...memberB }); await changeMember({ ...memberA });
				const current = await begin(kind, 101, '돌아온 A 새 초안');
				await release(old, fails);
				assert.equal(await pendingButton(kind, 101).isDisabled(), true);
				assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
				if (kind === 'edit') { assert.equal(await row(101).getByRole('textbox').inputValue(), '돌아온 A 새 초안'); }
				await noOldError(); assert.equal(writes.length, 2);
				assert.deepEqual(reads.map(read => read.memberId), [7, 8, 7]);
				await release(current);
			});

			test(`${width}: ${kind} post change ignores old ${fails ? 'failure' : 'success'} without touching the new post request`, async () => {
				await openPost(width); const old = await begin(kind, 101);
				await navigate('/posts/2');
				await row(1303).getByText(marker(1303, 2), { exact: true }).waitFor();
				const current = await begin(kind, 1101, '새 글의 초안');
				await release(old, fails);
				assert.equal(await pendingButton(kind, 1101).isDisabled(), true);
				assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
				await noOldError(); assert.equal(writes.length, 2);
				await release(current);
			});

			test(`${width}: ${kind} completion after unmount ignores old ${fails ? 'failure' : 'success'}`, async () => {
				await openPost(width); const old = await begin(kind, 101);
				await navigate('/privacy'); await page.locator('.comments').waitFor({ state: 'detached' });
				await release(old, fails); await noOldError();
				assert.equal(await page.locator('.comments').count(), 0);
			});
		}

		for (const mode of ['member-refresh', 'response', 'next-action']) {
			test(`${width}: ${kind} same ID authentication change is guarded at ${mode}`, async () => {
				await openPost(width); const old = await begin(kind, 101);
				if (mode === 'member-refresh') { await changeMember({ ...memberA }, true); }
				else { await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration()); }
				if (mode === 'response') {
					await release(old, true);
					assert.equal(await row(101).locator('.comment-edit').count(), 0);
					assert.equal(await row(101).getByText('정말 지울까요?', { exact: true }).count(), 0);
					await noOldError();
				} else if (mode === 'next-action') {
					await row(101).getByRole('button', { name: '취소', exact: true }).click();
				}
				const current = await begin(kind, 101, '새 인증의 초안');
				if (mode !== 'response') {
					await release(old, true);
					assert.equal(await pendingButton(kind, 101).isDisabled(), true);
					await noOldError();
				}
				assert.equal(writes.length, 2); assert.equal(reads.length, 1);
				await release(current);
			});
		}

		test(`${width}: ${kind} nickname refresh and failed logout preserve the same valid request`, async () => {
			await openPost(width); const request = await begin(kind, 101, '보존할 초안');
			await changeMember({ ...memberA, nickname: '같은 회원 새 닉네임' });
			logoutFails = true;
			await page.locator('.comment-form').getByRole('button', { name: '로그아웃', exact: true }).click();
			await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
			assert.equal(await pendingButton(kind, 101).isDisabled(), true);
			if (kind === 'edit') { assert.equal(await row(101).getByRole('textbox').inputValue(), '보존할 초안'); }
			assert.equal(writes.length, 1); assert.equal(reads.length, 1);
			await release(request);
		});

		test(`${width}: ${kind} identity reset dismisses only its persistent error toast`, async () => {
			await openPost(width); const request = await begin(kind, 101);
			await release(request, true);
			await page.getByText(oldError, { exact: true }).first().waitFor();
			await page.evaluate(async () => (await import('/src/shared/toast/toastStore.js')).notifyError('다른 기능의 알림'));
			await changeMember({ ...memberB }); await noOldError();
			assert.equal(await page.getByText('다른 기능의 알림', { exact: true }).count(), 1);
		});
	}

	for (const fails of [false, true]) {
		test(`${width}: B can send deletion while the old A ${fails ? 'failure' : 'success'} remains pending`, async () => {
			await openPost(width); const old = await begin('delete', 101);
			await changeMember({ ...memberB }); const button = await deletion(202);
			assert.equal(await button.isDisabled(), false);
			const current = await send(button); await release(old, fails);
			assert.equal(await pendingButton('delete', 202).isDisabled(), true);
			assert.equal(await row(101).count(), 1); await noOldError();
			assert.equal((await page.locator('.comments__count').textContent()).trim(), '3');
			assert.equal(writes.length, 2); await release(current);
			assert.equal(await row(202).count(), 0);
			assert.equal((await page.locator('.comments__count').textContent()).trim(), '2');
		});
	}

	test(`${width}: account change clears the retained old edit before the B draft receives a late error`, async () => {
		await openPost(width); const old = await begin('edit', 101, 'A 내용');
		await changeMember({ ...memberB });
		assert.equal(await page.locator('.comment-edit').count(), 0);
		await edit(202, 'B 초안'); await release(old, true);
		assert.equal(await row(202).getByRole('textbox').inputValue(), 'B 초안');
		await noOldError();
	});
}
