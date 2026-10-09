import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, groups, codes, requests, errors, failNextCodeUpdate;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = (route, status, code, message) => route.fulfill({ status, json: { success: false, code, message } });
const stamp = '2026-10-09T10:00:00';
const group = (groupCode, groupName, managedByEnum = false, enabled = true) => ({ groupCode, groupName, description: null, enabled, managedByEnum, updatedAt: stamp });
const code = (value, codeName, sortOrder, managedByEnum = false) => ({ code: value, codeName, description: null, sortOrder, enabled: true, managedByEnum, updatedAt: stamp });
const dialog = name => page.getByRole('dialog', { name });
const groupRow = name => page.locator('.admin-common-code__pane').first().getByRole('row').filter({ hasText: name });
const codeRows = () => page.locator('.admin-common-code__pane').nth(1).locator('tbody tr');
const rightTitle = () => page.locator('#common-code-codes-title');

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  groups = [group('ACTOR_TYPE', '처리자', true), group('MEMBER_STATUS', '회원 상태', true), group('MEMBER_STATUS_REASON', '회원 상태 변경 사유', true)];
  codes = {
    ACTOR_TYPE: [code('SELF', '본인', 1, true), code('ADMIN', '관리자', 2, true)],
    MEMBER_STATUS: [code('ACTIVE', '활동', 1, true), code('WITHDRAWN', '탈퇴', 3, true)],
    MEMBER_STATUS_REASON: [code('SIGNUP', '가입', 1, true)],
  };
  requests = []; errors = []; failNextCodeUpdate = false;
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    if (path === '/api/v1/admin/auth/me') return ok(route, { username: 'test', role: 'ADMIN' });
    if (path.endsWith('/csrf')) return ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' });

    const base = '/api/v1/admin/common-codes/groups';
    if (path.startsWith(base)) {
      const body = method === 'GET' ? null : request.postDataJSON();
      requests.push({ method, path: path.slice(base.length) || '/', query: Object.fromEntries(url.searchParams), body });
      const [, groupCode, part, codeValue] = path.slice(base.length).split('/');

      if (!groupCode && method === 'GET') {
        const keyword = (url.searchParams.get('keyword') ?? '').toLowerCase();
        const enabled = url.searchParams.get('enabled');
        const size = Number(url.searchParams.get('size') ?? 20);
        const index = Number(url.searchParams.get('page') ?? 0);
        const matched = groups.filter(item => (!keyword
            || item.groupCode.toLowerCase().includes(keyword) || item.groupName.toLowerCase().includes(keyword)
            || (codes[item.groupCode] ?? []).some(c => c.code.toLowerCase().includes(keyword) || c.codeName.includes(keyword)))
          && (enabled === null || String(item.enabled) === enabled));
        return ok(route, { items: matched.slice(index * size, index * size + size).map(item => ({ ...item, codeCount: (codes[item.groupCode] ?? []).length })),
          page: index, size, totalElements: matched.length, totalPages: Math.ceil(matched.length / size) });
      }
      if (!groupCode && method === 'POST') {
        const created = { ...group(body.groupCode, body.groupName), description: body.description };
        groups.push(created); codes[body.groupCode] = [];
        return ok(route, { ...created, codeCount: 0 });
      }
      if (groupCode && !part && method === 'PUT') {
        const target = groups.find(item => item.groupCode === groupCode);
        Object.assign(target, { groupName: body.groupName, description: body.description, enabled: body.enabled });
        return ok(route, { ...target, codeCount: codes[groupCode].length });
      }
      if (part === 'codes' && !codeValue && method === 'GET') return ok(route, codes[groupCode] ?? []);
      if (part === 'codes' && !codeValue && method === 'POST') {
        const created = code(body.code, body.codeName, body.sortOrder ?? codes[groupCode].length + 1);
        codes[groupCode].push(created);
        return ok(route, created);
      }
      if (part === 'codes' && codeValue && method === 'PUT') {
        if (failNextCodeUpdate) {
          failNextCodeUpdate = false;
          return fail(route, 409, 'MODIFIED_BY_OTHERS', '다른 곳에서 먼저 수정했습니다.');
        }
        const target = codes[groupCode].find(item => item.code === codeValue);
        Object.assign(target, { codeName: body.codeName, description: body.description, sortOrder: body.sortOrder, enabled: body.enabled });
        return ok(route, target);
      }
    }
    if (path === '/api/v1/admin/blog/members') {
      return ok(route, { items: [{ id: 7, nickname: '떠난 독자', email: null, provider: 'GOOGLE', status: 'WITHDRAWN',
        statusName: codes.MEMBER_STATUS[1].codeName, commentCount: 1, createdAt: '2026-10-01T09:00:00' }],
      statusCounts: { all: 1, active: 0, suspended: 0, withdrawn: 1 }, page: 0, size: 20, totalElements: 1, totalPages: 1 });
    }
    return ok(route, null);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

