import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';

const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};

async function store() {
	const calls = [];
	const source = (await readFile(new URL('../src/features/post/data/categoryStore.js', import.meta.url), 'utf8'))
		.replace(/^import[^\n]+\n/gm, '')
		.replace('export function loadCategories', 'function loadCategories');
	const loadCategories = vm.runInNewContext(`${source}\n;loadCategories`, {
		getCategories: () => {
			const call = deferred();
			calls.push(call);
			return call.promise;
		},
	});
	return { loadCategories, calls };
}

test('실패를 공유한 호출은 빈 결과로 끝나고 다음 명시적 호출에서 새 요청을 공유한다', async () => {
	const { loadCategories, calls } = await store();
	const first = loadCategories(), sharedFailure = loadCategories();
	assert.equal(first, sharedFailure);
	assert.equal(calls.length, 1);
	calls[0].reject(new Error('카테고리 서버 일시 오류'));
	assert.equal((await first).length, 0);
	assert.equal((await sharedFailure).length, 0);

	const retry = loadCategories(), sharedRetry = loadCategories();
	assert.notEqual(retry, first);
	assert.equal(retry, sharedRetry);
	assert.equal(calls.length, 2);
	const categories = [{ id: 1, name: '개발' }];
	calls[1].resolve(categories);
	assert.deepEqual(await retry, categories);
	assert.deepEqual(await sharedRetry, categories);
	assert.equal(loadCategories(), retry);
	assert.equal(calls.length, 2);
});

for (const categories of [[{ id: 1, name: '개발' }], []]) {
	test(`정상 ${categories.length ? '카테고리' : '빈 목록'} 응답은 이후 호출에도 캐시한다`, async () => {
		const { loadCategories, calls } = await store();
		const first = loadCategories(), concurrent = loadCategories();
		assert.equal(first, concurrent);
		assert.equal(calls.length, 1);
		calls[0].resolve(categories);
		assert.deepEqual(await first, categories);
		assert.equal(loadCategories(), first);
		assert.deepEqual(await loadCategories(), categories);
		assert.equal(calls.length, 1);
	});
}

test('연속 실패도 자동 재시도하지 않고 다음 명시적 호출까지 기다린다', async () => {
	const { loadCategories, calls } = await store();
	for (let index = 0; index < 2; index += 1) {
		const request = loadCategories();
		assert.equal(calls.length, index + 1);
		calls[index].reject(new Error('아직 복구되지 않음'));
		assert.equal((await request).length, 0);
		await new Promise(setImmediate);
		assert.equal(calls.length, index + 1);
	}
	const recovered = loadCategories();
	calls[2].resolve([]);
	assert.equal((await recovered).length, 0);
	assert.equal(calls.length, 3);
});

test('기존 비배열 성공 응답의 빈 목록 변환과 캐시는 유지한다', async () => {
	const { loadCategories, calls } = await store();
	const request = loadCategories();
	calls[0].resolve(null);
	assert.equal((await request).length, 0);
	assert.equal(loadCategories(), request);
	assert.equal(calls.length, 1);
});
