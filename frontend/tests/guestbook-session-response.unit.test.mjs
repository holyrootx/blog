import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { computed, nextTick, reactive, ref, watch } from 'vue';

const accountA = { id: 7, nickname: 'A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: 'B', role: 'ROLE_USER' };
const entry = id => ({ id, content: `테스트 본문 ${id}`, secret: true, readable: true, mine: true });
const pageOf = items => ({ items, page: 1, totalElements: items.length, totalPages: 1, maxLength: 500 });
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const settle = async () => { await Promise.resolve(); await nextTick(); };

const actions = [
	{
		name: 'create', api: 'createGuestbookEntry', pending: 'submitting', error: 'formError',
		start(p, item) { p.draft.value = `새 초안 ${item.id}`; p.secret.value = true; return p.submit(); },
		check(p, item) { assert.equal(p.draft.value, `새 초안 ${item.id}`); assert.equal(p.secret.value, true); },
	},
	{
		name: 'edit', api: 'updateGuestbookEntry', pending: 'editPending', error: 'editError',
		start(p, item) { p.startEdit(item); p.editDraft.value = `편집 ${item.id}`; return p.saveEdit(item); },
		check(p, item) { assert.equal(p.editingId.value, item.id); assert.equal(p.editDraft.value, `편집 ${item.id}`); },
	},
	{
		name: 'delete', api: 'deleteGuestbookEntry', pending: 'deletePending', error: 'actionError',
		start(p, item) { p.askDelete(item); return p.confirmDelete(item); },
		check(p, item) { assert.equal(p.deletingId.value, item.id); },
	},
	{
		name: 'report', api: 'reportGuestbookEntry', pending: 'reportPending', error: 'reportError',
		start(p, item) { p.openReport(item); return p.submitReport({ reason: 'SPAM', detail: '' }); },
		check(p, item) { assert.equal(p.reportTarget.value.id, item.id); assert.equal(p.reportDone.value, ''); },
	},
	{
		name: 'reply', api: 'saveGuestbookReply', pending: 'replyPending', error: 'replyError',
		start(p, item) { p.openReply(item); p.replyDraft.value = `답글 ${item.id}`; return p.saveReply(item); },
		check(p, item) { assert.equal(p.replyTargetId.value, item.id); assert.equal(p.replyDraft.value, `답글 ${item.id}`); },
	},
	{
		name: 'delete reply', api: 'deleteGuestbookReply', pending: 'replyPending', error: 'actionError',
		start(p, item) { p.deletingReplyId.value = item.id; return p.confirmDeleteReply(item); },
		check(p, item) { assert.equal(p.deletingReplyId.value, item.id); },
	},
	{
		name: 'logout', api: 'signOutMember', pending: 'signingOut',
		start(p, item) { p.draft.value = `새 초안 ${item.id}`; return p.signOut(); },
		check(p, item) { assert.equal(p.draft.value, `새 초안 ${item.id}`); },
	},
];

for (const action of actions) {
	for (const failOld of [false, true]) {
		test(`late ${action.name} ${failOld ? 'failure' : 'success'} cannot alter a replacement session action`, async () => {
			const old = deferred(), current = deferred();
			let calls = 0;
			const p = await page({ [action.api]: () => (++calls === 1 ? old.promise : current.promise) });
			await p.mount();
			const oldRequest = action.start(p, entry(1));
			p.member.value = accountB;
			await settle();
			const currentEntry = entry(2);
			const currentRequest = action.start(p, currentEntry);
			assert.equal(calls, 2);
			failOld ? old.reject(new Error('old request failed')) : old.resolve();
			await oldRequest;
			assert.equal(p[action.pending].value, true);
			if (action.error) {
				assert.equal(p[action.error].value, '');
			}
			action.check(p, currentEntry);
			assert.equal(p.notices.length, 0);
			current.resolve();
			await currentRequest;
			assert.equal(p[action.pending].value, false);
		});
	}
	for (const scenario of ['A to B to A', 'unmount']) {
		for (const failOld of [false, true]) {
			test(`${action.name} ignores its old ${failOld ? 'failure' : 'success'} after ${scenario}`, async () => {
				const response = deferred();
				const p = await page({ [action.api]: () => response.promise });
				await p.mount();
				const request = action.start(p, entry(1));
				if (scenario === 'unmount') {
					p.unmount();
				} else {
					p.member.value = accountB;
					p.member.value = accountA;
					await settle();
				}
				// 이전 완료가 현재 화면의 상태를 바꾸지 않는지 입력과 진행 상태를 그대로 비교한다.
				const snapshot = JSON.stringify(p.snapshot());
				failOld ? response.reject(new Error('old request failed')) : response.resolve();
				await request;
				assert.equal(JSON.stringify(p.snapshot()), snapshot);
				assert.equal(p.notices.length, 0);
			});
		}
	}
}

test('an initial list request is replaced on session change even when abort is ignored', async () => {
	const old = deferred(), current = deferred(), started = deferred();
	let calls = 0;
	const p = await page({ getGuestbook: () => { started.resolve(); return ++calls === 1 ? old.promise : current.promise; } });
	const initial = p.mount();
	await started.promise;
	p.member.value = accountB;
	await settle();
	assert.equal(calls, 2);
	old.resolve(pageOf([entry(1)]));
	await initial;
	assert.equal(p.entries.value.length, 0);
	assert.equal(p.loading.value, true);
	current.resolve(pageOf([entry(2)]));
	await settle();
	assert.equal(p.entries.value[0].id, 2);
});

test('A to B to A invalidates every older response even when the final member ID matches', async () => {
	const responses = [deferred(), deferred(), deferred()];
	let calls = 0;
	const p = await page({ getGuestbook: () => responses[calls++].promise });
	const initial = p.mount();
	await settle();
	p.member.value = accountB;
	p.member.value = accountA;
	assert.equal(calls, 3);
	responses[2].resolve(pageOf([entry(3)]));
	await settle();
	responses[0].resolve(pageOf([entry(1)]));
	responses[1].reject(new Error('older B response'));
	await initial;
	await settle();
	assert.equal(p.entries.value[0].id, 3);
	assert.equal(p.loadError.value, '');
	assert.equal(p.loading.value, false);
});

test('a changed session clears loaded secrets and all drafts before its refresh completes or fails', async () => {
	const refresh = deferred();
	let calls = 0;
	const p = await page({ getGuestbook: () => ++calls === 1 ? Promise.resolve(pageOf([entry(1)])) : refresh.promise });
	await p.mount();
	p.draft.value = 'private draft'; p.secret.value = true;
	p.startEdit(entry(1)); p.openReply(entry(1)); p.openReport(entry(1));
	p.member.value = accountB;
	assert.equal(p.entries.value.length, 0);
	assert.equal(p.loaded.value, false);
	assert.equal(p.totalElements.value, 0);
	for (const name of ['draft', 'editDraft', 'replyDraft', 'formError', 'editError', 'replyError']) {
		assert.equal(p[name].value, '');
	}
	assert.equal(p.secret.value, false);
	assert.equal(p.reportTarget.value, null);
	refresh.reject(new Error('refresh unavailable'));
	await settle();
	assert.equal(p.entries.value.length, 0);
	assert.notEqual(p.loadError.value, '');
});

test('unmount before session resolution prevents starting a list request', async () => {
	const session = deferred();
	let calls = 0;
	const p = await page({ ensureMemberSession: () => session.promise, getGuestbook: async () => { calls += 1; return pageOf([]); } });
	const mounted = p.mount();
	p.unmount();
	session.resolve();
	await mounted;
	assert.equal(calls, 0);
});

test('unmount drops list responses even if the transport ignores abort', async () => {
	const response = deferred();
	const p = await page({ getGuestbook: () => response.promise });
	const mounted = p.mount();
	await settle();
	p.unmount();
	response.resolve(pageOf([entry(1)]));
	await mounted;
	assert.equal(p.entries.value.length, 0);
	assert.equal(p.loaded.value, false);
});

test('a normal same-account refresh failure preserves readable entries and drafts', async () => {
	let calls = 0;
	const p = await page({ getGuestbook: async () => { if (++calls > 1) { throw new Error('offline'); } return pageOf([entry(1)]); } });
	await p.mount();
	p.draft.value = 'keep draft';
	await p.load();
	assert.equal(p.entries.value[0].id, 1);
	assert.equal(p.draft.value, 'keep draft');
	assert.equal(p.loaded.value, true);
	assert.notEqual(p.loadError.value, '');
});

async function page(api = {}) {
	const member = ref(accountA), mounts = [], unmounts = [], notices = [];
	const injected = {
		computed, ref, watch, AbortController,
		useRoute: () => reactive({ query: {}, fullPath: '/guestbook' }),
		useRouter: () => ({ replace: async () => {}, push: async () => {} }),
		useMemberAuth: () => ({ member, isSignedIn: computed(() => member.value !== null), sessionResolved: ref(true) }),
		onMounted: callback => mounts.push(callback), onBeforeUnmount: callback => unmounts.push(callback),
		GUESTBOOK_MAX_LENGTH: 500, getGuestbook: async () => pageOf([entry(1)]), ensureMemberSession: async () => {},
		notifySuccess: message => notices.push(message), notifyError: message => notices.push(message),
		formatSeoulDateTime: value => value, ...api,
	};
	const source = (await readFile(new URL('../src/features/guestbook/pages/GuestbookPage.vue', import.meta.url), 'utf8'))
		.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '');
	const names = ['load', 'entries', 'loaded', 'loading', 'loadError', 'totalElements', 'draft', 'secret', 'submitting', 'formError',
		'submit', 'signOut', 'signingOut', 'startEdit', 'saveEdit', 'editingId', 'editDraft', 'editPending', 'editError',
		'askDelete', 'confirmDelete', 'deletingId', 'deletePending', 'actionError', 'openReport', 'submitReport', 'reportTarget',
		'reportPending', 'reportError', 'reportDone', 'openReply', 'saveReply', 'replyTargetId', 'replyDraft', 'replyPending', 'replyError',
		'confirmDeleteReply', 'deletingReplyId'];
	const state = vm.runInNewContext(`"use strict";\n${source}\n;({${names.join(',')}})`, injected);
	return {
		...state, member, notices, mount: () => mounts[0](), unmount: () => unmounts.forEach(callback => callback()),
		snapshot: () => Object.fromEntries(Object.entries(state).filter(([, value]) => typeof value !== 'function')
			.map(([name, value]) => [name, value.value])),
	};
}