const open = path => page.goto(`${server.resolvedUrls.local[0]}${path}`);
const codesOf = () => codeRows().evaluateAll(rows => rows.map(row => row.textContent.replace(/\s+/g, ' ').trim()));

test('the first group opens on the right and picking another group shows its codes', async () => {
  await open('admin/codes');
  await rightTitle().filter({ hasText: '처리자' }).waitFor();
  await codeRows().filter({ hasText: 'SELF' }).waitFor();
  assert.equal(await groupRow('처리자').getAttribute('aria-current'), 'true');

  await groupRow('회원 상태 변경 사유').click();
  await rightTitle().filter({ hasText: '회원 상태 변경 사유' }).waitFor();
  await codeRows().filter({ hasText: 'SIGNUP' }).waitFor();
  assert.equal(await groupRow('회원 상태 변경 사유').getAttribute('aria-current'), 'true');
  assert.equal(await groupRow('처리자').getAttribute('aria-current'), null);
  assert.equal(await codeRows().filter({ hasText: 'SELF' }).count(), 0);
});

test('searching by a code name finds its groups and opens the first result', async () => {
  await open('admin/codes');
  await codeRows().filter({ hasText: 'SELF' }).waitFor();
  await page.locator('.admin-search input').first().fill('탈퇴');
  await page.getByRole('button', { name: '조회', exact: true }).click();

  await rightTitle().filter({ hasText: '회원 상태' }).waitFor();
  await codeRows().filter({ hasText: 'WITHDRAWN' }).waitFor();
  assert.equal(await groupRow('처리자').count(), 0);
  assert.deepEqual(requests.filter(r => r.method === 'GET' && r.path === '/').at(-1).query, { keyword: '탈퇴', page: '0', size: '20' });
});

test('a new group is created and opened, then codes can be added to it', async () => {
  await open('admin/codes');
  await codeRows().filter({ hasText: 'SELF' }).waitFor();

  await page.getByRole('button', { name: '+ 그룹 추가' }).click();
  await dialog('그룹 추가').waitFor();
  const groupInputs = dialog('그룹 추가').locator('input');
  await groupInputs.nth(0).fill('bank');
  await groupInputs.nth(1).fill('은행');
  await dialog('그룹 추가').getByRole('button', { name: '저장', exact: true }).click();
  await dialog('그룹 추가').waitFor({ state: 'hidden' });
  await rightTitle().filter({ hasText: '은행' }).waitFor();
  await page.getByText('이 그룹에 코드가 없습니다.').waitFor();
  assert.deepEqual(requests.find(r => r.method === 'POST').body, { groupCode: 'BANK', groupName: '은행', description: null });

  await page.getByRole('button', { name: '+ 코드 추가' }).click();
  await dialog('코드 추가').waitFor();
  const codeInputs = dialog('코드 추가').locator('input');
  await codeInputs.nth(0).fill('kb');
  await codeInputs.nth(1).fill('국민');
  await dialog('코드 추가').getByRole('button', { name: '저장', exact: true }).click();
  await dialog('코드 추가').waitFor({ state: 'hidden' });
  await codeRows().filter({ hasText: '국민' }).waitFor();
  assert.deepEqual(requests.filter(r => r.method === 'POST').at(-1),
    { method: 'POST', path: '/BANK/codes', query: {}, body: { code: 'KB', codeName: '국민', description: null, sortOrder: null } });
  await groupRow('은행').filter({ hasText: '1' }).waitFor();
});

