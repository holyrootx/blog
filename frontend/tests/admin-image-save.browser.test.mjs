import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';
let server, browser, page, post, uploaded, writes, errors;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY7sAAAAASUVORK5CYII=', 'base64');
const file = { name: 'photo.png', mimeType: 'image/png', buffer: png };
const imageUrl = '/saved-photo.png';
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const saveButton = () => page.getByRole('button', { name: '임시저장', exact: true });
const publishButton = () => page.getByRole('button', { name: '발행', exact: true });
const url = suffix => `${server.resolvedUrls.local[0]}admin/posts/${suffix}`;
before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = []; writes = []; uploaded = [];
	post = { id: 9001, title: '사진이 있는 글', categoryId: 1, excerpt: '요약', content: '기존 본문',
		thumbnailImageUrl: '', status: 'DRAFT', updatedAt: '2026-10-15T00:00:00', publishedAt: null };
	page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
	page.setDefaultTimeout(6000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/api/v1/**', route => {
		const path = new URL(route.request().url()).pathname, method = route.request().method();
		if (path === '/api/v1/admin/blog/images') { uploaded.push(route); return; }
		if (path.endsWith('/csrf')) {
			return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });
		}
		if (path === '/api/v1/admin/auth/me') {
			return ok(route, { username: 'test', role: 'ADMIN' });
		}
		if (path.endsWith('/categories')) {
			return ok(route, [{ id: 1, name: '테스트', sortOrder: 1 }]);
		}
		if (path.endsWith('/sidebar/menus')) {
			return ok(route, []);
		}
		if (path === '/api/v1/admin/blog/posts/9001') {
			if (method === 'PUT') {
				const body = route.request().postDataJSON(); writes.push({ path, body });
				post = { ...post, ...body, updatedAt: '2026-10-15T00:01:00' };
			}
			return ok(route, post);
		}
		if (path.endsWith('/9001/publish')) { writes.push({ path }); post.status = 'PUBLISHED'; return ok(route); }
		if (path.endsWith('/posts')) {
			if (method === 'POST') {
				const body = route.request().postDataJSON(); writes.push({ path, body }); post = { ...post, ...body }; return ok(route, 9001);
			}
			return ok(route, { items: [], totalElements: 0 });
		}
		return ok(route);
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });
async function beginUpload(kind) {
	if (kind === 'body') {
		await page.locator('[contenteditable=true]').first().evaluate((el, base64) => {
			const data = new DataTransfer();
			data.items.add(new File([Uint8Array.from(atob(base64), char => char.charCodeAt(0))], 'photo.png', { type: 'image/png' }));
			el.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: data }));
		}, png.toString('base64'));
		await page.locator('.block-editor__image-status').first().waitFor();
	} else {
		await page.getByRole('button', { name: '수정하기', exact: true }).click();
		await page.locator('input[type=file]').setInputFiles(file);
		await page.getByRole('button', { name: '업로드 중', exact: true }).waitFor();
		await page.keyboard.press('Escape');
	}
	await page.getByRole('status').filter({ hasText: '업로드하고 있습니다' }).waitFor();
}
async function finishUpload() {
	assert.ok(uploaded.length);
	await ok(uploaded.at(-1), { id: 1, url: imageUrl, originalName: 'photo.png' });
	await page.waitForFunction(() => ![...document.querySelectorAll('[role=status]')].some(el => /업로드하고|실패한 이미지/.test(el.textContent)));
}
for (const kind of ['body', 'thumbnail']) {
	for (const action of ['save', 'publish']) {
		test(`${kind}: ${action} waits for upload and preserves image after reopening`, async () => {
			await page.goto(url('9001')); await page.getByPlaceholder('글 제목', { exact: true }).waitFor();
			await beginUpload(kind);
			assert.equal(await saveButton().isDisabled(), true); assert.equal(await publishButton().isDisabled(), true);
			assert.equal(writes.length, 0); await finishUpload();
			if (action === 'save') { await saveButton().click(); await page.waitForURL('**/admin/posts'); }
			else {
				await publishButton().click();
				await page.getByRole('dialog').getByRole('button', { name: '확인', exact: true }).click();
				await page.getByRole('button', { name: '내리기', exact: true }).waitFor();
			}
			assert.equal(post.thumbnailImageUrl, imageUrl);
			if (kind === 'body') {
				assert.ok(post.content.includes(imageUrl));
			}
			await page.goto(url('9001'));
			await page.locator(`.admin-editor__thumbnail img[src="${imageUrl}"]`).waitFor();
			if (kind === 'body') {
				assert.equal(await page.locator(`.block-editor__image-preview[src="${imageUrl}"]`).count(), 1);
			}
		});
	}
}
for (const kind of ['body', 'thumbnail']) {
	test(`${kind}: failed upload preserves input and can be retried`, async () => {
		await page.goto(url('9001')); await page.getByPlaceholder('글 제목', { exact: true }).fill('실패해도 보존할 제목');
		await beginUpload(kind);
		await uploaded.at(-1).fulfill({ status: 500, json: { success: false, message: '이미지 업로드 실패' } });
		await page.getByRole('status').filter({ hasText: '업로드에 실패한 이미지' }).waitFor();
		assert.equal(await saveButton().isDisabled(), true);
		assert.equal(await page.getByPlaceholder('글 제목', { exact: true }).inputValue(), '실패해도 보존할 제목');
		if (kind === 'body') {
			const chooser = page.waitForEvent('filechooser'); await page.getByRole('button', { name: '다시 고르기' }).click();
			await (await chooser).setFiles(file); await page.locator('.block-editor__image-status').waitFor();
		} else {
			await page.getByRole('button', { name: '수정하기', exact: true }).click();
			await page.locator('input[type=file]').setInputFiles(file); await page.getByRole('button', { name: '업로드 중', exact: true }).waitFor();
		}
		await finishUpload(); await saveButton().click(); await page.waitForURL('**/admin/posts');
		assert.equal(post.title, '실패해도 보존할 제목'); assert.equal(post.thumbnailImageUrl, imageUrl);
	});
}
test('new post includes uploaded body image and automatic thumbnail in its first save', async () => {
	await page.goto(url('new')); await page.getByPlaceholder('글 제목', { exact: true }).fill('새 글');
	await page.locator('.admin-field').filter({ has: page.locator('.admin-field__label').filter({ hasText: /^카테고리$/ }) }).getByRole('combobox').click();
	await page.getByRole('option', { name: '테스트', exact: true }).click();
	await beginUpload('body'); assert.equal(await saveButton().isDisabled(), true);
	await finishUpload(); await saveButton().click(); await page.waitForURL('**/admin/posts/9001');
	assert.ok(post.content.includes(imageUrl)); assert.equal(post.thumbnailImageUrl, imageUrl);
});
test('late response after leaving cannot change the reopened post', async () => {
	await page.goto(url('9001')); await beginUpload('body');
	await page.getByRole('link', { name: '목록', exact: true }).click(); await page.waitForURL('**/admin/posts');
	await ok(uploaded[0], { url: imageUrl, originalName: 'photo.png' });
	await page.goto(url('9001')); await page.getByPlaceholder('글 제목', { exact: true }).waitFor();
	assert.equal(writes.length, 0); assert.equal(post.content, '기존 본문');
	assert.equal(await page.locator(`img[src="${imageUrl}"]`).count(), 0);
});

