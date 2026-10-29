import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, heldPost, postRequested, listRequests;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

/**
 * 글 6 요청이 실제로 나갈 때까지 기다린다.
 *
 * 시간을 정해 두고 기다리면 느린 CI 에서 화면 코드가 아직 안 와 요청 전에 확인하게 된다.
 * 요청이 끝내 안 나가면 그 사실이 실패 사유로 보이게 시간 제한을 둔다.
 */
function waitForPostRequest() {
	return Promise.race([
		postRequested.promise,
		new Promise((_, reject) => setTimeout(() => reject(new Error('post detail request was not sent')), 10000)),
	]);
}
const post = (id, title) => ({ id, categoryId: 1, categoryName: '생각', title, excerpt: `${title} 요약`,
	content: `${title} 본문`, publishedAt: '2026-09-17T14:07:16', views: 1, thumbnailImageUrl: null,
	structuredData: { '@context': 'https://schema.org', '@type': 'BlogPosting', headline: title,
		url: `https://jsjlog.me/posts/${id}`, datePublished: '2026-09-17T14:07:16+09:00',
		author: { '@type': 'Person', name: '공개 이름' } } });
const robots = () => page.locator('meta[name="robots"]').getAttribute('content');
const canonical = () => page.locator('link[rel="canonical"]').getAttribute('href');
const structuredData = async () => JSON.parse(await page.locator('#post-structured-data').textContent());

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = []; heldPost = null; postRequested = deferred(); listRequests = [];
	page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/api/v1/**', async route => {
		const { pathname, searchParams } = new URL(route.request().url());
		if (pathname === '/api/v1/blog/posts') {
			listRequests.push(Object.fromEntries(searchParams));
			const number = Number(searchParams.get('page') ?? 0);
			return ok(route, { items: [post(101 + number, `목록 ${number + 1}쪽 글`)],
				page: number, size: 12, totalElements: 276, totalPages: 23 });
		}
		if (pathname === '/api/v1/blog/categories') {
			return ok(route, [{ id: 3, name: 'Spring' }]);
		}
		if (pathname === '/api/v1/blog/posts/5') {
			return ok(route, post(5, '앞의 글'));
		}
		if (pathname === '/api/v1/blog/posts/5/adjacent') {
			return ok(route, { previousPost: null, nextPost: { id: 6, title: '뒤의 글' } });
		}
		if (pathname === '/api/v1/blog/posts/6') { heldPost = route; postRequested.resolve(); return undefined; }
		if (pathname.endsWith('/comments')) {
			return ok(route, { items: [], total: 0, nextCursor: null, hasNext: false });
		}
		if (pathname.endsWith('/related')) {
			return ok(route, []);
		}
		return ok(route, null);
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

/** 운영에서 Nginx 가 글 주소를 백엔드로 넘기면 첫 HTML 에 그 글의 메타가 들어 있다. 그 상태를 흉내 낸다 */
async function serveServerRenderedMeta(path) {
	await page.route(`**${path}`, async route => {
		if (route.request().resourceType() !== 'document') {
			return route.fallback();
		}
		const response = await route.fetch();
		const html = (await response.text()).replace(/<!-- blog-meta:start -->[\s\S]*?<!-- blog-meta:end -->/,
			'<title>서버가 넣은 제목 · 정성주의 기록</title>\n<meta name="robots" content="index,follow" />\n'
			+ `<link rel="canonical" href="https://jsjlog.me${path}" />`
			+ '<script id="post-structured-data" type="application/ld+json">'
			+ JSON.stringify(post(6, '서버가 넣은 제목').structuredData) + '</script>');
		return route.fulfill({ response, body: html });
	});
}

test('first visit keeps the server metadata even when the post API fails', async () => {
	await serveServerRenderedMeta('/posts/6');
	await page.goto(`${server.resolvedUrls.local[0]}posts/6`);
	await waitForPostRequest();
	assert.equal(await page.title(), '서버가 넣은 제목 · 정성주의 기록');
	await heldPost.fulfill({ status: 500, json: { success: false, code: 'INTERNAL_SERVER_ERROR', message: '오류' } });
	// 실패 화면이 그려진 뒤에 본다. 그 전이면 메타를 건드릴 기회가 아직 남아 있다
	await page.getByText('글을 불러오지 못했습니다').waitFor();
	assert.equal(await page.title(), '서버가 넣은 제목 · 정성주의 기록');
	assert.equal(await robots(), 'index,follow');
	assert.equal(await canonical(), 'https://jsjlog.me/posts/6');
	assert.equal((await structuredData()).headline, '서버가 넣은 제목');
});

