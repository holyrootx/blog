import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const paragraphs = Array(17).fill('프로필의 응답 시점과 해시 제목의 배치를 확인하는 긴 본문입니다.');
const content = ['## 첫 번째 제목', ...paragraphs, '## 두 번째 제목', ...paragraphs, '## 세 번째 제목', ...paragraphs].join('\n\n');
const profile = { id: 1, name: '검사 작성자', intro: '로컬 가짜 프로필', job: '개발 기록',
	avatarImageUrl: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="38" height="38"><rect width="38" height="38" fill="#7188aa"/></svg>'),
	githubUrl: null, email: null, blogStartedAt: '2026-01-01', daysSinceStart: 321 };
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const frames = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
async function observed(promise) {
	let timer;
	try { return await Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Expected layout or HTTP handler observation did not arrive')), 7000); })]); }
	finally { clearTimeout(timer); }
}
let server, browser, page, cacheDir, errors, consoleErrors, samples;
const traces = [];
before(async () => {
	cacheDir = await mkdtemp(join(tmpdir(), 'blog-author-layout-'));
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }); });
beforeEach(async () => {
	errors = []; consoleErrors = []; samples = null;
	page = await browser.newPage({ reducedMotion: 'no-preference', serviceWorkers: 'block' }); page.setDefaultTimeout(7000);
	page.on('pageerror', error => errors.push(error.message));
	page.on('console', message => { if (message.type() === 'error') { consoleErrors.push(message.text()); } });
});
afterEach(async context => {
	try {
		if (process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({ path: join(process.env.QA_SCREENSHOT_DIR, `${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 130)}.png`) });
		}
		traces.push({ name: context.name, ...samples, consoleErrors, pageErrors: errors });
		if (process.env.QA_TRACE_PATH) {
			await mkdir(dirname(process.env.QA_TRACE_PATH), { recursive: true });
			await writeFile(process.env.QA_TRACE_PATH, JSON.stringify(traces, null, 2) + '\n');
		}
	} finally { await page.close(); }
	assert.deepEqual(errors, []);
});

