import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { ref, computed, reactive, watch, nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

for (const reject of [false, true]) test(`late member session response cannot replace a new session after invalidation (failure=${reject})`, async () => {
  const pending = deferred(); let calls = 0;
  const m = await load('features/member/data/memberAuthStore.js', ['ensureMemberSession', 'clearMemberSession', 'useMemberAuth'], {
    getMemberSession: () => ++calls === 1 ? pending.promise : Promise.resolve({ id: 22 }),
    refreshCsrfToken: async () => {}, clearNotifications() {},
  });
  const oldRequest = m.ensureMemberSession();
  m.clearMemberSession();
  await m.ensureMemberSession();
  reject ? pending.reject(new Error('old failure')) : pending.resolve({ id: 11 });
  await oldRequest;
  assert.equal(m.useMemberAuth().member.value.id, 22);
});

test('late admin session response cannot restore a cleared session', async () => {
  const pending = deferred();
  const m = await load('features/admin/data/adminAuthStore.js', ['ensureAdminSession', 'clearAdminSession', 'useAdminAuth'], {
    getAdminSession: () => pending.promise, refreshCsrfToken: async () => {},
  });
  const request = m.ensureAdminSession();
  await Promise.resolve(); await Promise.resolve();
  m.clearAdminSession();
  pending.resolve({ username: 'old-admin' });
  assert.equal(await request, false);
  assert.equal(m.useAdminAuth().admin.value, null);
});

test('late notification responses cannot restore a previous member information', async () => {
  const count = deferred(), items = deferred();
  const n = await load('features/member/data/notificationStore.js', ['refreshUnreadCount', 'loadNotifications', 'clearNotifications', 'useNotifications'], {
    getUnreadCount: () => count.promise, getNotifications: () => items.promise,
  });
  const reads = [n.refreshUnreadCount(), n.loadNotifications()];
  n.clearNotifications();
  count.resolve(7); items.resolve([{ id: 9, unread: true }]);
  await Promise.all(reads);
  assert.equal(n.useNotifications().unreadCount.value, 0);
  assert.equal(n.useNotifications().notifications.value.length, 0);
  assert.equal(n.useNotifications().loading.value, false);
});

for (const method of ['getApiData', 'sendApiData', 'sendApiFile']) test(`late unauthorized ${method} cannot clear a newer login`, async () => {
  const pending = deferred(); let expired = 0;
  const c = await load('shared/api/blogApiClient.js', [method, 'onUnauthorized', 'advanceAuthenticationGeneration'], {
    fetch: () => pending.promise,
  });
  c.onUnauthorized(() => expired++);
  const request = c[method]('/test', { method: 'POST' });
  c.advanceAuthenticationGeneration();
  pending.resolve({ ok: false, status: 401, json: async () => ({ success: false, code: 'UNAUTHORIZED' }) });
  await assert.rejects(request);
  assert.equal(expired, 0);
  await assert.rejects(c[method]('/test', { method: 'POST' }));
  assert.equal(expired, 1);
});

test('a stale mutation is not retried with a newer login after CSRF refresh', async () => {
  const refreshing = deferred(), started = deferred(); let calls = 0;
  const c = await load('shared/api/blogApiClient.js', ['sendApiData', 'setCsrfToken', 'onCsrfRejected', 'advanceAuthenticationGeneration'], {
    fetch: async () => { calls++; return { ok: false, status: 403, json: async () => ({ success: false, code: 'FORBIDDEN' }) }; },
  });
  c.setCsrfToken({ headerName: 'X-CSRF-TOKEN', token: 'old' });
  c.onCsrfRejected(() => { started.resolve(); return refreshing.promise; });
  const request = c.sendApiData('/write', { method: 'POST', body: {} });
  await started.promise;
  c.advanceAuthenticationGeneration(); refreshing.resolve();
  await assert.rejects(request);
  assert.equal(calls, 1);
});

test('late CSRF response does not replace the current login token', async () => {
  const pending = deferred(); let generation = 0, token = 'new';
  const c = await load('shared/api/csrfApi.js', ['refreshCsrfToken'], {
    getApiData: () => pending.promise, getAuthenticationGeneration: () => generation,
    setCsrfToken: value => { token = value; }, onCsrfRejected() {},
  });
  const request = c.refreshCsrfToken(); generation++;
  pending.resolve('old'); await request;
  assert.equal(token, 'new');
});

test('late nickname change cannot restore invalidated member state', async () => {
  const pending = deferred();
  const m = await load('features/member/data/memberAuthStore.js', ['changeNickname', 'clearMemberSession', 'useMemberAuth'], {
    changeMemberNickname: () => pending.promise, clearNotifications() {},
  });
  const request = m.changeNickname('old'); m.clearMemberSession();
  pending.resolve({ id: 11, nickname: 'old' }); await request;
  assert.equal(m.useMemberAuth().member.value, null);
});

for (const admin of [false, true]) test(`late logout verification cannot overwrite a newer session (admin=${admin})`, async () => {
  const pending = deferred(), started = deferred();
  const recheck = () => { started.resolve(); return pending.promise; };
  const path = admin ? 'features/admin/data/adminAuthStore.js' : 'features/member/data/memberAuthStore.js';
  const names = admin ? ['signOutAdmin', 'clearAdminSession', 'useAdminAuth'] : ['signOutMember', 'clearMemberSession', 'useMemberAuth'];
  const m = await load(path, names, {
    logoutAdmin: async () => { throw new Error('lost response'); }, logoutMember: async () => { throw new Error('lost response'); },
    getAdminSession: recheck, getMemberSession: recheck, clearNotifications() {}, refreshCsrfToken: async () => {},
  });
  const request = admin ? m.signOutAdmin() : m.signOutMember();
  await started.promise;
  admin ? m.clearAdminSession() : m.clearMemberSession();
  const current = admin ? m.useAdminAuth().admin : m.useMemberAuth().member;
  current.value = { id: 22 };
  pending.resolve({ id: 11 }); await assert.rejects(request);
  assert.equal(current.value.id, 22);
});
for (const operation of ['signInAdmin', 'completeSignUp', 'completeReactivation', 'completeRejoin']) {
  const admin = operation === 'signInAdmin';
  const path = admin ? 'features/admin/data/adminAuthStore.js' : 'features/member/data/memberAuthStore.js';
  const clear = admin ? 'clearAdminSession' : 'clearMemberSession';
  const useAuth = admin ? 'useAdminAuth' : 'useMemberAuth';

  test(`invalidated ${operation} does not send authentication after waiting for CSRF`, async () => {
    const csrf = deferred(); let authenticationCalls = 0;
    const authenticate = async () => { authenticationCalls++; return { id: 22 }; };
    const m = await load(path, [operation, clear], {
      refreshCsrfToken: () => csrf.promise, clearNotifications() {},
      loginAdmin: authenticate, signUpMember: authenticate, reactivateMember: authenticate, rejoinMember: authenticate,
    });
    const authentication = m[operation]('name', 'password');
    m[clear](); csrf.resolve();
    await assert.rejects(authentication);
    assert.equal(authenticationCalls, 0);
  });

  test(`request begun during ${operation} cannot expire the completed login`, async () => {
    const login = deferred(), started = deferred(), oldResponse = deferred(); let expired = 0;
    const c = await load('shared/api/blogApiClient.js', ['getApiData', 'onUnauthorized', 'advanceAuthenticationGeneration'], {
      fetch: () => oldResponse.promise,
    });
    const authenticate = () => { started.resolve(); return login.promise; };
    const m = await load(path, [operation, clear, useAuth], {
      advanceAuthenticationGeneration: c.advanceAuthenticationGeneration,
      refreshCsrfToken: async () => {}, refreshMemberSession: async () => {}, clearNotifications() {},
      loginAdmin: authenticate, signUpMember: authenticate, reactivateMember: authenticate, rejoinMember: authenticate,
    });
    c.onUnauthorized(() => { expired++; m[clear](); });
    const authentication = m[operation]('name', 'password');
    await started.promise;
    const oldRequest = c.getApiData('/protected');
    login.resolve({ id: 22 }); await authentication;
    oldResponse.resolve({ ok: false, status: 401, json: async () => ({ success: false, code: 'UNAUTHORIZED' }) });
    await assert.rejects(oldRequest);
    assert.equal(expired, 0);
    assert.equal((admin ? m[useAuth]().admin : m[useAuth]().member).value.id, 22);
  });
}

for (const result of ['success', 'lost-signedout', 'lost-signedin', 'lost-unavailable']) {
  test(`public member logout synchronizes the admin cache only when confirmed (${result})`, async () => {
    const m = await load('features/member/data/memberAuthStore.js', ['onMemberSessionCleared', 'signOutMember', 'useMemberAuth'], {
      logoutMember: async () => { if (result !== 'success') throw new Error('lost response'); },
      getMemberSession: async () => {
        if (result === 'lost-unavailable') throw new Error('offline');
        return result === 'lost-signedout' ? null : { id: 22 };
      },
      refreshCsrfToken: async () => {}, clearNotifications() {},
    });
    const a = await load('features/admin/data/adminAuthStore.js', ['ensureAdminSession', 'useAdminAuth'], {
      onMemberSessionCleared: m.onMemberSessionCleared,
      getAdminSession: async () => ({ id: 22 }), refreshCsrfToken: async () => {},
    });
    m.useMemberAuth().member.value = { id: 22 };
    await a.ensureAdminSession();
    const confirmed = ['success', 'lost-signedout'].includes(result);
    if (confirmed) await m.signOutMember(); else await assert.rejects(m.signOutMember());
    assert.equal(a.useAdminAuth().admin.value === null, confirmed);
    assert.equal(m.useMemberAuth().member.value === null, confirmed);
  });
}

// Execute the actual module/script with boundary APIs injected. Vue refs/watchers remain real.
async function load(path, names, injected = {}) {
  let source = await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8');
  if (path.endsWith('.vue')) source = source.match(/<script setup>([\s\S]*?)<\/script>/)[1];
  source = source.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '').replace(/\bexport (?=(async )?function|const|class)/g, '');
  const context = vm.createContext({ ref, computed, reactive, watch, nextTick, console,
    setTimeout, clearTimeout, onBeforeUnmount() {}, onMounted() {}, onBeforeRouteLeave() {},
    notifyError() {}, notifySuccess() {}, advanceAuthenticationGeneration() {}, onMemberSessionCleared() {}, ...injected });
  return vm.runInContext(`"use strict";\n${source}\n;({${names.join(',')}})`, context);
}

