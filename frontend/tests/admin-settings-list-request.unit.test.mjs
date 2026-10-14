import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { computed, effectScope, onScopeDispose, reactive, ref } from 'vue';
import { useAdminListRequest } from '../src/features/admin/composables/useAdminListRequest.js';

const screens = [
  { page: 'AdminMenuPage', api: 'getAdminMenus', load: 'loadMenus', rows: 'menus', loading: 'loading', error: 'loadError', extra: 'collapsedGroupIds,toggleGroup', result: (items) => items },
  { page: 'AdminCategoryPage', api: 'getAdminCategories', load: 'loadCategories', rows: 'categories', loading: 'loading', error: 'loadError', extra: 'selectedIds,page', result: (items) => items },
  { page: 'AdminCommonCodePage', api: 'getAdminCommonCodeGroups', load: 'loadGroups', rows: 'groups', loading: 'groupsLoading', error: 'groupsError', extra: 'selectedGroup,selectGroup,search,condition,applied,page,pageSize,changePage,groupsPending,codes,codesLoading,codesError,loadCodes', result: (items) => ({ items, totalElements: items.length, totalPages: 2 }) },
];
const item = (id) => ({ id, name: `항목 ${id}`, groupCode: `GROUP_${id}`, groupName: `그룹 ${id}` });
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

// 기존 회귀 테스트와 같이 화면의 실제 script를 실행하고 API 경계만 대체한다.
async function fixture(t, screen, injected = {}) {
  const source = (await readFile(new URL(`../src/features/admin/pages/${screen.page}.vue`, import.meta.url), 'utf8'))
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '');
  const scope = effectScope(), calls = [], errors = [];
  t.after(() => scope.stop());
  const names = `${screen.load},${screen.rows},${screen.loading},${screen.error},${screen.extra}`;
  const state = scope.run(() => vm.runInNewContext(`${source}\n;({${names}})`, {
    ref, reactive, computed, onScopeDispose, useAdminListRequest, onMounted() {},
    console: { error: (error) => errors.push(error) }, notifyError() {}, notifySuccess() {},
    [screen.api]: (params) => { const call = { ...deferred(), params }; calls.push(call); return call.promise; },
    getAdminCommonCodes: async () => [],
    ...injected,
  }));
  return { state, calls, errors, scope, load: () => state[screen.load]({ keyword: '검색' }) };
}

for (const screen of screens) {
  test(`${screen.page}: 늦은 이전 응답이 최신 목록과 화면 선택을 덮지 않는다`, async (t) => {
    const f = await fixture(t, screen);
    const old = f.load(), latest = f.load();
    const items = Array.from({ length: 12 }, (_, index) => item(index + 20));
    f.calls[1].resolve(screen.result(items));
    await latest;
    if (screen.page === 'AdminMenuPage') f.state.toggleGroup(20);
    if (screen.page === 'AdminCategoryPage') { f.state.selectedIds.value = [20]; f.state.page.value = 2; }
    if (screen.page === 'AdminCommonCodePage') f.state.selectGroup(items[1]);
    f.calls[0].resolve(screen.result([item(1)]));
    await old;
    assert.equal(f.state[screen.rows].value[0].id, 20);
    assert.equal(f.state[screen.loading].value, false);
    if (screen.page === 'AdminMenuPage') assert.equal(f.state.collapsedGroupIds.value[0], 20);
    if (screen.page === 'AdminCategoryPage') {
      assert.equal(f.state.selectedIds.value[0], 20);
      assert.equal(f.state.page.value, 2);
    }
    if (screen.page === 'AdminCommonCodePage') assert.equal(f.state.selectedGroup.value.groupCode, 'GROUP_21');
  });

  test(`${screen.page}: 이전 조회 실패는 최신 조회의 로딩과 오류 상태에 영향을 주지 않는다`, async (t) => {
    const f = await fixture(t, screen);
    const old = f.load(), latest = f.load();
    f.calls[0].reject(new Error('이전 요청 실패'));
    await old;
    assert.equal(f.state[screen.loading].value, true);
    assert.equal(f.state[screen.error].value, '');
    assert.equal(f.errors.length, 0);
    if (screen.page === 'AdminCommonCodePage') assert.equal(f.state.groupsPending.value, true);
    f.calls[1].resolve(screen.result([item(20)]));
    await latest;
    assert.equal(f.state[screen.loading].value, false);
    assert.equal(f.state[screen.rows].value[0].id, 20);
  });

  test(`${screen.page}: 최신 실패를 이전 성공으로 숨기지 않고 재조회로 복구한다`, async (t) => {
    const f = await fixture(t, screen);
    const old = f.load(), latest = f.load();
    f.calls[1].reject(new Error('최신 요청 실패'));
    await latest;
    const message = f.state[screen.error].value;
    assert.notEqual(message, '');
    f.calls[0].resolve(screen.result([item(1)]));
    await old;
    assert.equal(f.state[screen.error].value, message);
    assert.equal(f.state[screen.rows].value.length, 0);
    const retry = f.load();
    assert.equal(f.state[screen.error].value, '');
    f.calls[2].resolve(screen.result([item(20)]));
    await retry;
    assert.equal(f.state[screen.rows].value[0].id, 20);
    assert.equal(f.state[screen.loading].value, false);
  });
}