test('server-managed groups cannot get new codes and their codes cannot be turned off', async () => {
  await open('admin/codes');
  await groupRow('회원 상태').first().click();
  await codeRows().filter({ hasText: 'WITHDRAWN' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '+ 코드 추가' }).isDisabled(), true);
  await page.getByText('서버 코드와 연결된 그룹이라').first().waitFor();

  await codeRows().filter({ hasText: 'WITHDRAWN' }).click();
  await dialog('코드 수정').waitFor();
  assert.equal(await dialog('코드 수정').getByRole('group', { name: '사용 여부' }).count(), 0);
});

test('editing a code sends the loaded timestamp; a conflict keeps the dialog open', async () => {
  await open('admin/codes');
  await groupRow('회원 상태').first().click();
  await codeRows().filter({ hasText: 'WITHDRAWN' }).click();
  await dialog('코드 수정').waitFor();
  // 관리자 입력칸(AdminTextInput)은 label 과 input 이 연결돼 있지 않아 이름으로 찾지 못한다. 첫 칸이 이름이다
  await dialog('코드 수정').locator('input').first().fill('탈퇴 회원');

  failNextCodeUpdate = true;
  await dialog('코드 수정').getByRole('button', { name: '저장', exact: true }).click();
  await dialog('코드 수정').getByText('다른 곳에서 먼저 수정했습니다.').waitFor();
  assert.equal(await dialog('코드 수정').isVisible(), true);

  await dialog('코드 수정').getByRole('button', { name: '저장', exact: true }).click();
  await dialog('코드 수정').waitFor({ state: 'hidden' });
  await codeRows().filter({ hasText: '탈퇴 회원' }).waitFor();
  const updates = requests.filter(r => r.method === 'PUT');
  assert.equal(updates.length, 2);
  assert.equal(updates[1].path, '/MEMBER_STATUS/codes/WITHDRAWN');
  assert.deepEqual(updates[1].body, { codeName: '탈퇴 회원', description: null, sortOrder: 3, enabled: true, updatedAt: stamp });
});

test('many groups are paged', async () => {
  for (let i = 1; i <= 23; i += 1) {
    groups.push(group(`G${String(i).padStart(2, '0')}`, `그룹 ${i}`));
  }
  await open('admin/codes');
  await groupRow('처리자').waitFor();
  await page.getByRole('button', { name: '2', exact: true }).click();
  await groupRow('그룹 23').waitFor();
  assert.equal(requests.filter(r => r.method === 'GET' && r.path === '/').at(-1).query.page, '1');
  // 다른 페이지로 넘어가도 보던 그룹은 오른쪽에 그대로 있다
  assert.match(await rightTitle().textContent(), /처리자/);
  assert.deepEqual((await codesOf()).length, 2);
});

test('member list uses common code names for rows and the status combo', async () => {
  codes.MEMBER_STATUS[1].codeName = '탈퇴 회원';
  await open('admin/members');
  await page.getByRole('row').filter({ hasText: '떠난 독자' }).waitFor();
  assert.match(await page.getByRole('row').filter({ hasText: '떠난 독자' }).textContent(), /탈퇴 회원/);

  const status = page.locator('.admin-search').getByRole('combobox');
  assert.equal((await status.textContent()).trim(), '전체 1');
  await status.click();
  assert.deepEqual(await page.getByRole('option').allTextContents().then(items => items.map(item => item.trim())),
    ['전체 1', '활동 0', '정지 0', '탈퇴 회원 1']);
  await page.getByRole('option', { name: '탈퇴 회원 1', exact: true }).click();
  const searched = page.waitForRequest(request => new URL(request.url()).searchParams.get('status') === 'WITHDRAWN');
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await searched;
});