test('publish retry saves an image added after the first publish failure', async () => {
	let failPublish = true;
	await page.route('**/api/v1/admin/blog/posts/9001/publish', route => {
		if (failPublish) {
			return route.fulfill({ status: 503, json: { success: false, message: '발행 실패' } });
		}
		post.status = 'PUBLISHED'; return ok(route);
	});
	await page.goto(url('9001')); await publishButton().click();
	await page.getByRole('dialog').getByRole('button', { name: '확인', exact: true }).click();
	const retry = page.getByRole('button', { name: '발행 다시 시도', exact: true });
	await retry.waitFor();
	await beginUpload('body'); assert.equal(await retry.isDisabled(), true);
	await finishUpload(); failPublish = false; await retry.click();
	await page.getByRole('button', { name: '내리기', exact: true }).waitFor();
	assert.ok(post.content.includes(imageUrl)); assert.equal(post.thumbnailImageUrl, imageUrl);
	assert.equal(writes.filter(write => write.body).length, 2);
});

test('publish retry preserves a newly added image without overwriting changes from another editor', async () => {
	let publishAttempts = 0;
	await page.route('**/api/v1/admin/blog/posts/9001/publish', route => {
		publishAttempts++;
		return route.fulfill({ status: 503, json: { success: false, message: '발행 실패' } });
	});
	await page.goto(url('9001')); await publishButton().click();
	await page.getByRole('dialog').getByRole('button', { name: '확인', exact: true }).click();
	const retry = page.getByRole('button', { name: '발행 다시 시도', exact: true });
	await retry.waitFor();
	post = { ...post, content: '다른 창에서 수정한 본문', updatedAt: '2026-10-15T00:02:00' };
	await beginUpload('body'); await finishUpload(); await retry.click();
	await page.locator('.admin-form-error').filter({ hasText: '다른 곳에서 글이 수정되었습니다' }).waitFor();
	assert.equal(post.content, '다른 창에서 수정한 본문');
	assert.equal(writes.filter(write => write.body).length, 1);
	assert.equal(publishAttempts, 1);
	assert.equal(await page.locator('.block-editor__image-preview').count(), 1);
	assert.equal(await page.locator(`.admin-editor__thumbnail img[src="${imageUrl}"]`).count(), 1);
});

