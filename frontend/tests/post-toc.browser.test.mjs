import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';
let server, browser, page, errors, postContent;
const paragraphs = Array(17).fill('목차 이동을 확인하기 위한 충분히 긴 본문입니다.');
const content = ['## 첫 번째 제목', ...paragraphs, '## 두 번째 제목', ...paragraphs,
  '## 세 번째 제목', ...paragraphs, '<details>', '<summary>## 접힌 제목</summary>', '',
  '## 접힌 안쪽 제목', ...paragraphs, '</details>', ...paragraphs].join('\n\n');
const base = () => `${server.resolvedUrls.local[0]}posts/1?source=toc`;
const link = index => page.locator('.post-toc__link').nth(index - 1);
const arrived = id => page.waitForFunction(target => {
  const element = document.getElementById(`section-${target}`);
  return element && Math.abs(element.getBoundingClientRect().top - 88) < 2;
}, id).catch(async error => {
  console.log('scroll diagnostic', await page.evaluate(() => ({ url: location.href, scrollY, tops: [...document.querySelectorAll('.post-body__heading')].map(el => [el.id, el.getBoundingClientRect().top]), calls: window.scrollCalls, positions: window.positions.slice(-8) })));
  throw error;
});
before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  errors = [];
  postContent = content;
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'no-preference' });
  page.setDefaultTimeout(7000); page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    window.positions = []; window.scrollCalls = [];
    const original = window.scrollTo;
    window.scrollTo = function(...args) { window.scrollCalls.push(args[0]); return original.apply(this, args); };
    addEventListener('scroll', () => window.positions.push(scrollY));
  });
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    let data = null;
    if (/\/posts\/\d+$/.test(path)) data = { id: 1, categoryId: 1, categoryName: '기록', title: '목차 이동', excerpt: '요약', content: postContent, publishedAt: '2026-10-15T00:00:00', views: 1 };
    else if (path.endsWith('/comments')) data = { total: 0, items: [], hasNext: false };
    else if (path.endsWith('/related')) data = [];
    return route.fulfill({ json: { success: true, data } });
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });
async function open(hash = '') { await page.goto(base() + hash); await link(3).waitFor(); }
async function resetSamples() { await page.evaluate(() => { window.positions = [scrollY]; window.scrollCalls = []; }); }
async function assertAnchor(id) {
  assert.equal(new URL(page.url()).hash, `#section-${id}`);
  assert.equal(new URL(page.url()).searchParams.get('source'), 'toc');
  await page.waitForFunction(target => document.querySelector(`.post-toc__link[href$="#section-${target}"]`)?.getAttribute('aria-current') === 'location', id);
}
test('TOC click moves through intermediate positions and preserves URL and 88px offset', async () => {
  await open(); await resetSamples(); await link(3).click(); await arrived(3); await assertAnchor(3);
  const positions = await page.evaluate(() => window.positions);
  const destination = await page.evaluate(() => scrollY);
  assert.ok(positions.some(y => y > 10 && y < destination - 10), 'smooth movement has intermediate positions');
  assert.equal(await link(3).getAttribute('href'), '/posts/1?source=toc#section-3');
});
test('upward navigation, same link reselection and keyboard activation work', async () => {
  await open(); await link(3).click(); await arrived(3);
  await link(1).focus(); await page.keyboard.press('Enter'); await arrived(1); await assertAnchor(1);
  await page.evaluate(() => scrollTo({ top: 0, behavior: 'instant' }));
  await link(1).click(); await arrived(1);
});
test('last of rapidly selected links wins', async () => {
  await open(); await link(3).click(); await link(2).click(); await arrived(2); await assertAnchor(2);
});
test('reduced motion reaches the same heading without smooth animation', async () => {
  await page.emulateMedia({ reducedMotion: 'reduce' }); await open(); await resetSamples();
  await link(3).click(); await arrived(3);
  const calls = await page.evaluate(() => window.scrollCalls);
  assert.ok(calls.some(call => call?.behavior === 'instant'));
  assert.equal(calls.some(call => call?.behavior === 'smooth'), false);
});
test('direct hash, reload, back and forward retain expected locations', async () => {
  await open('#section-2'); await arrived(2); await page.reload(); await arrived(2);
  await link(3).click(); await arrived(3);
  await page.goBack(); await arrived(2); await assertAnchor(2);
  await page.goForward(); await arrived(3); await assertAnchor(3);
});
test('heading toggle remains closed on its own link; inner hash reveals its parent', async () => {
  await open(); await link(4).click(); await arrived(4);
  assert.equal(await page.locator('#section-4').evaluate(el => el.open), false);
  await page.evaluate(async () => {
    const router = (await import('/src/app/router/index.js')).default;
    await router.push({ path: '/posts/1', query: { source: 'toc' }, hash: '#section-5' });
  });
  await arrived(5); assert.equal(await page.locator('#section-4').evaluate(el => el.open), true);
});
test('modified click keeps the link available to open in another tab', async () => {
  await open();
  const result = await link(3).evaluate(el => {
    let routerPrevented;
    const observe = event => { routerPrevented = event.defaultPrevented; event.preventDefault(); };
    document.addEventListener('click', observe, { once: true });
    el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true }));
    return routerPrevented;
  });
  assert.equal(result, false); assert.equal(new URL(page.url()).hash, '');
});

test('posts without headings retain the same desktop column and reserved ad space', async () => {
  await open();
  const withToc = await page.locator('.post-shell__column').boundingBox();
  const asideWithToc = await page.locator('.post-aside').boundingBox();
  if (process.env.BLOG_TOC_SCREENSHOTS) {
    await page.screenshot({ path: `${process.env.BLOG_TOC_SCREENSHOTS}/with-toc.png` });
  }
  postContent = paragraphs.join('\n\n');
  await page.reload(); await page.locator('.post-body').waitFor();
  assert.equal(await page.locator('.post-toc').count(), 0);
  const withoutToc = await page.locator('.post-shell__column').boundingBox();
  const asideWithoutToc = await page.locator('.post-aside').boundingBox();
  assert.equal(withoutToc.width, withToc.width);
  assert.equal(asideWithoutToc.width, asideWithToc.width);
  assert.equal(asideWithoutToc.x, asideWithToc.x);
  assert.ok(asideWithoutToc.width > 0);
  assert.ok(asideWithoutToc.x > withoutToc.x + withoutToc.width);
  assert.equal(await page.locator('.post-aside').getAttribute('aria-hidden'), 'true');
  assert.equal(await page.locator('.post-aside .ad-card').count(), 0);
  if (process.env.BLOG_TOC_SCREENSHOTS) {
    await page.screenshot({ path: `${process.env.BLOG_TOC_SCREENSHOTS}/without-toc.png` });
  }
});

test('narrow screens keep their full-width content without an empty side column', async () => {
  postContent = paragraphs.join('\n\n');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(base()); await page.locator('.post-body').waitFor();
  assert.equal(await page.locator('.post-aside').isVisible(), false);
  const column = await page.locator('.post-shell__column').boundingBox();
  const layout = await page.locator('.post-shell__layout').boundingBox();
  assert.equal(column.width, layout.width);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
});

test('an unavailable font cannot keep a heading navigation pending indefinitely', async () => {
  await open();
  await page.evaluate(() => Object.defineProperty(document, 'fonts', {
    configurable: true, value: { status: 'loading', ready: new Promise(() => {}) },
  }));
  await link(3).click(); await arrived(3); await assertAnchor(3);
});
