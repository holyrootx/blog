import { after, afterEach, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import vue from '@vitejs/plugin-vue';
import { chromium } from 'playwright';

let server, browser, page, pending, pendingStatus, pendingReads, actions, errors, submitFailure, csrfStatus, pendingGate;
const ok = (route, data = null) => route.fulfill({ json: { success: true, data } });
const fail = (route, status, code) => route.fulfill({ status,
  json: { success: false, code, message: '요청을 완료하지 못했습니다.' } });
const choice = () => page.getByRole('heading', { name: '예전에 쓰시던 계정이 있습니다' });

before(async () => {
  server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false,
    plugins: [vue()], server: { host: '127.0.0.1', port: 0 } });
  await server.listen();
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
});
after(async () => { await browser?.close(); await server?.close(); });
beforeEach(async () => {
  pending = { loginState: 'SIGNUP_REQUIRED', suggestedNickname: '제공자 이름' };
  pendingStatus = 200;
  pendingReads = 0;
  actions = [];
  errors = [];
  submitFailure = null;
  csrfStatus = 200;
  pendingGate = null;
  page = await browser.newPage();
  page.setDefaultTimeout(5000);
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/csrf')) return csrfStatus === 200
      ? ok(route, { headerName: 'X-CSRF-TOKEN', token: 'test' })
      : fail(route, csrfStatus, 'INTERNAL_SERVER_ERROR');
    if (path === '/api/v1/auth/oauth/pending') {
      pendingReads++;
      if (pendingGate) await pendingGate;
      if (pendingStatus === 0) return route.abort('failed');
      return pendingStatus === 200 ? ok(route, pending) : fail(route, pendingStatus, pendingStatus === 401 ? 'OAUTH_SESSION_EXPIRED' : 'INTERNAL_SERVER_ERROR');
    }
    if (path.startsWith('/api/v1/auth/oauth/') && request.method() === 'POST') {
      actions.push({ action: path.split('/').at(-1), body: request.postDataJSON() });
      if (submitFailure) return fail(route, submitFailure.status, submitFailure.code);
      return ok(route, { id: 7, nickname: '완료한 회원', role: 'USER' });
    }
    return ok(route);
  });
});
afterEach(async () => { await page?.close(); assert.deepEqual(errors, []); });

async function open(result = 'signup-required') {
  await page.goto(`${server.resolvedUrls.local[0]}oauth/callback?result=${result}`);
}
async function staleSignup() {
  await open();
  await page.getByLabel('닉네임', { exact: true }).fill('내가 입력한 이름');
  pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '예전 이름' };
  submitFailure = { status: 409, code: 'OAUTH_SIGNUP_NOT_ALLOWED' };
  await page.getByRole('button', { name: '시작하기', exact: true }).click();
  await choice().waitFor();
  assert.equal(pendingReads, 2);
  assert.deepEqual(actions.map(item => item.action), ['signup']);
  submitFailure = null;
}

test('the current server state overrides an old signup URL without submitting a choice', async () => {
  pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '예전 이름' };
  await open();
  await choice().waitFor();
  await page.getByText('예전 이름', { exact: true }).waitFor();
  assert.deepEqual(actions, []);
});

test('an expired recovery choice opens the normal signup form', async () => {
  await open('reactivation-required');
  await page.getByRole('heading', { name: '닉네임을 정해 주세요' }).waitFor();
  assert.deepEqual(actions, []);
});

test('a stale signup returns to the choice and restores only after the reader selects it', async () => {
  await staleSignup();
  await page.getByRole('button', { name: /예전 계정 쓰기/ }).click();
  await page.waitForURL(server.resolvedUrls.local[0]);
  assert.deepEqual(actions.map(item => item.action), ['signup', 'reactivate']);
});

test('a stale signup creates a new account only after the explicit new-account form is submitted', async () => {
  await staleSignup();
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  await page.getByRole('heading', { name: '새 계정으로 시작합니다' }).waitFor();
  assert.deepEqual(actions.map(item => item.action), ['signup']);
  await page.getByLabel('닉네임', { exact: true }).fill('새로 고른 이름');
  await page.getByRole('button', { name: '새로 만들기', exact: true }).click();
  await page.waitForURL(server.resolvedUrls.local[0]);
  assert.deepEqual(actions.at(-1), { action: 'rejoin', body: { nickname: '새로 고른 이름' } });
});

