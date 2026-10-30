import { test } from 'node:test';
import assert from 'node:assert/strict';

import { createPageTracker, pageKey, shouldTrack } from '../src/shared/analytics/googleAnalytics.js';

const route = (fullPath, fields = {}) => ({ fullPath, name: 'post-list', meta: {}, ...fields });
const adminRoute = fullPath => route(fullPath, { name: 'admin-posts', meta: { requiresAdmin: true } });

function fakeBrowser(hostname = 'jsjlog.me') {
	const scripts = [];
	const win = { location: { hostname, origin: `https://${hostname}` } };
	const doc = {
		title: '정성주의 기록',
		head: { appendChild: element => scripts.push(element) },
		createElement: () => ({}),
	};
	// gtag 는 arguments 객체를 넣는다. 비교하려고 배열로 바꾼다
	const commands = () => (win.dataLayer ?? []).map(entry => Array.from(entry));
	const pageViews = () => commands().filter(([type, name]) => type === 'event' && name === 'page_view')
		.map(([, , params]) => params);
	return { win, doc, scripts, commands, pageViews };
}

test('admin and login callback routes are never sent; a hash change is the same page', () => {
	assert.equal(shouldTrack(route('/posts')), true);
	assert.equal(shouldTrack(adminRoute('/admin/posts')), false);
	assert.equal(shouldTrack(route('/admin/login', { name: 'admin-login' })), false);
	assert.equal(shouldTrack(route('/oauth/callback?code=x', { name: 'oauth-callback' })), false);
	assert.equal(pageKey(route('/posts/3?ref=a#comment-9')), '/posts/3?ref=a');
});

test('nothing loads without a measurement id or away from the production host', () => {
	for (const [measurementId, host] of [['', 'jsjlog.me'], ['G-TEST', 'localhost'], ['G-TEST', '127.0.0.1']]) {
		const browser = fakeBrowser(host);
		const tracker = createPageTracker({ measurementId, win: browser.win, doc: browser.doc });
		tracker.trackRoute(route('/posts'));
		assert.equal(browser.scripts.length, 0);
		assert.equal(browser.win.gtag, undefined);
	}
});

test('the first public page loads gtag once without ad signals and records the page itself', () => {
	const browser = fakeBrowser();
	const tracker = createPageTracker({ measurementId: 'G-TEST', win: browser.win, doc: browser.doc });

	tracker.trackRoute(route('/posts?page=2#top'));
	tracker.trackRoute(route('/guestbook', { name: 'guestbook' }));

	assert.equal(browser.scripts.length, 1);
	assert.equal(browser.scripts[0].src, 'https://www.googletagmanager.com/gtag/js?id=G-TEST');
	assert.equal(browser.scripts[0].async, true);
	const config = browser.commands().find(([type]) => type === 'config');
	assert.deepEqual(config, ['config', 'G-TEST', {
		send_page_view: false, allow_google_signals: false, allow_ad_personalization_signals: false,
	}]);
	assert.deepEqual(browser.pageViews().map(view => view.page_location),
		['https://jsjlog.me/posts?page=2', 'https://jsjlog.me/guestbook']);
});

test('hash-only moves are not new pages, but coming back after another page is', () => {
	const browser = fakeBrowser();
	const tracker = createPageTracker({ measurementId: 'G-TEST', win: browser.win, doc: browser.doc });

	tracker.trackRoute(route('/privacy'));
	tracker.trackRoute(route('/privacy#cookies'));
	tracker.trackRoute(adminRoute('/admin/posts'));
	tracker.trackRoute(route('/privacy'));

	assert.deepEqual(browser.pageViews().map(view => view.page_location),
		['https://jsjlog.me/privacy', 'https://jsjlog.me/privacy']);
});

test('starting on an admin page does not load gtag', () => {
	const browser = fakeBrowser();
	const tracker = createPageTracker({ measurementId: 'G-TEST', win: browser.win, doc: browser.doc });

	tracker.trackRoute(adminRoute('/admin/dashboard'));
	tracker.trackRoute(route('/oauth/callback?code=secret', { name: 'oauth-callback' }));

	assert.equal(browser.scripts.length, 0);
	assert.deepEqual(browser.pageViews(), []);
});

test('a post page waits for its title, sends once, and drops a post left before loading', () => {
	const browser = fakeBrowser();
	const tracker = createPageTracker({ measurementId: 'G-TEST', win: browser.win, doc: browser.doc });
	const post = id => route(`/posts/${id}`, { name: 'post-detail' });

	browser.doc.title = '글 불러오는 중 · 정성주의 기록';
	tracker.trackRoute(post(1), { waitForTitle: true });
	assert.deepEqual(browser.pageViews(), []);

	// 같은 글 안의 해시 이동은 기다리던 기록을 지우지 않는다
	tracker.trackRoute(route('/posts/1#comment-3', { name: 'post-detail' }), { waitForTitle: true });
	browser.doc.title = '스프링과 테스트코드 · 정성주의 기록';
	tracker.trackPendingPage();
	// 캐시 응답 뒤 새 응답이 와서 다시 불려도 한 번만 남는다
	tracker.trackPendingPage();

	// 제목이 오기 전에 떠난 글은 남기지 않는다
	tracker.trackRoute(post(2), { waitForTitle: true });
	browser.doc.title = '글 목록 · 정성주의 기록';
	tracker.trackRoute(route('/posts'));
	tracker.trackPendingPage();

	assert.deepEqual(browser.pageViews(), [
		{ page_location: 'https://jsjlog.me/posts/1', page_title: '스프링과 테스트코드 · 정성주의 기록' },
		{ page_location: 'https://jsjlog.me/posts', page_title: '글 목록 · 정성주의 기록' },
	]);
});
