import assert from 'node:assert/strict';
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const fakeMember = { id: 1, nickname: '메뉴 검사 회원', role: 'ROLE_USER' };
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
let server, browser, page, origin, member, errors, logoutRequests, holdLogout, pendingLogout;
const siteTrigger = () => page.getByRole('button', { name: '메뉴', exact: true });
const sitePanel = () => page.locator('.site-menu__dropdown');
const memberTrigger = () => page.locator('.member-menu__trigger');
const memberPanel = () => page.locator('.member-menu__dropdown');
const outside = () => page.locator('#outside-menu-control');
const focused = locator => locator.evaluate(element => element === document.activeElement);
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

before(async () => {
	server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
		plugins: [vue()], server: { host: '127.0.0.1', port: 0 },
	});
	await server.listen();
	origin = server.resolvedUrls.local[0];
	browser = await chromium.launch({ headless: true,
		...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}),
	});
});
after(async () => { try { await browser?.close(); } finally { await server?.close(); } });

beforeEach(async () => {
	member = { ...fakeMember };
	errors = [];
	logoutRequests = 0;
	holdLogout = false;
	pendingLogout = null;
	page = await browser.newPage({ serviceWorkers: 'block' });
	page.setDefaultTimeout(5000);
	page.on('pageerror', error => errors.push(error.message));
	await page.route('**/*', async route => {
		const request = route.request(), url = new URL(request.url()), path = url.pathname;
		if (url.origin !== new URL(origin).origin) { return route.abort('blockedbyclient'); }
		if (!path.startsWith('/api/')) { return route.continue(); }
		if (path === '/api/v1/auth/me') { return ok(route, member); }
		if (path.endsWith('/csrf')) { return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test-only-csrf' }); }
		if (path === '/api/v1/auth/logout') {
			logoutRequests += 1;
			if (holdLogout) { pendingLogout = route; return; }
			member = null;
			return ok(route);
		}
		if (path.endsWith('/categories')) { return ok(route, [{ id: 3, name: '메뉴 검사 분류' }]); }
		if (path.endsWith('/notifications/unread-count')) { return ok(route, 0); }
		if (path.endsWith('/recent-posts') || path === '/api/v1/blog/posts') {
			return ok(route, { items: [], totalElements: 0, totalPages: 0, page: 0, size: 10 });
		}
		if (path.endsWith('/notifications') || path.endsWith('/viewed-posts')) { return ok(route, []); }
		return ok(route);
	});
});
afterEach(async (context) => {
	try {
		if (page && process.env.QA_SCREENSHOT_DIR) {
			await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
			await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/${context.name.replace(/[^a-z0-9]+/gi, '-').slice(0, 125)}.png` });
		}
	} finally { await page?.close(); }
	assert.deepEqual(errors, []);
});

async function openPage(width) {
	await page.setViewportSize({ width, height: 900 });
	await page.goto(`${origin}privacy`);
	await siteTrigger().waitFor();
	await page.waitForFunction(async () => (await import('/src/features/member/data/memberAuthStore.js')).useMemberAuth().sessionResolved.value);
	await page.evaluate(() => {
		const button = document.createElement('button');
		button.id = 'outside-menu-control'; button.textContent = '메뉴 밖 검사';
		button.style.cssText = 'position:fixed;bottom:8px;left:8px;z-index:1000';
		document.querySelector('.blog-header').after(button);
	});
	await settled();
}

async function openMember(key = 'Enter') {
	await memberTrigger().focus();
	await page.keyboard.press(key);
	await memberPanel().waitFor();
	await settled();
}

async function changeMember(next) {
	member = next;
	await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).refreshMemberSession());
	await settled();
}

async function startLogout(key = 'Enter') {
	await openMember();
	await page.keyboard.press('End');
	holdLogout = true;
	await page.keyboard.press(key);
	await page.waitForFunction(() => document.querySelector('.member-menu__item[aria-disabled="true"]'));
	return pendingLogout;
}

async function completeLogout(request, { success = true, clearMember = true } = {}) {
	const response = page.waitForResponse(value => value.request() === request.request());
	if (success) {
		if (clearMember) { member = null; }
		await ok(request);
	} else {
		await request.fulfill({ status: 503, json: { success: false, code: 'TEST_LOGOUT_FAILURE' } });
	}
	await (await response).finished();
	await settled();
}

for (const width of [1280, 390]) {
	for (const key of ['Enter', 'Space']) {
		test(`${width}: ${key} logout completion focuses the replacement login button`, async (context) => {
				await openPage(width);
				await page.evaluate(() => window.scrollTo(0, 240));
				await openMember();
			await page.keyboard.press('End');
			holdLogout = true;
			await page.keyboard.press(key);
			await page.waitForFunction(() => document.querySelector('.member-menu__item[aria-disabled="true"]'));
			assert.equal(await focused(memberPanel().getByRole('menuitem', { name: '로그아웃', exact: true })), true);
				const scrollBefore = await page.evaluate(() => window.scrollY);
			member = null; await ok(pendingLogout);
			const signin = page.locator('.member-menu__signin');
			await signin.waitFor(); await settled();
			context.diagnostic(`Completed focus: ${await page.evaluate(() => document.activeElement.tagName)}, login focused=${await focused(signin)}`);
			assert.equal(await focused(signin), true);
			assert.equal(await memberTrigger().count(), 0);
			assert.equal(await memberPanel().count(), 0);
			assert.equal(logoutRequests, 1);
				assert.equal(await page.evaluate(() => window.scrollY), scrollBefore);
		});

		test(`${width}: ${key} logout failure preserves the menu item and reports the error`, async () => {
			await openPage(width);
			const request = await startLogout(key);
			await completeLogout(request, { success: false });
			await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor();
			const logout = memberPanel().getByRole('menuitem', { name: '로그아웃', exact: true });
			assert.equal(await focused(logout), true);
			assert.equal(await logout.getAttribute('aria-disabled'), 'false');
			assert.equal(await memberTrigger().count(), 1);
			assert.equal(await page.locator('.member-menu__signin').count(), 0);
			assert.equal(logoutRequests, 1);
		});
	}

	test(`${width}: focus chosen after menu removal wins over the queued login restoration`, async () => {
		await openPage(width);
		const request = await startLogout();
		await page.evaluate(() => {
			const observer = new MutationObserver(() => {
				if (document.querySelector('.member-menu__signin')) {
					observer.disconnect();
					document.querySelector('#outside-menu-control').focus();
				}
			});
			observer.observe(document.querySelector('.blog-header'), { childList: true, subtree: true });
		});
		await completeLogout(request);
		await page.locator('.member-menu__signin').waitFor();
		assert.equal(await focused(outside()), true);
	});

	for (const success of [true, false]) {
		test(`${width}: outside focus survives pending logout ${success ? 'success' : 'failure'}`, async () => {
			await openPage(width);
			const request = await startLogout();
			await outside().focus();
			await completeLogout(request, { success });
			if (success) { await page.locator('.member-menu__signin').waitFor(); }
			else { await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).waitFor(); }
			assert.equal(await focused(outside()), true);
			assert.equal(logoutRequests, 1);
		});

		test(`${width}: unmount before logout ${success ? 'success' : 'failure'} leaves outside focus untouched`, async () => {
			await openPage(width);
			const request = await startLogout();
			await outside().evaluate(element => document.body.append(element));
			await page.evaluate(async () => (await import('/src/app/router/index.js')).default.push('/admin/login'));
			await page.locator('.blog-header').waitFor({ state: 'detached' });
			await outside().focus();
			await completeLogout(request, { success });
			assert.equal(await focused(outside()), true);
			assert.equal(await page.locator('.blog-header').count(), 0);
			assert.equal(await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).count(), 0);
		});

		for (const id of [1, 2]) {
			test(`${width}: late logout ${success ? 'success' : 'failure'} cannot change newer member ${id} pending or focus`, async () => {
				await openPage(width);
				const old = await startLogout();
				await page.evaluate(async () => (await import('/src/shared/api/blogApiClient.js')).advanceAuthenticationGeneration());
				await changeMember({ ...fakeMember, id, nickname: '새 로그인 회원' });
				const current = await startLogout();
				await completeLogout(old, { success, clearMember: false });
				const logout = memberPanel().getByRole('menuitem', { name: '로그아웃', exact: true });
				assert.equal(await logout.getAttribute('aria-disabled'), 'true');
				assert.equal(await focused(logout), true);
				assert.equal(await page.getByText('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', { exact: true }).count(), 0);
				assert.equal(logoutRequests, 2);
				await completeLogout(current);
				await page.locator('.member-menu__signin').waitFor();
				assert.equal(await focused(page.locator('.member-menu__signin')), true);
			});
		}
	}

	test(`${width}: changing route and returning cannot revive old logout focus or redirect a later route`, async () => {
		await openPage(width);
		const request = await startLogout();
		await page.evaluate(async () => {
			const router = (await import('/src/app/router/index.js')).default;
			await router.push('/privacy?away=1');
			await router.push('/privacy');
			await router.push('/privacy?later=1');
		});
		await outside().focus();
		await completeLogout(request);
		await page.locator('.member-menu__signin').waitFor();
		await settled();
		assert.equal(new URL(page.url()).search, '?later=1');
		assert.equal(await focused(outside()), true);
	});

	test(`${width}: an external signed-out transition without local logout does not force login focus`, async () => {
		await openPage(width); await openMember();
		await page.keyboard.press('End');
		await page.evaluate(async () => (await import('/src/features/member/data/memberAuthStore.js')).clearMemberSession());
		await page.locator('.member-menu__signin').waitFor(); await settled();
		assert.equal(await focused(page.locator('.member-menu__signin')), false);
		assert.equal(logoutRequests, 0);
	});

	test(`${width}: logout from settings retains the normal home redirect and replacement focus`, async () => {
		await openPage(width);
		await page.evaluate(async () => (await import('/src/app/router/index.js')).default.push('/settings'));
		const request = await startLogout();
		await completeLogout(request);
		await page.waitForURL(origin);
		await settled();
		assert.equal(await focused(page.locator('.member-menu__signin')), true);
	});

	for (const kind of ['site', 'member']) {
		test(`${width}: ${kind} Escape returns focus only to its own trigger`, async (context) => {
			await openPage(width);
			const trigger = kind === 'site' ? siteTrigger() : memberTrigger();
			const panel = kind === 'site' ? sitePanel() : memberPanel();
			await trigger.focus(); await page.keyboard.press('Enter');
			await panel.waitFor();
			await panel.locator('a').first().focus();
			await page.keyboard.press('Escape');
			await panel.waitFor({ state: 'detached' });
			const returned = await focused(trigger);
			context.diagnostic(`Escape focus returned: ${returned}`);
			assert.equal(returned, true);
			assert.equal(await trigger.getAttribute('aria-expanded'), 'false');
			assert.equal(await trigger.getAttribute('aria-controls'), null);
		});

		test(`${width}: ${kind} outside focus and pointer close without stealing focus`, async () => {
			await openPage(width);
			const trigger = kind === 'site' ? siteTrigger() : memberTrigger();
			const panel = kind === 'site' ? sitePanel() : memberPanel();
			await trigger.click(); await panel.waitFor();
			await panel.locator('a').first().focus();
			await outside().focus();
			await panel.waitFor({ state: 'detached' });
			assert.equal(await focused(outside()), true);
			await page.keyboard.press('Escape');
			assert.equal(await focused(outside()), true);
			await trigger.click(); await panel.waitFor();
			await outside().click();
			await panel.waitFor({ state: 'detached' });
			assert.equal(await focused(outside()), true);
		});
	}

	test(`${width}: site disclosure exposes expanded and controls while retaining native Tab links`, async (context) => {
		await openPage(width);
		assert.equal(await siteTrigger().getAttribute('aria-expanded'), 'false');
		assert.equal(await siteTrigger().getAttribute('aria-controls'), null);
		await siteTrigger().focus(); await page.keyboard.press('Enter');
		await sitePanel().getByRole('link', { name: '메뉴 검사 분류' }).waitFor();
		assert.equal(await siteTrigger().getAttribute('aria-expanded'), 'true');
		assert.equal(await sitePanel().getAttribute('id'), await siteTrigger().getAttribute('aria-controls'));
		assert.equal(await sitePanel().getAttribute('role'), null);
		assert.equal(await focused(siteTrigger()), true);
		const links = sitePanel().getByRole('link');
		for (let index = 0; index < await links.count(); index += 1) {
			await page.keyboard.press('Tab'); assert.equal(await focused(links.nth(index)), true);
		}
		await page.keyboard.press('Tab');
		await sitePanel().waitFor({ state: 'detached' });
		assert.equal(await focused(page.locator('.notification-bell__trigger')), true);
		context.diagnostic('Site Tab target: notification-bell__trigger, not BODY');
	});

	for (const key of ['Enter', 'Space']) {
		test(`${width}: member ${key} opens on the first item and arrows Home End stay scoped`, async () => {
			await openPage(width); await openMember(key);
			const items = memberPanel().getByRole('menuitem');
			assert.equal(await focused(items.first()), true);
			assert.equal(await memberPanel().getAttribute('id'), await memberTrigger().getAttribute('aria-controls'));
			assert.equal(await memberPanel().getAttribute('aria-labelledby'), await memberTrigger().getAttribute('id'));
			assert.equal(await items.first().getAttribute('tabindex'), '-1');
			await page.keyboard.press('End'); assert.equal(await focused(items.last()), true);
			await page.keyboard.press('ArrowDown'); assert.equal(await focused(items.first()), true);
			await page.keyboard.press('ArrowUp'); assert.equal(await focused(items.last()), true);
			await page.keyboard.press('Home'); assert.equal(await focused(items.first()), true);
			await page.keyboard.press('ArrowDown'); assert.equal(await focused(items.nth(1)), true);
		});
	}

	for (const key of ['Tab', 'Shift+Tab']) {
		test(`${width}: member ${key} leaves the menu naturally and closes it`, async (context) => {
			await openPage(width); await openMember();
			await memberPanel().getByRole('menuitem').first().focus();
			await page.keyboard.press(key);
			await memberPanel().waitFor({ state: 'detached' });
			if (key === 'Shift+Tab') { assert.equal(await focused(memberTrigger()), true); }
			else { assert.equal(await focused(outside()), true); }
			context.diagnostic(`Member ${key} focus: ${JSON.stringify(await page.evaluate(() => ({ tag: document.activeElement.tagName, id: document.activeElement.id, className: document.activeElement.className })))}`);
		});
	}

	test(`${width}: opening either header menu closes the other without redirecting focus`, async () => {
		await openPage(width);
		await siteTrigger().click(); await sitePanel().waitFor();
		await memberTrigger().click(); await memberPanel().waitFor();
		await sitePanel().waitFor({ state: 'detached' });
		assert.equal(await focused(memberTrigger()), true);
		await siteTrigger().click(); await sitePanel().waitFor();
		await memberPanel().waitFor({ state: 'detached' });
		await sitePanel().getByRole('link', { name: '전체 글', exact: true }).focus();
		await page.keyboard.press('Escape');
		assert.equal(await focused(siteTrigger()), true);
	});

	test(`${width}: site and member links preserve routes and admin visibility policy`, async () => {
		await openPage(width);
		await siteTrigger().click(); await sitePanel().getByRole('link', { name: '메뉴 검사 분류' }).click();
		await page.waitForURL('**/posts?category=3');
		await sitePanel().waitFor({ state: 'detached' });
		await openMember();
		assert.equal(await memberPanel().getByRole('menuitem', { name: '관리자', exact: true }).count(), 0);
		await memberPanel().getByRole('menuitem', { name: '최근 본 글', exact: true }).click();
		await page.waitForURL('**/settings#recent-posts');
		await memberPanel().waitFor({ state: 'detached' });
		await changeMember({ ...fakeMember, role: 'ROLE_ADMIN' });
		await openMember();
		assert.equal(await memberPanel().getByRole('menuitem', { name: '관리자', exact: true }).getAttribute('href'), '/admin/dashboard');
	});

	test(`${width}: logout and later login reset menu state and keep duplicate logout guarded`, async (context) => {
		await openPage(width); await openMember();
		holdLogout = true;
		const logout = memberPanel().getByRole('menuitem', { name: '로그아웃', exact: true });
		await logout.click();
		await page.waitForFunction(() => document.querySelector('.member-menu__item[aria-disabled="true"]'));
		await logout.focus(); await page.keyboard.press('Enter');
		await page.keyboard.press('Space');
		await logout.dblclick({ force: true });
		await settled();
		assert.equal(logoutRequests, 1);
		context.diagnostic(`Enter + Space + pointer dblclick during pending logout: ${logoutRequests} HTTP request`);
		member = null; await ok(pendingLogout);
		await page.locator('.member-menu__signin').waitFor();
		assert.equal(await memberPanel().count(), 0);
		await changeMember({ ...fakeMember });
		assert.equal(await memberTrigger().getAttribute('aria-expanded'), 'false');
		await openMember();
		assert.equal(await focused(memberPanel().getByRole('menuitem').first()), true);
	});

	test(`${width}: signed-out header retains disclosure navigation and the login route`, async (context) => {
		member = null;
		await openPage(width);
		assert.equal(await memberTrigger().count(), 0);
		await siteTrigger().focus(); await page.keyboard.press('Space');
		await sitePanel().getByRole('link', { name: '메뉴 검사 분류' }).waitFor();
		await sitePanel().getByRole('link', { name: '방명록', exact: true }).focus();
		await page.keyboard.press('Tab');
		assert.equal(await focused(page.locator('.member-menu__signin')), true);
		context.diagnostic('Signed-out Site Tab target: member-menu__signin, not BODY');
		await sitePanel().waitFor({ state: 'detached' });
		await page.keyboard.press('Enter');
		await page.waitForURL('**/login');
		await changeMember({ ...fakeMember });
		await openMember();
		assert.equal(await focused(memberPanel().getByRole('menuitem').first()), true);
	});

	test(`${width}: header unmount leaves outside Escape and focus untouched`, async () => {
		await openPage(width);
		await siteTrigger().click(); await sitePanel().waitFor();
		await outside().evaluate(element => document.body.append(element));
		await page.evaluate(async () => (await import('/src/app/router/index.js')).default.push('/admin/login'));
		await page.locator('.blog-header').waitFor({ state: 'detached' });
		await outside().focus();
		const prevented = await outside().evaluate(element => {
			const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
			element.dispatchEvent(event); return event.defaultPrevented;
		});
		assert.equal(prevented, false);
		assert.equal(await focused(outside()), true);
	});
}
