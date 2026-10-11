import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readAdminPostListQuery, toAdminPostListQuery } from '../src/features/admin/data/adminPostListQuery.js';

const defaults = { page: 1, pageSize: 20, status: '', categoryId: '', keyword: '' };

test('direct entry uses the default list; default values do not lengthen the URL', () => {
  assert.deepEqual(readAdminPostListQuery(), defaults);
  assert.deepEqual(toAdminPostListQuery(defaults), {});
});

test('page, size and all applied filters survive a URL round trip', () => {
  const query = { page: '3', size: '50', status: 'DRAFT', categoryId: '42', keyword: '한글 & Vue = 검색' };
  const state = readAdminPostListQuery(query);
  assert.deepEqual(state, { page: 3, pageSize: 50, status: 'DRAFT', categoryId: '42', keyword: query.keyword });
  assert.deepEqual(toAdminPostListQuery(state), query);
});

for (const invalid of ['0', '-1', '2.5', 'Infinity', '1e3', 'abc', ['3', '4'], null]) {
  test(`malformed numbers and duplicate query parameters are rejected: ${JSON.stringify(invalid)}`, () => {
    assert.deepEqual(readAdminPostListQuery({ page: invalid, size: invalid, categoryId: invalid }), defaults);
  });
}

test('unsupported sizes, statuses, unsafe IDs and oversized API pages fall back independently', () => {
  assert.deepEqual(readAdminPostListQuery({
    page: '2147483648', size: '30', status: 'DELETED', categoryId: '9007199254740992', keyword: ['a', 'b'],
  }), defaults);
  assert.deepEqual(readAdminPostListQuery({ page: '3', size: '30', status: ['DRAFT'], categoryId: '8' }),
    { ...defaults, page: 3, categoryId: '8' });
});

test('supported sizes and trash remain usable; normalization keeps meaningful search text', () => {
  for (const size of [10, 20, 50, 100]) {
    const state = readAdminPostListQuery({ size: String(size), status: 'TRASH', keyword: '  내용 검색  ' });
    assert.equal(state.pageSize, size);
    assert.equal(state.status, 'TRASH');
    assert.equal(state.keyword, '내용 검색');
  }
});