for (const reject of [false, true]) test(`comment response after post changes cannot touch new state (${reject ? 'failure' : 'success'})`, async () => {
  const pending = deferred();
  const { useCommentComposer } = await load('features/post/composables/useCommentComposer.js', ['useCommentComposer'], { createPostComment: () => pending.promise });
  const postId = ref(1), items = ref([]), total = ref(0);
  const composer = useCommentComposer({ postId, items, total, member: ref({ nickname: 'me' }), replyInput: ref(null) });
  composer.draft.value = 'A comment';
  const result = composer.submitComment();
  postId.value = 2; items.value = [{ id: 22 }]; total.value = 1; composer.draft.value = 'B draft';
  reject ? pending.reject(new Error('late error')) : pending.resolve({ id: 11 });
  await result;
  assert.equal(items.value[0].id, 22); assert.equal(total.value, 1); assert.equal(composer.draft.value, 'B draft');
  assert.equal(composer.formError.value, ''); assert.equal(composer.submitting.value, false);
});

test('reply response preserves a different reply draft and ignores changed post', async () => {
  let pending = deferred();
  const { useCommentComposer } = await load('features/post/composables/useCommentComposer.js', ['useCommentComposer'], { createPostComment: () => pending.promise });
  const postId = ref(1), items = ref([{ id: 1, replies: [] }, { id: 2, replies: [] }]), total = ref(2);
  const c = useCommentComposer({ postId, items, total, member: ref({}), replyInput: ref(null) });
  c.toggleReply(1); c.replyDraft.value = 'reply A'; const request = c.submitReply(1);
  c.toggleReply(2); c.replyDraft.value = 'reply B'; pending.resolve({ id: 3 }); await request;
  assert.equal(items.value[0].replies[0].id, 3); assert.equal(c.replyDraft.value, 'reply B'); assert.equal(c.replyTargetId.value, 2);
  pending = deferred(); const next = c.submitReply(2); postId.value = 3; items.value = []; total.value = 0;
  pending.resolve({ id: 4 }); await next; assert.equal(total.value, 0); assert.equal(items.value.length, 0);
});

