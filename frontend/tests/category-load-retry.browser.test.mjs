import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors, categoryRequests, categoryHandler;
const categories = [{ id: 1, name: '복구된 카테고리' }];
const menuButton = () => page.getByRole('button', { name: '메뉴', exact: true });
const menu = () => page.locator('.site-menu__dropdown');
const ok = (route, data) => route.fulfill({ json: { success: true, data } });
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

before(async () => {
	server = await createServer({
		root: fileURLToPath(new URL('..', import.meta.url)),
		configFile: false,
		plugins: [vue()],
		server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen();
	browser = await chromium.launch({
		headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});

after(async () => {
	try {
		await browser?.close();
	} finally {
		await server?.close();
	}
});

beforeEach(async () => {
	errors = [];
	categoryRequests = [];
	categoryHandler = route => route.fulfill({
		status: 503,
		json: { success: false, code: 'TEST_TEMPORARY_FAILURE', message: '테스트용 일시 오류' },
	});
	page = await browser.newPage({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	const origin = new URL(server.resolvedUrls.local[0]).origin;
	await page.route('**/*', route => {
		const url = new URL(route.request().url());
		if (url.origin !== origin) {
			return route.abort('blockedbyclient');
		}
		if (url.pathname === '/api/v1/blog/categories') {
			categoryRequests.push(route.request().method());
			return categoryHandler(route);
		}
		if (url.pathname === '/api/v1/blog/posts') {
			return ok(route, { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
		}
		if (url.pathname.startsWith('/api/')) {
			return ok(route, null);
		}
		return route.continue();
	});
	await page.goto(`${server.resolvedUrls.local[0]}privacy`);
	await menuButton().waitFor();
});

afterEach(async (context) => {
	context.diagnostic(`카테고리 HTTP 요청 수: ${categoryRequests.length}`);
	try {
		if (page && process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({
				path: `${process.env.QA_SCREENSHOT_DIR}/category-${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 90)}.png`,
				fullPage: true,
			});
		}
	} finally {
		await page?.close();
	}
	assert.deepEqual(errors, []);
});

async function failFirstMenuLoad() {
	const failed = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/blog/categories');
	await menuButton().click();
	const response = await failed;
	assert.equal(response.status(), 503);
	await response.finished();
	await settled();
	assert.equal(categoryRequests.length, 1);
	assert.equal(await menu().getByRole('link', { name: categories[0].name }).count(), 0);
}

test('mobile menu retries a failed category load on reopen and then caches success', async () => {
	await failFirstMenuLoad();
	categoryHandler = route => ok(route, categories);
	await menuButton().click();
	await menu().waitFor({ state: 'detached' });
	await menuButton().click();
	await menu().getByRole('link', { name: categories[0].name, exact: true }).waitFor();
	assert.equal(categoryRequests.length, 2);
	await menuButton().click();
	await menuButton().click();
	await settled();
	assert.equal(categoryRequests.length, 2);
});

test('moving to the post list retries categories in the same tab after menu failure', async () => {
	await failFirstMenuLoad();
	categoryHandler = route => ok(route, categories);
	await menu().getByRole('link', { name: '전체 글', exact: true }).click();
	await page.waitForURL('**/posts');
	await page.locator('.post-list__filters').getByRole('link', { name: categories[0].name, exact: true }).waitFor();
	assert.equal(categoryRequests.length, 2);
});

test('a successful empty category response stays cached across reopen and post-list navigation', async () => {
	categoryHandler = route => ok(route, []);
	const response = page.waitForResponse(reply => new URL(reply.url()).pathname === '/api/v1/blog/categories');
	await menuButton().click();
	await (await response).finished();
	await settled();
	await menuButton().click();
	await menuButton().click();
	await settled();
	await menu().getByRole('link', { name: '전체 글', exact: true }).click();
	await page.waitForURL('**/posts');
	await page.locator('.post-list').waitFor();
	await settled();
	assert.equal(categoryRequests.length, 1);
});
