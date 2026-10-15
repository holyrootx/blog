import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { ref } from 'vue';

const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const tick = () => new Promise(setImmediate);
const item = (id, unread = true) => ({ id, unread });

async function store(overrides = {}) {
  const api = {
    getNotifications: async () => [], getUnreadCount: async () => 0,
    markNotificationRead: async () => {}, markAllNotificationsRead: async () => {},
    ...overrides,
  };
  const source = (await readFile(new URL('../src/features/member/data/notificationStore.js', import.meta.url), 'utf8'))
    .replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '')
    .replace(/\bexport (?=(async )?function)/g, '');
  const functions = Object.fromEntries(Object.keys(api).map(key => [key, (...args) => api[key](...args)]));
  const result = vm.runInNewContext(`${source}\n({useNotifications, loadNotifications, refreshUnreadCount, readNotification, readAllNotifications, clearNotifications})`, { ref, ...functions });
  return { ...result, state: result.useNotifications(), api };
}

test('recent 30 notifications never replace the total of 47 unread notifications', async () => {
  const s = await store({ getUnreadCount: async () => 47, getNotifications: async () => Array.from({ length: 30 }, (_, i) => item(i)) });
  await s.refreshUnreadCount(); await s.loadNotifications();
  assert.equal(s.state.unreadCount.value, 47);
  assert.equal(s.state.notifications.value.length, 30);
});

test('unread notifications outside the latest page can still all be marked read', async () => {
  let count = 17;
  const s = await store({ getUnreadCount: async () => count, getNotifications: async () => [item(1, false)], markAllNotificationsRead: async () => { count = 0; } });
  await s.refreshUnreadCount(); await s.loadNotifications();
  assert.equal(s.state.unreadCount.value, 17);
  assert.equal(await s.readAllNotifications(), true); await tick();
  assert.equal(s.state.unreadCount.value, 0);
});

test('failed list refresh retains data and a failed count refresh retains the last count', async () => {
  const s = await store({ getUnreadCount: async () => 47, getNotifications: async () => [item(1)] });
  await s.refreshUnreadCount(); await s.loadNotifications();
  s.api.getNotifications = s.api.getUnreadCount = async () => { throw Error('offline'); };
  await s.loadNotifications(); await s.refreshUnreadCount();
  assert.equal(s.state.notifications.value[0].id, 1);
  assert.equal(s.state.unreadCount.value, 47);
  assert.match(s.state.loadError.value, /이전에/);
  assert.ok(s.state.countError.value);
  s.api.getNotifications = async () => [];
  await s.loadNotifications();
  assert.equal(s.state.loadError.value, ''); assert.equal(s.state.loaded.value, true);
  assert.equal(s.state.notifications.value.length, 0);
});

test('first failure remains distinct from a successful empty list', async () => {
  const s = await store({ getNotifications: async () => { throw Error('failed'); } });
  await s.loadNotifications();
  assert.equal(s.state.loaded.value, false); assert.ok(s.state.loadError.value);
  s.api.getNotifications = async () => [];
  await s.loadNotifications();
  assert.equal(s.state.loaded.value, true); assert.equal(s.state.loadError.value, '');
});

test('late success and late failure cannot replace the latest list or its loading state', async () => {
  const old = deferred(), next = deferred();
  const s = await store({ getNotifications: () => old.promise });
  const first = s.loadNotifications();
  s.api.getNotifications = () => next.promise;
  const second = s.loadNotifications();
  old.reject(Error('late')); await first;
  assert.equal(s.state.loading.value, true); assert.equal(s.state.loadError.value, '');
  next.resolve([item(2)]); await second;
  assert.equal(s.state.notifications.value[0].id, 2);
  const late = deferred(); s.api.getNotifications = () => late.promise;
  const third = s.loadNotifications();
  s.api.getNotifications = async () => [item(3)]; await s.loadNotifications();
  late.resolve([item(1)]); await third;
  assert.equal(s.state.notifications.value[0].id, 3);
});