for (const reject of [false, true]) test(`edit response preserves another edit session (${reject ? 'failure' : 'success'})`, async () => {
  const pending = deferred();
  const { useCommentManagement } = await load('features/post/composables/useCommentManagement.js', ['useCommentManagement'], { updatePostComment: () => pending.promise });
  const a = { id: 1, content: 'A' }, b = { id: 2, content: 'B' };
  const m = useCommentManagement({ postId: ref(1), items: ref([a, b]), total: ref(2) });
  m.startEdit(a); m.editDraft.value = 'A saved'; const request = m.saveEdit(a);
  m.cancelEdit(); m.startEdit(b); m.editDraft.value = 'B unsaved';
  reject ? pending.reject(new Error('late error')) : pending.resolve(); await request;
  assert.equal(m.editingId.value, 2); assert.equal(m.editDraft.value, 'B unsaved'); assert.equal(m.editError.value, '');
});

test('deletion response after navigation does not change next post count', async () => {
  const pending = deferred();
  const { useCommentManagement } = await load('features/post/composables/useCommentManagement.js', ['useCommentManagement'], { deletePostComment: () => pending.promise });
  const postId = ref(1), items = ref([{ id: 1, replies: [] }]), total = ref(1);
  const m = useCommentManagement({ postId, items, total }); const request = m.confirmDelete(items.value[0]);
  postId.value = 2; items.value = [{ id: 2, replies: [] }]; total.value = 1; pending.resolve(); await request;
  assert.equal(items.value[0].id, 2); assert.equal(total.value, 1);
});