test('a concurrent request that already completed elsewhere asks for a new login', async () => {
  await open();
  await page.getByRole('button', { name: '시작하기', exact: true }).waitFor();
  pendingStatus = 401;
  submitFailure = { status: 409, code: 'DATA_CONSTRAINT_VIOLATED' };
  await page.getByRole('button', { name: '시작하기', exact: true }).click();
  await page.getByRole('heading', { name: '로그인 정보가 만료되었습니다' }).waitFor();
  assert.deepEqual(actions.map(item => item.action), ['signup']);
});

test('a lock conflict refreshes the choice but preserves the nickname when signup is still available', async () => {
  await open();
  await page.getByLabel('닉네임', { exact: true }).fill('그대로 보존할 이름');
  submitFailure = { status: 409, code: 'RESOURCE_BUSY' };
  await page.getByRole('button', { name: '시작하기', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '가입·복구 정보를 다시 확인했습니다.' }).waitFor();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '그대로 보존할 이름');
  assert.equal(pendingReads, 2);
  assert.deepEqual(actions.map(item => item.action), ['signup']);
});

test('other server failures keep the entered nickname and do not retry a write', async () => {
  await open();
  await page.getByLabel('닉네임', { exact: true }).fill('실패해도 남길 이름');
  submitFailure = { status: 500, code: 'INTERNAL_SERVER_ERROR' };
  await page.getByRole('button', { name: '시작하기', exact: true }).click();
  await page.getByRole('alert').waitFor();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '실패해도 남길 이름');
  assert.equal(pendingReads, 1);
  assert.deepEqual(actions.map(item => item.action), ['signup']);
});

test('a temporary rejoin conflict preserves the nickname on the next attempt', async () => {
  pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '기존 이름' };
  await open('reactivation-required');
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  await page.getByLabel('닉네임', { exact: true }).fill('실패해도 유지할 새 이름');
  submitFailure = { status: 409, code: 'RESOURCE_BUSY' };
  await page.getByRole('button', { name: '새로 만들기', exact: true }).click();
  await choice().waitFor();
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '실패해도 유지할 새 이름');
});

test('edits made while rejoin is being checked survive returning to the choice', async () => {
  pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '기존 이름' };
  await open('reactivation-required');
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  await page.getByLabel('닉네임', { exact: true }).fill('처음 작성한 이름');
  let release;
  pendingGate = new Promise(resolve => { release = resolve; });
  submitFailure = { status: 409, code: 'RESOURCE_BUSY' };
  try {
    await page.getByRole('button', { name: '새로 만들기', exact: true }).click();
    await page.getByRole('button', { name: '처리 중…', exact: true }).waitFor();
    await page.getByLabel('닉네임', { exact: true }).fill('기다리며 고친 이름');
  } finally {
    release();
  }
  await choice().waitFor();
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '기다리며 고친 이름');
  submitFailure = null;
  await page.getByRole('button', { name: '새로 만들기', exact: true }).click();
  await page.waitForURL(server.resolvedUrls.local[0]);
  assert.deepEqual(actions.at(-1), { action: 'rejoin', body: { nickname: '기다리며 고친 이름' } });
});

test('returning to the choice manually also preserves an empty or edited rejoin draft', async () => {
  pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '기존 이름' };
  await open('reactivation-required');
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  await page.getByLabel('닉네임', { exact: true }).fill('돌아와도 남을 이름');
  await page.getByRole('button', { name: '뒤로', exact: true }).click();
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '돌아와도 남을 이름');
  await page.getByLabel('닉네임', { exact: true }).fill('');
  await page.getByRole('button', { name: '뒤로', exact: true }).click();
  await page.getByRole('button', { name: /새로 만들기/ }).click();
  assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '');
  assert.deepEqual(actions, []);
});