test('moving to another post inside the app never marks the loading page noindex', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts/5`);
	await page.waitForFunction(() => document.title.startsWith('앞의 글'));
	assert.equal((await structuredData()).headline, '앞의 글');
	assert.equal(await page.locator('.post-article__meta time').getAttribute('datetime'), '2026-09-17T14:07:16');
	assert.equal(await page.locator('.post-article__meta time').textContent(), '2026. 09. 17');
	await page.getByRole('link', { name: /뒤의 글/ }).click();
	await page.waitForURL('**/posts/6');
	await waitForPostRequest();
	await page.waitForFunction(() => document.title.startsWith('글 불러오는 중'));
	assert.notEqual(await robots(), 'noindex,follow');
	assert.match(await canonical(), /\/posts\/6$/);
	assert.equal(await page.locator('#post-structured-data').count(), 0);
	await ok(heldPost, post(6, '뒤의 글'));
	await page.waitForFunction(() => document.title.startsWith('뒤의 글'));
	assert.equal(await robots(), 'index,follow');
	assert.deepEqual(await structuredData(), post(6, '뒤의 글').structuredData);
	assert.equal(await page.locator('#post-structured-data').count(), 1);
});

test('article structured data is removed when moving to an ordinary page and recreated on back', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts/5`);
	await page.waitForFunction(() => document.title.startsWith('앞의 글'));
	await page.getByRole('link', { name: '개인정보처리방침', exact: true }).click();
	await page.waitForURL('**/privacy');
	assert.equal(await page.locator('#post-structured-data').count(), 0);
	await page.goBack();
	await page.waitForFunction(() => document.title.startsWith('앞의 글'));
	assert.deepEqual(await structuredData(), post(5, '앞의 글').structuredData);
});

test('a missing article removes server structured data and an HTML-looking title remains plain JSON', async () => {
	await serveServerRenderedMeta('/posts/6');
	await page.goto(`${server.resolvedUrls.local[0]}posts/6`);
	await waitForPostRequest();
	await heldPost.fulfill({ status: 404, json: { success: false, code: 'POST_NOT_FOUND', message: '없는 글' } });
	await page.getByRole('heading', { name: '찾는 글이 없습니다', exact: true }).waitFor();
	assert.equal(await page.locator('#post-structured-data').count(), 0);
	assert.equal(await robots(), 'noindex,follow');

	await page.route('**/api/v1/blog/posts/5', route => ok(route, post(5, '</script><script>window.injected=1</script>')));
	await page.goto(`${server.resolvedUrls.local[0]}posts/5`);
	await page.waitForFunction(() => document.querySelector('#post-structured-data'));
	assert.equal((await structuredData()).headline, '</script><script>window.injected=1</script>');
	assert.equal(await page.evaluate(() => window.injected), undefined);
});

test('a later category page removes tracking and fragments without canonicalizing to page one', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts/?utm_source=test&category=03&page=02&sort=latest#top`);
	await page.getByRole('heading', { name: '목록 2쪽 글', exact: true }).waitFor();
	const expected = new URL('/posts?page=2&category=3', page.url()).href;
	assert.equal(await canonical(), expected);
	assert.equal(await page.locator('meta[property="og:url"]').getAttribute('content'), expected);
	assert.equal(await page.locator('link[rel="canonical"]').count(), 1);
	assert.equal(await robots(), 'index,follow');
	assert.deepEqual(listRequests.at(-1), { page: '1', size: '12', sort: 'latest', categoryId: '3' });
});