for (const reopen of [false, true]) test(`report targets captured comment after close (reopen=${reopen})`, async () => {
  const pending = deferred(); let apiId;
  const { useCommentFeedback } = await load('features/post/composables/useCommentFeedback.js', ['useCommentFeedback'], { reportComment: id => { apiId = id; return pending.promise; } });
  const f = useCommentFeedback({ postId: ref(1), isSignedIn: ref(true), goToLogin() {} });
  const a = reactive({ id: 1 }), b = reactive({ id: 2 }); f.openReport(a); const request = f.submitReport({ reason: 'SPAM', detail: '' });
  await f.submitReport({ reason: 'SPAM', detail: '' });
  f.closeReport(); if (reopen) f.openReport(b); pending.resolve(); await request;
  assert.equal(apiId, 1); assert.equal(a.reportedByMe, true); assert.equal(b.reportedByMe, undefined);
  assert.equal(f.reportTarget.value?.id ?? null, reopen ? 2 : null); assert.equal(f.reportError.value, '');
});

test('stale post list successes, failures and fallback do not replace latest result or loading', async () => {
  const first = deferred(), second = deferred(), fallback = deferred(); let call = 0;
  const route = reactive({ query: { category: '1' }, fullPath: '/posts?category=1' });
  const p = await load('features/post/pages/PostListPage.vue', ['load', 'posts', 'loading', 'failed', 'fallbackPosts'], {
    useRoute: () => route, useRouter: () => ({}), watch() {}, getPosts: () => (++call === 1 ? first.promise : second.promise),
    getHomePosts: () => fallback.promise, loadCategories: async () => [], useSectionLoader: await sectionLoader(),
  });
  const a = p.load(); route.query.category = '2'; const b = p.load();
  first.reject(new Error('stale')); await a; assert.equal(p.loading.value, true); assert.equal(p.failed.value, false);
  second.resolve({ items: [{ id: 2 }], totalElements: 1, totalPages: 1 }); await b;
  assert.equal(p.posts.value[0].id, 2); assert.equal(p.loading.value, false);
});