test('an earlier count response cannot overwrite a newer count', async () => {
  const old = deferred(); const s = await store({ getUnreadCount: () => old.promise });
  const first = s.refreshUnreadCount(); s.api.getUnreadCount = async () => 8;
  await s.refreshUnreadCount(); old.resolve(47); await first;
  assert.equal(s.state.unreadCount.value, 8);
});

test('count and list responses started before reading cannot restore unread state', async () => {
  const count = deferred(), list = deferred(), read = deferred();
  let total = 47, unread = true;
  const s = await store({ getUnreadCount: async () => total, getNotifications: async () => [item(1, unread)], markNotificationRead: () => read.promise });
  await s.loadNotifications(); await s.refreshUnreadCount();
  s.api.getUnreadCount = () => count.promise; s.api.getNotifications = () => list.promise;
  const oldCount = s.refreshUnreadCount(), oldList = s.loadNotifications();
  const mark = s.readNotification(1);
  total = 46; unread = false;
  s.api.getUnreadCount = async () => total; s.api.getNotifications = async () => [item(1, unread)];
  read.resolve(); await mark; await tick();
  count.resolve(47); list.resolve([item(1)]); await Promise.all([oldCount, oldList]);
  assert.equal(s.state.unreadCount.value, 46); assert.equal(s.state.notifications.value[0].unread, false);
});

test('failed read preserves unread state and is reported while reconciliation still runs', async () => {
  const s = await store({ getNotifications: async () => [item(1)], getUnreadCount: async () => 47, markNotificationRead: async () => { throw Error('offline'); } });
  await s.loadNotifications(); await s.refreshUnreadCount();
  assert.equal(await s.readNotification(1), false); await tick();
  assert.equal(s.state.unreadCount.value, 47); assert.equal(s.state.notifications.value[0].unread, true);
  assert.ok(s.state.readError.value); assert.equal(s.state.reading.value, false);
});

test('duplicate clicks issue one read and overlapping reads reconcile after the last response', async () => {
  const a = deferred(), b = deferred(); let calls = 0, counts = 0;
  const s = await store({ markNotificationRead: id => { calls += 1; return id === 1 ? a.promise : b.promise; }, getUnreadCount: async () => { counts += 1; return 45; } });
  const first = s.readNotification(1), duplicate = s.readNotification(1), second = s.readNotification(2);
  await tick(); assert.equal(calls, 2);
  a.resolve(); await first; await duplicate; assert.equal(counts, 0); assert.equal(s.state.reading.value, true);
  b.resolve(); await second; await tick(); assert.equal(counts, 1); assert.equal(s.state.unreadCount.value, 45);
});

test('session changes discard list, count and mutation results from the previous member', async () => {
  const list = deferred(), count = deferred(), read = deferred();
  const s = await store({ getNotifications: () => list.promise, getUnreadCount: () => count.promise, markNotificationRead: () => read.promise });
  const requests = [s.loadNotifications(), s.refreshUnreadCount(), s.readNotification(1)];
  await tick(); s.clearNotifications();
  s.api.getNotifications = async () => [item(9)]; s.api.getUnreadCount = async () => 3;
  await s.loadNotifications(); await s.refreshUnreadCount();
  list.resolve([item(1)]); count.resolve(47); read.reject(Error('old failure'));
  await Promise.all(requests); await tick();
  assert.equal(s.state.unreadCount.value, 3); assert.equal(s.state.notifications.value[0].id, 9);
  assert.equal(s.state.readError.value, ''); assert.equal(s.state.loading.value, false);
});

test('a queued read does not start after the member session has changed', async () => {
  let calls = 0; const s = await store({ markNotificationRead: async () => { calls += 1; } });
  const reading = s.readNotification(1); s.clearNotifications(); await reading;
  assert.equal(calls, 0);
});