test('pagination exposes real links and keeps the selected category across page blocks', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?page=10&category=3&utm_source=test`);
	await page.getByRole('heading', { name: '목록 10쪽 글', exact: true }).waitFor();
	const pager = page.getByRole('navigation', { name: '페이지', exact: true });
	assert.equal(await pager.getByRole('button').count(), 0);
	assert.equal(await pager.getByRole('link', { name: '1', exact: true }).getAttribute('href'), '/posts?category=3');
	assert.equal(await pager.getByRole('link', { name: '끝', exact: true }).getAttribute('href'), '/posts?page=23&category=3');
	assert.equal(await pager.locator('[aria-current="page"]').count(), 1);
	assert.equal(await pager.locator('[aria-current="page"]').textContent(), '10');

	const next = pager.getByRole('link', { name: '다음 10개 ›', exact: true });
	assert.equal(await next.getAttribute('href'), '/posts?page=11&category=3');
	await next.click();
	await page.getByRole('heading', { name: '목록 11쪽 글', exact: true }).waitFor();
	assert.equal(await canonical(), new URL('/posts?page=11&category=3', page.url()).href);
	assert.equal(await pager.getByRole('link', { name: '처음', exact: true }).getAttribute('href'), '/posts?category=3');
	assert.equal(await pager.getByRole('link', { name: '‹ 이전 10개', exact: true }).getAttribute('href'), '/posts?page=10&category=3');
	await pager.getByRole('link', { name: '처음', exact: true }).click();
	await page.getByRole('heading', { name: '목록 1쪽 글', exact: true }).waitFor();
	assert.deepEqual(listRequests.at(-1), { page: '0', size: '12', sort: 'latest', categoryId: '3' });
});

test('search is noindex until cleared, then restores the category canonical in the same tab', async () => {
	const query = new URLSearchParams({ q: '  Vue & Spring  ', page: '2', category: '3', utm_source: 'test' });
	await page.goto(`${server.resolvedUrls.local[0]}posts?${query}`);
	await page.getByRole('heading', { name: '‘Vue & Spring’ 검색 결과', exact: true }).waitFor();
	assert.equal(await robots(), 'noindex,follow');
	assert.equal(await page.locator('link[rel="canonical"]').count(), 0);
	assert.equal(await page.title(), '‘Vue & Spring’ 검색 결과 · 정성주의 기록');
	const next = page.getByRole('navigation', { name: '페이지', exact: true }).getByRole('link', { name: '3', exact: true });
	const nextUrl = new URL(await next.getAttribute('href'), page.url());
	assert.equal(nextUrl.searchParams.get('q'), 'Vue & Spring');
	assert.equal(nextUrl.searchParams.get('category'), '3');

	await page.getByRole('button', { name: '검색 지우기', exact: true }).click();
	await page.getByRole('heading', { name: 'Spring', exact: true }).waitFor();
	assert.equal(await robots(), 'index,follow');
	assert.equal(await canonical(), new URL('/posts?category=3', page.url()).href);
	assert.equal(await page.title(), '전체 글 · 정성주의 기록');
	assert.deepEqual(listRequests.at(-1), { page: '0', size: '12', sort: 'latest', categoryId: '3' });
});

test('duplicate search values and malformed numbers still render the ordinary list', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}posts?q=Vue&q=Spring&page=0.5&category=bad&utm_source=test`);
	await page.getByRole('heading', { name: '목록 1쪽 글', exact: true }).waitFor();
	assert.equal(await canonical(), new URL('/posts', page.url()).href);
	assert.equal(await robots(), 'index,follow');
	assert.equal(await page.getByRole('button', { name: '검색 지우기', exact: true }).count(), 0);
	assert.deepEqual(listRequests.at(-1), { page: '0', size: '12', sort: 'latest' });
});

test('static pages and article pages keep clean canonical and share URLs', async () => {
	await page.goto(`${server.resolvedUrls.local[0]}privacy/?utm_source=test#top`);
	await page.waitForFunction(() => document.title.startsWith('개인정보 처리방침'));
	assert.equal(await canonical(), new URL('/privacy', page.url()).href);
	assert.equal(await page.locator('meta[property="og:url"]').getAttribute('content'), await canonical());

	await page.goto(`${server.resolvedUrls.local[0]}posts/5?utm_source=test#top`);
	await page.waitForFunction(() => document.title.startsWith('앞의 글'));
	assert.equal(await canonical(), new URL('/posts/5', page.url()).href);
	assert.equal(await page.locator('meta[property="og:type"]').getAttribute('content'), 'article');
});

for (const width of [1280, 390]) {
	for (const theme of ['light', 'dark']) {
		test(`${width}px ${theme} article search accepts typing and returns to its header button`, async () => {
			await page.setViewportSize({ width, height: 900 });
			await page.goto(`${server.resolvedUrls.local[0]}posts/5`);
			await page.getByRole('heading', { name: '앞의 글', exact: true }).waitFor();
			await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
			const opener = page.getByRole('button', { name: '검색', exact: true });
			await opener.focus();
			await page.keyboard.press('Enter');
			const modal = page.getByRole('dialog', { name: '검색', exact: true });
			const input = modal.getByRole('combobox', { name: '검색어' });
			await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
			assert.equal(await input.evaluate(element => element === document.activeElement), true);
			await page.keyboard.type('Spring');
			assert.equal(await input.inputValue(), 'Spring');
			await modal.getByRole('button', { name: '닫기', exact: true }).focus();
			await page.keyboard.press('Shift+Tab');
			assert.equal(await modal.getByRole('button', { name: /전체 결과 보기/ }).evaluate(element => element === document.activeElement), true);
			const box = await modal.boundingBox();
			assert.ok(box.x >= 0 && box.x + box.width <= width && box.y >= 0 && box.y + box.height <= 900);
			if (process.env.QA_SCREENSHOT_DIR) {
				await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
				await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/search-${width}-${theme}.png` });
			}
			await page.keyboard.press('Escape');
			await modal.waitFor({ state: 'hidden' });
			assert.equal(await opener.evaluate(element => element === document.activeElement), true);
		});
	}
}
