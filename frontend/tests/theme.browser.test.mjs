import { after, afterEach, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

const STORAGE_KEY = 'jsjlog-theme';
let server, browser, page, errors;

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

/** 약관 화면을 연다. 헤더가 있고 API 에 기대는 것이 적은 화면이다 */
async function openPage(colorScheme, blockedMethods = []) {
  errors = [];
  page = await browser.newPage({ viewport: { width: 1280, height: 900 }, colorScheme });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(methods => {
    for (const method of methods) {
      Storage.prototype[method] = () => { throw new DOMException('Storage blocked', 'SecurityError'); };
    }
  }, blockedMethods);
  await page.route('**/api/v1/**', route => route.fulfill({ json: { success: true, data: null } }));
  await page.goto(`${server.resolvedUrls.local[0]}terms`);
  await page.locator('.theme-toggle').waitFor();
}

const state = () => page.evaluate(key => ({
  attribute: document.documentElement.dataset.theme ?? null,
  stored: localStorage.getItem(key),
  background: getComputedStyle(document.documentElement).backgroundColor,
  label: document.querySelector('.theme-toggle').getAttribute('aria-label'),
}), STORAGE_KEY);

test('follows the device until the reader picks, then keeps the pick across reloads', async () => {
  await openPage('dark');
  assert.deepEqual(await state(), { attribute: null, stored: null, background: 'rgb(18, 16, 14)', label: '밝은 화면으로 보기' });

  await page.locator('.theme-toggle').click();
  assert.deepEqual(await state(), { attribute: 'light', stored: 'light', background: 'rgb(244, 239, 232)', label: '어두운 화면으로 보기' });

  await page.reload();
  await page.locator('.theme-toggle').waitFor();
  assert.equal((await state()).background, 'rgb(244, 239, 232)');

  // 기기와 같은 쪽으로 돌아오면 기억을 지운다
  await page.locator('.theme-toggle').click();
  assert.deepEqual(await state(), { attribute: null, stored: null, background: 'rgb(18, 16, 14)', label: '밝은 화면으로 보기' });
});

test('the remembered pick is applied before the app code arrives', async () => {
  await openPage('light');
  await page.locator('.theme-toggle').click();
  assert.equal((await state()).stored, 'dark');

  // 앱 코드를 막아도 붙어 있어야 한다. 앱이 붙이면 그 전까지 밝은 화면이 한 번 그려진다
  await page.route('**/src/app/main.js', route => route.abort());
  await page.reload();
  assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'dark');
});

const visibleState = () => page.evaluate(() => ({
  attribute: document.documentElement.dataset.theme ?? null,
  background: getComputedStyle(document.documentElement).backgroundColor,
  disabled: document.querySelector('.theme-toggle').disabled,
  label: document.querySelector('.theme-toggle').getAttribute('aria-label'),
}));

async function expectDeviceTheme(colorScheme) {
  const dark = colorScheme === 'dark';
  await page.emulateMedia({ colorScheme });
  const label = `기기 설정에 따라 ${dark ? '어두운' : '밝은'} 화면 사용 중`;
  await page.waitForFunction(expected => document.querySelector('.theme-toggle').getAttribute('aria-label') === expected, label);
  assert.deepEqual(await visibleState(), {
    attribute: null,
    background: dark ? 'rgb(18, 16, 14)' : 'rgb(244, 239, 232)',
    disabled: true,
    label,
  });
}

test('blocked storage follows the device and does not offer a manual choice', async () => {
  await openPage('light', ['getItem', 'setItem', 'removeItem']);
  await expectDeviceTheme('light');
  await expectDeviceTheme('dark');
  await expectDeviceTheme('light');
});

test('a failed write keeps the screen and toggle on the device theme', async () => {
  await openPage('light', ['setItem']);
  await page.locator('.theme-toggle').click();
  await expectDeviceTheme('light');
  await expectDeviceTheme('dark');
  await expectDeviceTheme('light');
});

test('a remembered theme is cleared from the screen when storage reads start failing', async () => {
  await openPage('light');
  await page.locator('.theme-toggle').click();
  assert.equal((await state()).attribute, 'dark');
  await page.evaluate(() => {
    Storage.prototype.getItem = () => { throw new DOMException('Storage blocked', 'SecurityError'); };
  });
  await expectDeviceTheme('dark');
  await expectDeviceTheme('light');
});

test('a failed removal cannot reapply the stale remembered choice on a device change', async () => {
  await openPage('dark');
  await page.locator('.theme-toggle').click();
  assert.equal((await state()).stored, 'light');
  await page.evaluate(() => {
    Storage.prototype.removeItem = () => { throw new DOMException('Storage blocked', 'SecurityError'); };
  });
  await page.locator('.theme-toggle').click();
  await expectDeviceTheme('dark');
  await expectDeviceTheme('light');
  await expectDeviceTheme('dark');
});