test('late empty-search fallback cannot overwrite a newer category result', async () => {
  const fallback = deferred(); let call = 0;
  const route = reactive({ query: { q: 'search' }, fullPath: '/posts?q=search' });
  const p = await load('features/post/pages/PostListPage.vue', ['load', 'posts', 'fallbackPosts'], {
    useRoute: () => route, useRouter: () => ({}), watch() {}, getPosts: async () => ({ items: ++call === 1 ? [] : [{ id: 2 }], totalElements: 1, totalPages: 1 }),
    getHomePosts: () => fallback.promise, loadCategories: async () => [], useSectionLoader: await sectionLoader(),
  });
  const a = p.load(); await Promise.resolve(); route.query = { category: '2' }; await p.load();
  fallback.resolve([{ id: 99 }]); await a; assert.equal(p.posts.value[0].id, 2); assert.equal(p.fallbackPosts.value.length, 0);
});

/** 화면이 쓰는 실제 영역 로더. 늦은 응답을 버리는 일은 이 로더가 하므로 가짜로 바꾸지 않는다 */
async function sectionLoader() {
  const cache = await load('shared/data/responseCache.js', ['readCachedResponse', 'writeCachedResponse']);
  const { useSectionLoader } = await load('shared/composables/useSectionLoader.js', ['useSectionLoader'], cache);
  return useSectionLoader;
}

async function editor(api = {}) {
  const backups = new Map(), route = reactive({ params: {} });
  const draftStore = {
    savePostDraft: (key, form) => backups.set(key, { ...form }), clearPostDraft: key => backups.delete(key),
    loadPostDraft: key => (backups.has(key) ? { form: backups.get(key) } : null),
  };
  // 편집 화면에서 떼어 낸 조각도 실제 코드를 그대로 쓴다. 저장소 경계만 같은 가짜로 잇는다
  const rules = await load('features/admin/composables/usePostEditRules.js', ['usePostEditRules', 'TITLE_MAX', 'EXCERPT_MAX']);
  const backup = await load('features/admin/composables/usePostDraftBackup.js', ['usePostDraftBackup'], { watch() {}, ...draftStore });
  const dates = await load('features/admin/data/postEditorDates.js', ['formatEditorDateTime', 'toLocalInputValue']);
  const p = await load('features/admin/pages/AdminPostEditPage.vue', ['form', 'save', 'runConfirmedAction', 'confirmAction', 'lastSavedForm', 'saveDraftNow', 'onBeforeUnload', 'status', 'canSave', 'cancelSchedule'], {
    watch() {}, useRoute: () => route, useRouter: () => ({ replace: async () => {} }),
    firstImageUrlOf: () => '', getFallbackPostImageUrl: () => '',
    ...draftStore, ...rules, ...backup, ...dates,
    getAdminPost: async () => ({ status: 'DRAFT', updatedAt: 'now', publishedAt: null }),
    ...api,
  });
  Object.assign(p.form, { title: 'Title', categoryId: '1', content: 'Body', excerpt: 'Summary' });
  return { ...p, backups, route };
}

for (const action of ['save', 'publish']) test(`failed new ${action} keeps last input through unload and retry`, async () => {
  let fail = true;
  const e = await editor({ createAdminPost: async () => { if (fail) throw new Error('NETWORK_FAILURE'); return 42; }, publishAdminPost: async () => {} });
  e.lastSavedForm.value = JSON.stringify({ ...e.form, content: 'old' });
  if (action === 'save') await e.save(); else { e.confirmAction.value = 'publish'; await e.runConfirmedAction(); }
  e.onBeforeUnload(); assert.equal(e.backups.get('new').content, 'Body'); assert.notEqual(e.lastSavedForm.value, JSON.stringify({ ...e.form }));
  fail = false; await e.save(); assert.equal(e.backups.has('new'), false);
});

test('scheduled save validates content, while cancellation preserves unsaved invalid draft', async () => {
  let cancelled = 0;
  const e = await editor({ unpublishAdminPost: async () => { cancelled += 1; } });
  e.route.params.postId = '42'; e.status.value = 'SCHEDULED'; e.form.content = '';
  assert.equal(e.canSave.value, false); await e.cancelSchedule();
  assert.equal(cancelled, 1); assert.equal(e.status.value, 'DRAFT'); assert.equal(e.form.content, ''); assert.equal(e.backups.get('42').content, '');
});

