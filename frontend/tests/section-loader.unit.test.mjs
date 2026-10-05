import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { reactive } from 'vue';

const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

test('cached value shows first, fresh value replaces it, and the cache keeps the fresh one', async () => {
  const { useSectionLoader, cache } = await loader();
  cache.writeCachedResponse('post:1', { title: '전에 받은 제목' });
  const { loading, load } = useSectionLoader(['post']);
  const fresh = deferred(); const applied = [];

  const request = load('post', { cacheKey: 'post:1', request: () => fresh.promise, apply: value => applied.push(value.title) });
  assert.deepEqual(applied, ['전에 받은 제목']);
  assert.equal(loading.post, false);

  fresh.resolve({ title: '새 제목' }); await request;
  assert.deepEqual(applied, ['전에 받은 제목', '새 제목']);
  assert.equal(cache.readCachedResponse('post:1').title, '새 제목');
});

test('responses that arrive after restart are dropped and do not touch loading', async () => {
  const { useSectionLoader } = await loader();
  const { loading, load, restart } = useSectionLoader(['post']);
  const old = deferred(), next = deferred(); const applied = []; const errors = [];

  const oldRequest = load('post', { request: () => old.promise, apply: value => applied.push(value), onError: e => errors.push(e) });
  restart();
  const nextRequest = load('post', { request: () => next.promise, apply: value => applied.push(value) });
  old.reject(new Error('late')); await oldRequest;
  assert.equal(loading.post, true); assert.deepEqual(errors, []);

  next.resolve('new'); await nextRequest;
  assert.deepEqual(applied, ['new']); assert.equal(loading.post, false);
});

test('a failed refresh tells the screen whether a cached value is already showing', async () => {
  const { useSectionLoader, cache } = await loader();
  cache.writeCachedResponse('list', ['cached']);
  const { load } = useSectionLoader(['withCache', 'withoutCache']);
  const seen = [];

  await load('withCache', { cacheKey: 'list', request: async () => { throw new Error('offline'); }, apply() {}, onError: (e, info) => seen.push(info.showingCached) });
  await load('withoutCache', { cacheKey: 'other', request: async () => { throw new Error('offline'); }, apply() {}, onError: (e, info) => seen.push(info.showingCached) });
  assert.deepEqual(seen, [true, false]);
});

test('the cache forgets the least recently read entry beyond its limit', async () => {
  const { cache } = await loader();
  for (let index = 0; index < 50; index += 1) cache.writeCachedResponse(`key:${index}`, index);
  cache.readCachedResponse('key:0');
  cache.writeCachedResponse('key:50', 50);
  assert.equal(cache.readCachedResponse('key:0'), 0);
  assert.equal(cache.readCachedResponse('key:1'), undefined);
  assert.equal(cache.readCachedResponse('key:50'), 50);
});

// 실제 모듈을 그대로 돌린다. 화면 밖이라 onBeforeUnmount 만 빈 함수로 넣는다
async function loader() {
  const cache = await run('shared/data/responseCache.js', ['readCachedResponse', 'writeCachedResponse']);
  const { useSectionLoader } = await run('shared/composables/useSectionLoader.js', ['useSectionLoader'], cache);
  return { useSectionLoader, cache };
}

async function run(path, names, injected = {}) {
  const source = (await readFile(new URL(`../src/${path}`, import.meta.url), 'utf8'))
    .replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '')
    .replace(/\bexport (?=(async )?function|const|class)/g, '');
  const context = vm.createContext({ reactive, console, onBeforeUnmount() {}, ...injected });
  return vm.runInContext(`"use strict";\n${source}\n;({${names.join(',')}})`, context);
}
