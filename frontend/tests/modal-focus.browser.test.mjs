import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, errors;
const dialog = name => page.getByRole('dialog', { name, exact: true });
const focused = locator => locator.evaluate(element => document.activeElement === element);
const settled = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
const activeInside = name => dialog(name).evaluate(element => element.contains(document.activeElement));

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
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', route => route.fulfill({ json: { success: true, data: [] } }));
  await page.goto(`${server.resolvedUrls.local[0]}tests/fixtures/modal-focus.html`);
  await page.locator('#search-trigger').waitFor();
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

test('opening search keeps the input focused so typing works without another click', async () => {
  await page.locator('#search-trigger').click();
  await settled();
  assert.equal(await focused(page.getByRole('combobox', { name: '검색어' })), true);
  await page.keyboard.type('Spring');
  assert.equal(await page.getByRole('combobox', { name: '검색어' }).inputValue(), 'Spring');
});

test('Tab and Shift+Tab wrap inside search even when the results footer appears', async () => {
  await page.locator('#search-trigger').click();
  await dialog('검색').getByRole('button', { name: '닫기' }).focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await focused(page.getByRole('combobox', { name: '검색어' })), true);
  await page.keyboard.type('Spring');
  await page.keyboard.press('Tab');
  assert.equal(await focused(dialog('검색').getByRole('button', { name: /전체 결과 보기/ })), true);
  await page.keyboard.press('Tab');
  assert.equal(await focused(dialog('검색').getByRole('button', { name: '닫기' })), true);
});

for (const method of ['Escape', 'close', 'backdrop']) {
  test(`${method} returns search focus and keeps the original scroll position`, async () => {
    await page.locator('#search-trigger').focus();
    await page.evaluate(() => window.scrollTo(0, 300));
    const before = await page.evaluate(() => window.scrollY);
    await page.keyboard.press('Enter');
    await settled();
    if (method === 'Escape') await page.keyboard.press('Escape');
    if (method === 'close') await dialog('검색').getByRole('button', { name: '닫기' }).click();
    if (method === 'backdrop') await page.mouse.click(5, 5);
    await dialog('검색').waitFor({ state: 'hidden' });
    await settled();
    assert.equal(await focused(page.locator('#search-trigger')), true);
    assert.equal(await page.evaluate(() => window.scrollY), before);
  });
}

test('nested dialogs close one at a time and preserve the underlying input and focus', async () => {
  await page.locator('#outer-trigger').click();
  await dialog('관리자 폼').locator('input').first().fill('저장 전 수정');
  await page.locator('#nested-trigger').click();
  await settled();
  assert.equal(await activeInside('확인'), true);
  const topIsVisible = await dialog('확인').evaluate(element => {
    const box = element.getBoundingClientRect();
    return element.contains(document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2));
  });
  assert.equal(topIsVisible, true, 'opening order must win over DOM order');
  await page.locator('#unmount').focus();
  await page.keyboard.press('Tab');
  assert.equal(await focused(dialog('확인').getByRole('button', { name: '닫기' })), true);
  await page.keyboard.press('Escape');
  await dialog('확인').waitFor({ state: 'hidden' });
  assert.equal(await focused(page.locator('#nested-trigger')), true);
  assert.equal(await dialog('관리자 폼').locator('input').first().inputValue(), '저장 전 수정');
  assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden');
  await page.keyboard.press('Escape');
  await dialog('관리자 폼').waitFor({ state: 'hidden' });
  assert.equal(await focused(page.locator('#outer-trigger')), true);
});

test('dynamic disabled or hidden controls are skipped and outside focus is contained', async () => {
  await page.locator('#outer-trigger').click();
  await dialog('관리자 폼').getByRole('button', { name: '마지막 버튼 사용 전환' }).click();
  await dialog('관리자 폼').getByRole('button', { name: '닫기' }).focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await focused(dialog('관리자 폼').getByRole('button', { name: '마지막 버튼 사용 전환' })), true);
  await page.keyboard.press('Tab');
  assert.equal(await focused(dialog('관리자 폼').getByRole('button', { name: '닫기' })), true);
  await dialog('관리자 폼').getByRole('button', { name: '마지막 버튼 표시 전환' }).click();
  await dialog('관리자 폼').getByRole('button', { name: '닫기' }).focus();
  await page.keyboard.press('Shift+Tab');
  assert.equal(await focused(dialog('관리자 폼').getByRole('button', { name: '마지막 버튼 사용 전환' })), true);
  await page.locator('#outside-after').evaluate(element => element.focus({ preventScroll: true }));
  assert.equal(await activeInside('관리자 폼'), true);
});

