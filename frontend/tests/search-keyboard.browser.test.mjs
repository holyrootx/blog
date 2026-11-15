import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors;
const field = () => page.getByRole('combobox', { name: '검색어' });
const dialog = () => page.getByRole('dialog', { name: '검색', exact: true });
const options = () => page.getByRole('option');
const selected = () => options().evaluateAll(elements => elements.findIndex(element => element.getAttribute('aria-selected') === 'true'));
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
const navigation = () => page.evaluate(() => ({
	path: document.querySelector('#app').__vue_app__.config.globalProperties.$router.currentRoute.value.fullPath,
	visits: window.searchNavigations,
}));

async function keydown(init) {
	const prevented = await field().evaluate((element, init) => {
		const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init });
		element.dispatchEvent(event);
		return event.defaultPrevented;
	}, init);
	await settled();
	return prevented;
}

async function openSearch(keyword = '스프링') {
	await page.locator('#search-trigger').click();
	// Opening the dialog can put a suggestion under the pointer and select it by hover.
	await page.mouse.move(0, 0);
	await field().fill(keyword);
	if (keyword.trim().length >= 2) {
		await options().nth(1).waitFor();
	}
	assert.equal(await selected(), -1);
}

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
	await server.listen();
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
	errors = [];
	page = await browser.newPage({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const url = new URL(route.request().url());
		if (url.origin !== origin) {
			return route.abort('blockedbyclient');
		}
		if (url.pathname.startsWith('/api/v1/')) {
			const data = url.pathname.endsWith('/suggest') ? [
				{ id: 991, title: '스프링 입문', categoryName: '개발' },
				{ id: 992, title: '스프링 심화', categoryName: '개발' },
			] : [];
			return route.fulfill({ json: { success: true, data } });
		}
		return route.continue();
	});
	await page.goto(`${server.resolvedUrls.local[0]}tests/fixtures/modal-focus.html`);
	await page.locator('#search-trigger').waitFor();
	await page.evaluate(async () => {
		const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router;
		await router.isReady();
		window.searchNavigations = [];
		router.afterEach(to => window.searchNavigations.push(to.fullPath));
	});
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

for (const composing of [{ isComposing: true }, { isComposing: false, keyCode: 229 }]) {
	const signal = composing.isComposing ? 'isComposing' : 'keyCode 229';
	for (const key of ['Enter', 'ArrowDown', 'ArrowUp']) {
		test(`${key} with ${signal} preserves the IME default action and selected suggestion`, async () => {
			await openSearch();
			await field().press('ArrowDown');
			assert.equal(await selected(), 0);
			assert.equal(await keydown({ key, ...composing }), false);
			assert.equal(await dialog().isVisible(), true);
			assert.equal(await selected(), 0);
			assert.equal(await field().inputValue(), '스프링');
			assert.deepEqual(await navigation(), { path: '/', visits: [] });
		});
	}
	test(`Enter with ${signal} does not submit a query when no suggestion is selected`, async () => {
		await openSearch();
		assert.equal(await keydown({ key: 'Enter', ...composing }), false);
		assert.equal(await dialog().isVisible(), true);
		assert.equal(await selected(), -1);
		assert.deepEqual(await navigation(), { path: '/', visits: [] });
	});
}

test('normal Enter submits the trimmed query once and closes search', async () => {
	await openSearch(' 스프링 ');
	assert.equal(await keydown({ key: 'Enter' }), true);
	assert.equal(await dialog().count(), 0);
	const path = `/posts?${new URLSearchParams({ q: '스프링' })}`;
	assert.deepEqual(await navigation(), { path, visits: [path] });
});

test('normal arrow keys wrap through suggestions and the unselected input', async () => {
	await openSearch();
	for (const [key, index] of [
		['ArrowDown', 0], ['ArrowDown', 1], ['ArrowDown', -1],
		['ArrowUp', 1], ['ArrowUp', 0], ['ArrowUp', -1],
	]) {
		assert.equal(await keydown({ key }), true);
		assert.equal(await selected(), index);
	}
	assert.deepEqual(await navigation(), { path: '/', visits: [] });
});

test('normal Enter opens the keyboard-selected post once', async () => {
	await openSearch();
	await field().press('ArrowDown');
	await field().press('ArrowDown');
	await field().press('Enter');
	await settled();
	assert.equal(await dialog().count(), 0);
	assert.deepEqual(await navigation(), { path: '/posts/992', visits: ['/posts/992'] });
});

test('composition end and its legacy Enter do not submit before the next deliberate Enter', async () => {
	await openSearch();
	await field().evaluate(element => element.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true })));
	assert.equal(await keydown({ key: 'Enter', isComposing: true }), false);
	await field().evaluate(element => {
		element.value = '스프링 완성';
		element.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: '성' }));
	});
	assert.equal(await keydown({ key: 'Enter', isComposing: false, keyCode: 229 }), false);
	assert.equal(await field().inputValue(), '스프링 완성');
	assert.deepEqual(await navigation(), { path: '/', visits: [] });
	await field().press('Enter');
	await settled();
	const path = `/posts?${new URLSearchParams({ q: '스프링 완성' })}`;
	assert.deepEqual(await navigation(), { path, visits: [path] });
	assert.equal(await dialog().count(), 0);
});

test('unrelated input keys retain their default action and suggestion selection', async () => {
	await openSearch();
	await field().press('ArrowDown');
	assert.equal(await keydown({ key: 'ArrowLeft' }), false);
	assert.equal(await selected(), 0);
	assert.deepEqual(await navigation(), { path: '/', visits: [] });
});

test('Enter and arrows with a short query keep search open without suggestions', async () => {
	await openSearch('스');
	for (const key of ['ArrowDown', 'ArrowUp', 'Enter']) {
		assert.equal(await keydown({ key }), true);
	}
	assert.equal(await dialog().isVisible(), true);
	assert.equal(await options().count(), 0);
	assert.equal(await field().inputValue(), '스');
	assert.deepEqual(await navigation(), { path: '/', visits: [] });
});