for (const status of [403, 500, undefined]) test(`member logout failure preserves session when recheck remains signed in (${status})`, async () => {
  const error = Object.assign(new Error('logout failed'), { status });
  const m = await load('features/member/data/memberAuthStore.js', ['signOutMember', 'useMemberAuth', 'withdraw'], {
    logoutMember: async () => { throw error; }, getMemberSession: async () => ({ id: 7 }),
    withdrawMember: async () => { throw error; }, refreshCsrfToken: async () => {}, clearNotifications() {},
  });
  m.useMemberAuth().member.value = { id: 7 };
  await assert.rejects(m.signOutMember); assert.equal(m.useMemberAuth().isSignedIn.value, true);
  await assert.rejects(m.withdraw); assert.equal(m.useMemberAuth().isSignedIn.value, true);
});

for (const check of ['signedout', 'unavailable', 'signedin']) test(`member logout lost response uses confirmed session (${check})`, async () => {
  const m = await load('features/member/data/memberAuthStore.js', ['signOutMember', 'useMemberAuth'], {
    logoutMember: async () => { throw new Error('lost response'); },
    getMemberSession: async () => { if (check === 'unavailable') throw new Error('offline'); return check === 'signedout' ? null : { id: 7 }; },
    refreshCsrfToken: async () => {}, clearNotifications() {},
  });
  m.useMemberAuth().member.value = { id: 7 };
  if (check === 'signedout') await m.signOutMember(); else await assert.rejects(m.signOutMember);
  assert.equal(m.useMemberAuth().isSignedIn.value, check !== 'signedout');
});

for (const check of [401, 403, 500, 'signedin']) test(`admin logout requires confirmed 401 on failed request (${check})`, async () => {
  let memberCleared = false;
  const m = await load('features/admin/data/adminAuthStore.js', ['signOutAdmin', 'useAdminAuth'], {
    logoutAdmin: async () => { throw new Error('failed'); },
    getAdminSession: async () => { if (check !== 'signedin') throw Object.assign(new Error('check'), { status: check }); return { username: 'admin' }; },
    clearMemberSession: () => { memberCleared = true; }, refreshCsrfToken: async () => {},
  });
  m.useAdminAuth().admin.value = { username: 'admin' };
  if (check === 401) await m.signOutAdmin(); else await assert.rejects(m.signOutAdmin);
  assert.equal(m.useAdminAuth().admin.value === null, check === 401); assert.equal(memberCleared, check === 401);
});

for (const fail of [false, true]) test(`save preserves input changed while request was pending (fail=${fail})`, async () => {
  const pending = deferred();
  const e = await editor({ createAdminPost: () => pending.promise });
  const request = e.save(); e.form.content = 'Typed during request';
  fail ? pending.reject(new Error('offline')) : pending.resolve(42); await request;
  assert.equal(e.form.content, 'Typed during request');
  assert.equal(e.backups.get(fail ? 'new' : '42').content, 'Typed during request');
});

for (const action of ['save', 'publish']) test(`new ${action} transfers the draft without recreating the new-post backup on route leave`, async () => {
  const pending = deferred();
  const component = {};
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/posts/new', name: 'admin-post-new', component },
    { path: '/posts/:postId', name: 'admin-post-edit', component },
  ] });
  await router.push({ name: 'admin-post-new' });
  const route = reactive({ get params() { return router.currentRoute.value.params; } });
  let leftNewPost = false;
  const e = await editor({
    watch, useRoute: () => route, useRouter: () => router,
    onBeforeRouteLeave(guard) {
      router.currentRoute.value.matched[0].leaveGuards.add(() => {
        leftNewPost = router.currentRoute.value.name === 'admin-post-new';
        return guard();
      });
    },
    createAdminPost: () => pending.promise, publishAdminPost: async () => {},
  });
  e.confirmAction.value = action === 'publish' ? 'publish' : '';
  const request = action === 'publish' ? e.runConfirmedAction() : e.save();
  e.form.content = 'Typed during request';
  pending.resolve(42);
  await request;
  await nextTick();
  e.onBeforeUnload();
  assert.equal(leftNewPost, true);
  assert.equal(router.currentRoute.value.name, 'admin-post-edit');
  assert.equal(route.params.postId, '42');
  assert.equal(e.form.content, 'Typed during request');
  assert.equal(e.backups.get('42').content, 'Typed during request');
  assert.equal(e.backups.has('new'), false);
});