for (const width of [1280, 390]) {
	for (const mode of ['normal-dto', 'http-503', 'no-avatar', 'cached-http-503', 'wrapped-ready']) {
		test(`${width}: author space and hash position survive ${mode}`, { timeout: 15000 }, async context => {
			await page.setViewportSize({ width, height: 1000 });
			const postArrived = deferred(), firstProfile = deferred(), refreshProfile = deferred(), measured = deferred();
			const origin = server.resolvedUrls.local[0], responses = [];
			let profileRequests = 0;
			await page.exposeFunction('__authorLayoutMeasured', value => measured.resolve(value));
			await page.addInitScript(() => {
				window.__authorLayout = { ended: false, calls: [] };
				window.__authorSample = () => {
					const box = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { top: r.top, documentTop: r.top + scrollY, height: r.height }; };
					return { author: box('.post-author'), heading: box('#section-2'), header: box('.blog-header'), scrollY,
						url: location.pathname + location.search + location.hash, fonts: document.fonts.status,
						busy: document.querySelector('.post-author').getAttribute('aria-busy'),
						focusOnMenu: document.activeElement === window.__authorExpectedFocus && document.activeElement !== document.body,
						focus: { tag: document.activeElement?.tagName, id: document.activeElement?.id, className: document.activeElement?.className },
						width: innerWidth, documentWidth: document.documentElement.scrollWidth };
				};
				const native = window.scrollTo;
				window.scrollTo = function(...args) {
					if (args[0]?.top > 100 && document.querySelector('#section-2')) {
						window.__authorLayout.ended = false; window.__authorLayout.calls.push(args[0]);
						window.__authorLayout.measured = true;
						window.__authorLayoutMeasured({ ...window.__authorSample(), argument: args[0] });
					}
					return native.apply(this, args);
				};
				addEventListener('scrollend', () => { if (window.__authorLayout.measured) { window.__authorLayout.ended = true; } });
			});
			await page.route('**/*', route => {
				const request = route.request(), url = new URL(request.url()), path = url.pathname;
				if (url.origin !== new URL(origin).origin) { return route.abort('blockedbyclient'); }
				if (!path.startsWith('/api/')) { return route.continue(); }
				if (request.method() !== 'GET') { return ok(route); }
				if (path === '/api/v1/blog/posts/1') { postArrived.resolve(route); return; }
				if (path === '/api/v1/blog/profile') {
					profileRequests += 1; (profileRequests === 1 ? firstProfile : refreshProfile).resolve(route); return;
				}
				if (path.endsWith('/comments')) { return ok(route, { total: 0, items: [], hasNext: false }); }
				if (path.endsWith('/related') || path.endsWith('/categories')) { return ok(route, []); }
				return ok(route);
			});
			page.on('response', response => { if (new URL(response.url()).pathname === '/api/v1/blog/profile') { responses.push(response.status()); } });
			await page.goto(origin + 'posts/1?source=author-layout#section-2', { waitUntil: 'domcontentloaded' });
			const postRoute = await observed(postArrived.promise), initialProfile = await observed(firstProfile.promise);
			let held = initialProfile;
			if (mode === 'cached-http-503' || mode === 'wrapped-ready') {
				await ok(initialProfile, mode === 'wrapped-ready' ? { ...profile, job: '개발 기록을 이어가는 소개 문장 '.repeat(20) } : profile);
				await page.locator('.blog-header__brand').getByText(profile.name, { exact: true }).waitFor(); await frames();
				if (mode === 'cached-http-503') {
					await page.evaluate(() => { window.__profileRefresh = import('/src/features/home/data/blogProfileStore.js').then(store => store.refreshBlogProfile()); });
					held = await observed(refreshProfile.promise);
				}
			}
			await page.evaluate(() => document.fonts.ready);
			await page.getByRole('button', { name: '메뉴', exact: true }).focus();
			await page.evaluate(() => { window.__authorExpectedFocus = document.activeElement; });
			await ok(postRoute, { id: 1, categoryId: 1, categoryName: '기록', title: '작성자 영역 배치 검사', excerpt: '로컬 회귀', content, publishedAt: '2026-11-17T00:00:00', views: 1 });
			const atCall = await observed(measured.promise);
			assert.equal(atCall.fonts, 'loaded'); assert.equal(atCall.focusOnMenu, true);
			assert.equal(atCall.argument.behavior, 'smooth');
			if (mode !== 'wrapped-ready') {
				if (mode.includes('503')) { await held.fulfill({ status: 503, contentType: 'text/plain', body: 'Local controlled profile unavailable' }); }
				else { await ok(held, mode === 'no-avatar' ? { ...profile, avatarImageUrl: null } : profile); }
			}
			await page.waitForFunction(() => document.querySelector('.post-author')?.getAttribute('aria-busy') === 'false');
			if (mode === 'cached-http-503') { await page.evaluate(() => window.__profileRefresh); }
			await page.waitForFunction(() => window.__authorLayout.ended); await frames();
			const final = await page.evaluate(() => window.__authorSample());
			samples = { mode, profileRequests, responses, atCall, final };
			context.diagnostic(JSON.stringify({ mode, author: [atCall.author.height, final.author.height], headingTop: final.heading.top, headingShift: final.heading.documentTop - atCall.heading.documentTop, focusPreserved: final.focusOnMenu }));
			assert.equal(final.url, '/posts/1?source=author-layout#section-2'); assert.deepEqual(final.focus, atCall.focus); assert.equal(final.focusOnMenu, true);
			assert.equal(final.documentWidth, width);
			if (mode === 'wrapped-ready') { assert.ok(final.author.height > 68, 'Long content is allowed to grow beyond the minimum'); }
			else { assert.equal(final.author.height, 68, 'Only the existing normal/skeleton height is reserved'); }
			assert.ok(Math.abs(final.heading.documentTop - atCall.heading.documentTop) < 1, 'Profile completion cannot move the measured heading');
			assert.ok(Math.abs(final.heading.top - 88) < 2, 'The unchanged hash offset remains 88px');
			assert.ok(final.heading.top > final.header.height, 'The heading stays below the sticky header');
			assert.ok(Math.abs(final.scrollY - atCall.argument.top) < 2, 'No extra compensating scroll or overshoot');
			if (mode.includes('503')) { assert.ok(consoleErrors.some(text => text.includes('ApiError') && text.includes('status=503')), 'The real HTTP client raises ApiError for503'); }
			if (mode === 'cached-http-503' || mode === 'normal-dto' || mode === 'no-avatar') { assert.ok((await page.locator('.post-author').textContent()).includes(profile.name)); }
			assert.deepEqual(responses, mode === 'cached-http-503' ? [200, 503] : [mode === 'http-503' ? 503 : 200]);
		});
	}
}
