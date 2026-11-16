import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { computed, nextTick, reactive, ref } from 'vue';
import { readPostListQuery, toPostListQuery } from '../src/features/post/data/postListQuery.js';

const emptyResult = { items: [], totalElements: 0, totalPages: 0 };
const resultWithPost = { items: [{ id: 2 }], totalElements: 1, totalPages: 1 };
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const settle = async () => { await Promise.resolve(); await nextTick(); };

test('empty search completes and is cached before its optional recommendation arrives', async () => {
	const recommendation = deferred();
	const p = await page({ getHomePosts: () => recommendation.promise });
	await p.load();
	assert.equal(p.loading.value, false);
	assert.equal(p.fallbackLoading.value, true);
	assert.equal(p.posts.value.length, 0);
	assert.equal(p.totalElements.value, 0);
	assert.equal(p.cache.readCachedResponse(p.cacheKey).items.length, 0);
	recommendation.resolve([{ id: 9 }]);
	await settle();
	assert.equal(p.fallbackLoading.value, false);
	assert.equal(p.fallbackPosts.value[0].id, 9);
});

test('a failed optional recommendation leaves the completed empty search intact', async () => {
	const recommendation = deferred();
	const p = await page({ getHomePosts: () => recommendation.promise });
	await p.load();
	recommendation.reject(new Error('recommendation unavailable'));
	await settle();
	assert.equal(p.loading.value, false);
	assert.equal(p.failed.value, false);
	assert.equal(p.fallbackLoading.value, false);
	assert.equal(p.fallbackPosts.value.length, 0);
	assert.equal(p.errors.length, 1);
});

for (const [keyword, result] of [['', emptyResult], ['found', resultWithPost]]) {
	test(`recommendations are skipped when no empty search needs them (keyword=${keyword})`, async () => {
		let calls = 0;
		const p = await page({
			keyword, getPosts: async () => result,
			getHomePosts: async () => { calls += 1; return []; },
		});
		await p.load();
		assert.equal(calls, 0);
		assert.equal(p.loading.value, false);
		assert.equal(p.fallbackLoading.value, false);
		assert.equal(p.posts.value.length, result.items.length);
	});
}

for (const rejectOld of [false, true]) {
	test(`an old recommendation cannot change a newer search or its loading state (failure=${rejectOld})`, async () => {
		const old = deferred(), current = deferred();
		let calls = 0;
		const p = await page({ getHomePosts: () => (++calls === 1 ? old.promise : current.promise) });
		await p.load();
		p.route.query = { q: 'new' };
		await p.load();
		rejectOld ? old.reject(new Error('stale recommendation')) : old.resolve([{ id: 9 }]);
		await settle();
		assert.equal(p.fallbackLoading.value, true);
		assert.equal(p.fallbackPosts.value.length, 0);
		assert.equal(p.failed.value, false);
		assert.equal(p.errors.length, 0);
		current.resolve([{ id: 10 }]);
		await settle();
		assert.equal(p.fallbackPosts.value[0].id, 10);
		assert.equal(p.fallbackLoading.value, false);
	});
}

for (const rejectOld of [false, true]) {
	test(`an unmounted list ignores recommendation completion and does not write its cache (failure=${rejectOld})`, async () => {
		const recommendation = deferred();
		const p = await page({ getHomePosts: () => recommendation.promise });
		await p.load();
		p.unmount();
		rejectOld ? recommendation.reject(new Error('stale failure')) : recommendation.resolve([{ id: 9 }]);
		await settle();
		assert.equal(p.fallbackPosts.value.length, 0);
		assert.equal(p.cache.readCachedResponse(`${p.cacheKey}:fallback`), undefined);
		assert.equal(p.errors.length, 0);
	});
}

test('returning to a cached empty search restores both regions and refreshes recommendations once', async () => {
	const freshList = deferred(), freshRecommendation = deferred();
	let listCalls = 0, recommendationCalls = 0;
	const p = await page({
		getPosts: () => (++listCalls === 1 ? Promise.resolve(emptyResult) : freshList.promise),
		getHomePosts: () => (++recommendationCalls === 1 ? Promise.resolve([{ id: 9 }]) : freshRecommendation.promise),
	});
	await p.load();
	await settle();
	const revalidation = p.load();
	assert.equal(p.loading.value, false);
	assert.equal(p.fallbackLoading.value, false);
	assert.equal(p.fallbackPosts.value[0].id, 9);
	freshList.resolve(emptyResult);
	await revalidation;
	assert.equal(recommendationCalls, 2);
	freshRecommendation.resolve([{ id: 10 }]);
	await settle();
	assert.equal(p.fallbackPosts.value[0].id, 10);
	assert.equal(p.cache.readCachedResponse(`${p.cacheKey}:fallback`)[0].id, 10);
});