test('카테고리 최신 재조회는 선택을 초기화하고 줄어든 마지막 페이지로 보정한다', async (t) => {
  const f = await fixture(t, screens[1]);
  f.state.categories.value = Array.from({ length: 21 }, (_, index) => item(index + 1));
  f.state.selectedIds.value = [21];
  f.state.page.value = 3;
  const request = f.load();
  f.calls[0].resolve([item(1)]);
  await request;
  assert.equal(f.state.page.value, 1);
  assert.equal(f.state.selectedIds.value.length, 0);
});

test('공통 코드 그룹의 페이지 이동은 적용한 검색 조건과 선택한 그룹을 유지한다', async (t) => {
  const f = await fixture(t, screens[2]);
  f.state.applied.value = { keyword: '적용한 조건', enabled: 'true' };
  f.state.condition.keyword = '아직 조회하지 않은 조건';
  f.state.selectedGroup.value = item(20);
  f.state.codes.value = [{ code: 'KEPT' }];
  const request = f.state.changePage(2);
  assert.equal(f.calls[0].params.keyword, '적용한 조건');
  assert.equal(f.calls[0].params.page, 1);
  assert.equal(f.calls[0].params.size, 20);
  f.calls[0].resolve(screens[2].result([item(30)]));
  await request;
  assert.equal(f.state.selectedGroup.value.groupCode, 'GROUP_20');
  assert.equal(f.state.codes.value[0].code, 'KEPT');
});

for (const fails of [false, true]) {
  test(`공통 코드 새 검색 결과가 비어 있으면 이전 그룹의 늦은 ${fails ? '실패' : '성공'}를 무시한다`, async (t) => {
    const pending = deferred();
    const f = await fixture(t, screens[2], { getAdminCommonCodes: () => pending.promise });
    f.state.selectedGroup.value = item(1);
    const oldCodes = f.state.loadCodes();
    const search = f.state.search();
    f.calls[0].resolve(screens[2].result([]));
    await search;
    if (fails) pending.reject(new Error('이전 그룹의 오류'));
    else pending.resolve([{ code: 'OLD' }]);
    await oldCodes;
    assert.equal(f.state.selectedGroup.value, null);
    assert.equal(f.state.codes.value.length, 0);
    assert.equal(f.state.codesError.value, '');
    assert.equal(f.state.codesLoading.value, false);
    assert.equal(f.errors.length, 0);
  });
}

test('공통 코드 화면을 떠나면 늦은 그룹·코드 응답을 모두 무시한다', async (t) => {
  const pending = deferred();
  const f = await fixture(t, screens[2], { getAdminCommonCodes: () => pending.promise });
  f.state.selectedGroup.value = item(1);
  const codesRequest = f.state.loadCodes(), groupsRequest = f.load();
  f.scope.stop();
  pending.resolve([{ code: 'OLD' }]);
  f.calls[0].resolve(screens[2].result([item(20)]));
  await Promise.all([codesRequest, groupsRequest]);
  assert.equal(f.state.groups.value.length, 0);
  assert.equal(f.state.codes.value.length, 0);
});
