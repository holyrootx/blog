import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readPostListQuery, toPostListQuery } from '../src/features/post/data/postListQuery.js';
import { routeDocumentMeta } from '../src/app/router/routeDocumentMeta.js';

const listRoute = (query = {}) => ({
	name: 'post-list', path: '/posts/', query, hash: '#top', meta: { title: '전체 글' },
});
const defaults = { page: 1, categoryId: null, sort: 'latest', keyword: '' };

test('default and tracking parameters do not create separate canonical URLs', () => {
	assert.deepEqual(readPostListQuery(), defaults);
	const meta = routeDocumentMeta(listRoute({ page: '01', sort: 'latest', utm_source: 'newsletter', q: ' ' }));
	assert.equal(meta.path, '/posts');
});

test('later pages, categories and alternative sorting keep their own content URLs', () => {
	const query = { page: '02', category: '003', sort: 'popular', utm_source: 'newsletter' };
	const state = readPostListQuery(query);
	assert.deepEqual(state, { ...defaults, page: 2, categoryId: 3, sort: 'popular' });
	assert.equal(routeDocumentMeta(listRoute(query)).path, '/posts?page=2&category=3&sort=popular');
});

test('the same normalized search terms are used for requests, titles and indexing', () => {
	const query = { q: '  Vue & 스프링 + 테스트  ', page: '3' };
	const state = readPostListQuery(query);
	const meta = routeDocumentMeta(listRoute(query));
	const encoded = new URL(meta.path, 'https://example.test').searchParams;
	assert.equal(state.keyword, 'Vue & 스프링 + 테스트');
	assert.equal(encoded.get('q'), state.keyword);
	assert.equal(encoded.get('page'), '3');
	assert.equal(meta.title, `‘${state.keyword}’ 검색 결과`);
	assert.equal(meta.robots, 'noindex,follow');
	assert.deepEqual(readPostListQuery(toPostListQuery(state)), state);
});

test('ignored short search terms do not exclude the ordinary list from indexing', () => {
	for (const q of ['', ' ', ' ㄱ ', null, ['Vue', 'Spring']]) {
		const meta = routeDocumentMeta(listRoute({ q }));
		assert.equal(meta.path, '/posts');
		assert.equal(meta.title, '전체 글');
		assert.equal(meta.robots, undefined);
	}
});

test('invalid and duplicate numeric parameters cannot change the API or canonical page', () => {
	for (const value of ['0', '-2', '0.5', '2.5', '1e3', 'Infinity', 'abc', ['2', '3'], null]) {
		assert.deepEqual(readPostListQuery({ page: value, category: value }), defaults);
		assert.equal(routeDocumentMeta(listRoute({ page: value, category: value })).path, '/posts');
	}
	assert.deepEqual(readPostListQuery({ page: '2147483648', category: '9007199254740992' }), defaults);
});

test('static pages ignore tracking and fragments while preserving existing noindex rules', () => {
	const meta = routeDocumentMeta({
		name: 'member-login', path: '/login/', query: { redirect: '/settings', utm_source: 'newsletter' },
		hash: '#form', meta: { title: '로그인', robots: 'noindex,follow' },
	});
	assert.deepEqual(meta, { title: '로그인', path: '/login', robots: 'noindex,follow' });
	assert.equal(routeDocumentMeta({ name: 'home', path: '/', query: {}, meta: {} }).path, '/');
});