test('publish status refresh preserves input changed after content save', async () => {
  const pending = deferred();
  const e = await editor({ updateAdminPost: async () => {}, publishAdminPost: () => pending.promise });
  e.route.params.postId = '42'; e.confirmAction.value = 'publish';
  const request = e.runConfirmedAction(); await Promise.resolve(); await Promise.resolve();
  e.form.content = 'Unsaved after request'; pending.resolve(); await request; e.saveDraftNow();
  assert.equal(e.form.content, 'Unsaved after request'); assert.equal(e.backups.get('42').content, 'Unsaved after request');
});

test('reply pagination deduplicates local replies and discards response after post change', async () => {
  let pending = deferred();
  const { useCommentFeed } = await load('features/post/composables/useCommentFeed.js', ['useCommentFeed'], { getCommentReplies: () => pending.promise });
  const postId = ref(1), comments = ref({ total: 3, items: [{ id: 1, replies: [{ id: 2 }, { id: 5 }], replyHasNext: true, replyNextCursor: 2 }] });
  const f = useCommentFeed({ postId, comments });
  const request = f.loadReplies(f.items.value[0]); pending.resolve({ items: [{ id: 3 }, { id: 5 }], hasNext: false, nextCursor: null }); await request;
  assert.equal(f.items.value[0].replies.map(x => x.id).join(','), '2,3,5'); assert.equal(f.items.value[0].replyHasNext, false);
  f.items.value[0].replyHasNext = true; pending = deferred(); const stale = f.loadReplies(f.items.value[0]);
  postId.value = 2; comments.value = { total: 1, items: [{ id: 9, replies: [] }] }; await nextTick();
  pending.resolve({ items: [{ id: 10 }], hasNext: false }); await stale; assert.equal(f.items.value[0].id, 9); assert.equal(f.items.value[0].replies.length, 0);
});

test('older successful post list response cannot replace the latest category', async () => {
  const first = deferred(), second = deferred(); let call = 0;
  const route = reactive({ query: { category: '1' }, fullPath: '/posts?category=1' });
  const p = await load('features/post/pages/PostListPage.vue', ['load', 'posts', 'loading'], {
    useRoute: () => route, useRouter: () => ({}), watch() {}, getPosts: () => (++call === 1 ? first.promise : second.promise),
    getHomePosts: async () => [], loadCategories: async () => [], useSectionLoader: await sectionLoader(),
  });
  const a = p.load(); route.query.category = '2'; const b = p.load();
  second.resolve({ items: [{ categoryId: 2 }], totalElements: 1, totalPages: 1 }); await b;
  first.resolve({ items: [{ categoryId: 1 }], totalElements: 1, totalPages: 1 }); await a;
  assert.equal(p.posts.value[0].categoryId, 2); assert.equal(p.loading.value, false);
});

test('normal logout clears member and admin sessions without a verification request', async () => {
  const m = await load('features/member/data/memberAuthStore.js', ['signOutMember', 'useMemberAuth'], {
    logoutMember: async () => {}, getMemberSession: () => { throw new Error('unnecessary verification'); }, refreshCsrfToken: async () => {}, clearNotifications() {},
  });
  m.useMemberAuth().member.value = { id: 7 }; await m.signOutMember(); assert.equal(m.useMemberAuth().isSignedIn.value, false);
  const a = await load('features/admin/data/adminAuthStore.js', ['signOutAdmin', 'useAdminAuth'], {
    logoutAdmin: async () => {}, getAdminSession: () => { throw new Error('unnecessary verification'); }, refreshCsrfToken: async () => {}, clearMemberSession() {},
  });
  a.useAdminAuth().admin.value = { username: 'admin' }; await a.signOutAdmin(); assert.equal(a.useAdminAuth().admin.value, null);
});