test('closing and unmounting restores previous body styles only after the last dialog', async () => {
  await page.evaluate(() => { document.body.style.overflow = 'scroll'; document.body.style.paddingRight = '13px'; });
  const before = await page.locator('#outer-trigger').boundingBox();
  await page.locator('#outer-trigger').click();
  const after = await page.locator('#outer-trigger').boundingBox();
  assert.equal(after.x, before.x);
  await page.locator('#nested-trigger').click();
  await page.keyboard.press('Escape');
  assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden');
  await page.keyboard.press('Escape');
  await settled();
  assert.deepEqual(await page.evaluate(() => [document.body.style.overflow, document.body.style.paddingRight]), ['scroll', '13px']);
  await page.locator('#outer-trigger').click();
  await page.locator('#nested-trigger').click();
  await page.locator('#unmount').click();
  await settled();
  assert.equal(await page.getByRole('dialog').count(), 0);
  assert.deepEqual(await page.evaluate(() => [document.body.style.overflow, document.body.style.paddingRight]), ['scroll', '13px']);
});

test('Escape during composition or handled by an inner control keeps the form open', async () => {
  await page.locator('#outer-trigger').click();
  await dialog('관리자 폼').locator('input').first().focus();
  await page.keyboard.down('a');
  await page.keyboard.up('a');
  await page.evaluate(() => document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true, isComposing: true })));
  assert.equal(await dialog('관리자 폼').isVisible(), true);
  await page.getByRole('textbox', { name: '내부에서 Escape 처리' }).focus();
  await page.keyboard.press('Escape');
  assert.equal(await dialog('관리자 폼').isVisible(), true);
});

test('each repeated admin label focuses its own input and retains the accessible name', async () => {
  await page.locator('#outer-trigger').click();
  const inputs = dialog('관리자 폼').getByRole('textbox', { name: '이름', exact: true });
  assert.equal(await inputs.count(), 2);
  const ids = await inputs.evaluateAll(elements => elements.map(element => element.id));
  assert.equal(new Set(ids).size, 2);
  for (let index = 0; index < 2; index += 1) {
    await dialog('관리자 폼').locator('label').nth(index).click();
    assert.equal(await focused(inputs.nth(index)), true);
  }
});

test('report radio focus and conditional reason input survive common modal focus handling', async () => {
  await page.locator('#report-trigger').click();
  await settled();
  assert.equal(await focused(page.getByRole('radio', { name: '광고·도배' })), true);
  await page.getByRole('radio', { name: '기타', exact: true }).check();
  await page.keyboard.press('Tab');
  assert.equal(await focused(page.getByRole('textbox', { name: '신고 사유 설명' })), true);
  await page.keyboard.type('신고 사유');
  await dialog('댓글 신고').getByRole('button', { name: '취소' }).focus();
  await page.keyboard.press('Tab');
  assert.equal(await focused(dialog('댓글 신고').getByRole('button', { name: '닫기' })), true);
  assert.equal(await page.getByRole('textbox', { name: '신고 사유 설명' }).inputValue(), '신고 사유');
});

for (const { width, height, theme } of [{ width: 1280, height: 900, theme: 'light' }, { width: 390, height: 844, theme: 'dark' }]) {
  test(`${width}px ${theme} dialog stays in the viewport and scrolls long content internally`, async () => {
    await page.setViewportSize({ width, height });
    await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
    await page.locator('#outer-trigger').click();
    const box = await dialog('관리자 폼').boundingBox();
    assert.ok(box.x >= 0 && box.y >= 0 && box.x + box.width <= width && box.y + box.height <= height);
    const body = dialog('관리자 폼').locator('.modal__body');
    assert.equal(await body.evaluate(element => element.scrollHeight > element.clientHeight), true);
    await dialog('관리자 폼').getByRole('button', { name: '닫기' }).focus();
    await page.keyboard.press('Shift+Tab');
    assert.equal(await focused(page.locator('#last')), true);
    if (process.env.QA_SCREENSHOT_DIR) {
      await mkdir(process.env.QA_SCREENSHOT_DIR, { recursive: true });
      await page.screenshot({ path: `${process.env.QA_SCREENSHOT_DIR}/modal-${width}-${theme}.png` });
    }
  });
}