test('an image started during saving stays in the editor and is included in the next save', async () => {
	let capture;
	const request = new Promise(resolve => { capture = resolve; });
	let pause = true;
	await page.route('**/api/v1/admin/blog/posts/9001', route => {
		if (pause && route.request().method() === 'PUT') {
			pause = false; capture(route); return;
		}
		return route.fallback();
	});
	await page.goto(url('9001')); await saveButton().click();
	const pending = await request;
	const body = pending.request().postDataJSON();
	await beginUpload('body');
	post = { ...post, ...body, updatedAt: '2026-10-15T00:01:00' };
	await ok(pending, post);
	await page.locator('.admin-editor__saved').filter({ hasText: '저장 중 추가한 내용은 아직 저장되지 않았습니다' }).waitFor();
	assert.equal(new URL(page.url()).pathname, '/admin/posts/9001');
	assert.equal(await saveButton().isDisabled(), true);
	assert.equal(post.content.includes(imageUrl), false);
	await finishUpload(); await saveButton().click(); await page.waitForURL('**/admin/posts');
	assert.ok(post.content.includes(imageUrl));
});

test('all concurrent uploads must finish before saving', async () => {
	await page.goto(url('9001')); await beginUpload('body'); await beginUpload('body');
	assert.equal(uploaded.length, 2);
	await ok(uploaded[0], { url: '/first-photo.png', originalName: 'first.png' });
	await page.getByRole('status').filter({ hasText: '이미지 1개' }).waitFor();
	assert.equal(await saveButton().isDisabled(), true);
	await finishUpload(); await saveButton().click(); await page.waitForURL('**/admin/posts');
	assert.ok(post.content.includes('/first-photo.png')); assert.ok(post.content.includes(imageUrl));
});

test('an upload response without a URL is a failure, not a completed image', async () => {
	await page.goto(url('9001')); await beginUpload('body');
	await ok(uploaded[0], { url: '', originalName: 'photo.png' });
	await page.getByRole('status').filter({ hasText: '업로드에 실패한 이미지' }).waitFor();
	assert.equal(await saveButton().isDisabled(), true);
	assert.equal(writes.length, 0);
});