test('failed revalidation preserves cached search results and recommendations', async () => {
	const freshList = deferred(), freshRecommendation = deferred();
	let listCalls = 0, recommendationCalls = 0;
	const p = await page({
		getPosts: () => (++listCalls === 1 ? Promise.resolve(emptyResult) : freshList.promise),
		getHomePosts: () => (++recommendationCalls === 1 ? Promise.resolve([{ id: 9 }]) : freshRecommendation.promise),
	});
	await p.load();
	await settle();
	const revalidation = p.load();
	freshList.reject(new Error('search unavailable'));
	freshRecommendation.reject(new Error('recommendation unavailable'));
	await revalidation;
	await settle();
	assert.equal(p.loading.value, false);
	assert.equal(p.failed.value, false);
	assert.equal(p.fallbackPosts.value[0].id, 9);
	assert.equal(p.fallbackLoading.value, false);
	assert.equal(p.errors.length, 2);
});

test('fresh nonempty results cancel a cached empty search recommendation request', async () => {
	const freshList = deferred(), freshRecommendation = deferred();
	let listCalls = 0, recommendationCalls = 0;
	const p = await page({
		getPosts: () => (++listCalls === 1 ? Promise.resolve(emptyResult) : freshList.promise),
		getHomePosts: () => (++recommendationCalls === 1 ? Promise.resolve([{ id: 9 }]) : freshRecommendation.promise),
	});
	await p.load();
	await settle();
	const revalidation = p.load();
	freshList.resolve(resultWithPost);
	await revalidation;
	assert.equal(p.posts.value[0].id, 2);
	assert.equal(p.fallbackPosts.value.length, 0);
	assert.equal(p.fallbackLoading.value, false);
	freshRecommendation.resolve([{ id: 10 }]);
	await settle();
	assert.equal(p.fallbackPosts.value.length, 0);
	assert.equal(p.cache.readCachedResponse(`${p.cacheKey}:fallback`)[0].id, 9);
});

test('a failed search without a cache shows the search error and skips recommendations', async () => {
	let calls = 0;
	const p = await page({
		getPosts: async () => { throw new Error('search unavailable'); },
		getHomePosts: async () => { calls += 1; return []; },
	});
	await p.load();
	assert.equal(p.failed.value, true);
	assert.equal(p.loading.value, false);
	assert.equal(p.fallbackLoading.value, false);
	assert.equal(calls, 0);
});

async function page({ keyword = 'missing', getPosts = async () => emptyResult, getHomePosts = async () => [] } = {}) {
	const hooks = [], errors = [];
	const cache = await run('shared/data/responseCache.js', ['readCachedResponse', 'writeCachedResponse']);
	const { useSectionLoader } = await run('shared/composables/useSectionLoader.js', ['useSectionLoader'], {
		...cache, onBeforeUnmount: callback => hooks.push(callback),
	});
	const route = reactive({ query: { q: keyword } });
	const state = await run('features/post/pages/PostListPage.vue', [
		'load', 'posts', 'fallbackPosts', 'loading', 'fallbackLoading', 'failed', 'totalElements',
	], {
		getPosts, getHomePosts, useSectionLoader, readPostListQuery, toPostListQuery,
		useRoute: () => route, useRouter: () => ({}), watch() {}, loadCategories: async () => [],
		console: { error: error => errors.push(error) },
	});
	return {
		...state, route, cache, errors, unmount: () => hooks.forEach(hook => hook()),
		cacheKey: `post-list:${JSON.stringify({ page: 1, size: 12, categoryId: null, sort: 'latest', keyword })}`,
	};
}

// Vue 상태와 실제 loader/cache를 실행하고 HTTP 경계와 unmount 시점만 제어한다.
async function run(path, names, injected = {}) {
	let source = await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8');
	if (path.endsWith('.vue')) {
		source = source.match(/<script setup>([\s\S]*?)<\/script>/)[1];
	}
	source = source.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '')
		.replace(/\bexport (?=(async )?function|const|class)/g, '');
	const context = vm.createContext({ reactive, computed, ref, console, ...injected });
	return vm.runInContext(`"use strict";\n${source}\n;({${names.join(',')}})`, context);
}
