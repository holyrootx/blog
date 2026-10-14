import assert from 'node:assert/strict';
import { test } from 'node:test';
import { effectScope } from 'vue';
import { useAdminListRequest } from '../src/features/admin/composables/useAdminListRequest.js';

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fixture(t, options = {}) {
  const calls = [], applied = [], errors = [];
  const scope = effectScope();
  t.after(() => scope.stop());
  const list = scope.run(() => useAdminListRequest({
    request(params) {
      const pending = deferred();
      calls.push({ ...pending, params });
      return pending.promise;
    },
    apply: (result, params) => applied.push({ result, params }),
    errorMessage: '목록을 불러오지 못했습니다.',
    onError: (error) => errors.push(error),
    ...options,
  }));
  return { ...list, calls, applied, errors, scope };
}

test('응답 순서가 뒤집혀도 최신 검색의 목록과 집계만 반영한다', async (t) => {
  const list = fixture(t);
  const old = list.load({ keyword: '이전', page: 0, size: 20 });
  const currentParams = { keyword: '최신', page: 2, size: 50 };
  const current = list.load(currentParams);
  const currentResult = { items: [{ id: 2 }], totalElements: 120, totalPages: 3 };
  list.calls[1].resolve(currentResult);
  await current;
  list.calls[0].resolve({ items: [{ id: 1 }], totalElements: 1, totalPages: 1 });
  await old;
  assert.deepEqual(list.applied, [{ result: currentResult, params: currentParams }]);
  assert.equal(list.loading.value, false);
  assert.equal(list.loadError.value, '');
});

for (const fails of [false, true]) {
  test(`이전 요청의 ${fails ? '실패' : '성공'}은 진행 중인 최신 요청의 로딩을 끝내지 않는다`, async (t) => {
    const list = fixture(t);
    const old = list.load('old'), current = list.load('current');
    if (fails) list.calls[0].reject(new Error('늦은 오류'));
    else list.calls[0].resolve('이전 결과');
    await old;
    assert.equal(list.loading.value, true);
    assert.equal(list.loadError.value, '');
    assert.deepEqual(list.applied, []);
    assert.deepEqual(list.errors, []);
    list.calls[1].resolve('최신 결과');
    await current;
    assert.deepEqual(list.applied, [{ result: '최신 결과', params: 'current' }]);
    assert.equal(list.loading.value, false);
  });
}

test('이전 요청의 늦은 오류가 최신 성공 화면을 지우거나 오류로 바꾸지 않는다', async (t) => {
  const list = fixture(t);
  const old = list.load('old'), current = list.load('current');
  list.calls[1].resolve('최신 결과');
  await current;
  list.calls[0].reject(new Error('이전 검색 실패'));
  await old;
  assert.deepEqual(list.applied, [{ result: '최신 결과', params: 'current' }]);
  assert.deepEqual(list.errors, []);
  assert.equal(list.loadError.value, '');
});

test('최신 조회가 실패하면 이전 성공 응답으로 오류를 덮지 않고 재조회로 복구한다', async (t) => {
  const list = fixture(t, { errorMessage: (error) => error.message });
  const old = list.load('old'), current = list.load('current');
  const error = new Error('최신 검색 실패');
  list.calls[1].reject(error);
  await current;
  assert.equal(list.loading.value, false);
  assert.equal(list.loadError.value, error.message);
  list.calls[0].resolve('이전 결과');
  await old;
  assert.deepEqual(list.applied, []);
  assert.deepEqual(list.errors, [error]);
  assert.equal(list.loadError.value, error.message);

  const retry = list.load('retry');
  assert.equal(list.loadError.value, '');
  assert.equal(list.loading.value, true);
  list.calls[2].resolve('복구된 결과');
  await retry;
  assert.deepEqual(list.applied, [{ result: '복구된 결과', params: 'retry' }]);
  assert.equal(list.loading.value, false);
});

for (const fails of [false, true]) {
  test(`화면을 떠난 뒤 도착한 ${fails ? '실패' : '성공'} 응답은 반영하지 않는다`, async (t) => {
    const list = fixture(t);
    const pending = list.load('before leaving');
    list.scope.stop();
    if (fails) list.calls[0].reject(new Error('늦은 오류'));
    else list.calls[0].resolve('늦은 결과');
    await pending;
    assert.deepEqual(list.applied, []);
    assert.deepEqual(list.errors, []);
    assert.equal(list.loading.value, false);
    assert.equal(list.loadError.value, '');
    await list.load('after leaving');
    assert.equal(list.calls.length, 1);
  });
}

test('다른 목록 인스턴스의 요청은 서로 무효화하지 않는다', async (t) => {
  const members = fixture(t), images = fixture(t);
  const memberRequest = members.load('members'), imageRequest = images.load('images');
  members.calls[0].resolve('회원');
  await memberRequest;
  assert.equal(images.loading.value, true);
  images.calls[0].resolve('이미지');
  await imageRequest;
  assert.deepEqual(members.applied, [{ result: '회원', params: 'members' }]);
  assert.deepEqual(images.applied, [{ result: '이미지', params: 'images' }]);
});

test('요청 함수가 동기적으로 실패해도 오류를 표시하고 로딩을 끝낸다', async (t) => {
  const error = new Error('요청 준비 실패');
  const list = fixture(t, { request() { throw error; } });
  await list.load();
  assert.equal(list.loadError.value, '목록을 불러오지 못했습니다.');
  assert.equal(list.loading.value, false);
  assert.deepEqual(list.errors, [error]);
});

test('글 목록의 페이지 보정처럼 응답 처리 중 새 조회가 시작돼도 최신 조회까지 기다린다', async (t) => {
  const redirect = deferred();
  const list = fixture(t, { apply: () => redirect.promise });
  const old = list.load({ page: 3 });
  list.calls[0].resolve({ totalPages: 2 });
  await Promise.resolve();
  const current = list.load({ page: 2 });
  redirect.resolve();
  await old;
  assert.equal(list.loading.value, true);
  list.calls[1].resolve({ totalPages: 2 });
  await current;
  assert.equal(list.loading.value, false);
});