for (const failure of ['server', 'network', 'csrf']) {
  test(`an initial ${failure} failure offers a pending read retry without requiring login`, async () => {
    if (failure === 'csrf') csrfStatus = 503;
    else pendingStatus = failure === 'server' ? 503 : 0;
    await open();
    await page.getByRole('heading', { name: '가입 정보를 확인하지 못했습니다' }).waitFor();
    assert.equal(await page.getByRole('button', { name: '다시 로그인', exact: true }).count(), 0);
    pendingStatus = 200;
    csrfStatus = 200;
    await page.getByRole('button', { name: '다시 확인', exact: true }).click();
    await page.getByRole('heading', { name: '닉네임을 정해 주세요' }).waitFor();
    assert.equal(await page.getByLabel('닉네임', { exact: true }).inputValue(), '제공자 이름');
    assert.deepEqual(actions, []);
  });
}

for (const form of ['signup', 'rejoin']) {
  for (const failure of ['server', 'network']) {
    test(`a ${failure} pending failure after ${form} conflict keeps the form and retries only the read`, async () => {
      if (form === 'rejoin') pending = { loginState: 'REACTIVATION_REQUIRED', suggestedNickname: '기존 이름' };
      await open(form === 'rejoin' ? 'reactivation-required' : 'signup-required');
      if (form === 'rejoin') await page.getByRole('button', { name: /새로 만들기/ }).click();
      const title = form === 'rejoin' ? '새 계정으로 시작합니다' : '닉네임을 정해 주세요';
      const submitLabel = form === 'rejoin' ? '새로 만들기' : '시작하기';
      const input = page.getByLabel('닉네임', { exact: true });
      await input.fill('그대로 남아야 할 이름');
      submitFailure = { status: 409, code: 'RESOURCE_BUSY' };
      pendingStatus = failure === 'server' ? 503 : 0;
      await page.getByRole('button', { name: submitLabel, exact: true }).click();
      await page.getByRole('button', { name: '다시 확인', exact: true }).waitFor();
      await page.getByRole('heading', { name: title }).waitFor();
      assert.equal(await input.inputValue(), '그대로 남아야 할 이름');
      assert.equal(await page.getByRole('button', { name: '다시 로그인', exact: true }).count(), 0);
      assert.equal(await page.getByRole('button', { name: submitLabel, exact: true }).isDisabled(), true);
      await input.fill('실패 뒤에도 고칠 수 있는 이름');
      await input.press('Enter');
      assert.deepEqual(actions.map(item => item.action), [form]);
      await page.getByRole('button', { name: '다시 확인', exact: true }).click();
      await page.getByRole('button', { name: '다시 확인', exact: true }).waitFor();
      assert.equal(await input.inputValue(), '실패 뒤에도 고칠 수 있는 이름');
      assert.deepEqual(actions.map(item => item.action), [form]);
      pendingStatus = 200;
      submitFailure = null;
      await page.getByRole('button', { name: '다시 확인', exact: true }).click();
      if (form === 'rejoin') {
        await choice().waitFor();
        await page.getByRole('button', { name: /새로 만들기/ }).click();
      }
      await page.getByRole('heading', { name: title }).waitFor();
      assert.equal(await input.inputValue(), '실패 뒤에도 고칠 수 있는 이름');
      assert.deepEqual(actions.map(item => item.action), [form]);
      await page.getByRole('button', { name: submitLabel, exact: true }).click();
      await page.waitForURL(server.resolvedUrls.local[0]);
      assert.deepEqual(actions.at(-1), { action: form, body: { nickname: '실패 뒤에도 고칠 수 있는 이름' } });
    });
  }
}

test('a genuinely expired session during a read retry asks for a new login', async () => {
  pendingStatus = 503;
  await open();
  await page.getByRole('button', { name: '다시 확인', exact: true }).waitFor();
  pendingStatus = 401;
  await page.getByRole('button', { name: '다시 확인', exact: true }).click();
  await page.getByRole('heading', { name: '로그인 정보가 만료되었습니다' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '다시 확인', exact: true }).count(), 0);
  assert.equal(await page.getByRole('button', { name: '다시 로그인', exact: true }).count(), 1);
  assert.deepEqual(actions, []);
});
