import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { effectScope, nextTick, reactive, ref, watch } from 'vue';

const accountA = { id: 7, nickname: '회원 A' };
const accountB = { id: 8, nickname: '회원 B' };
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const rows = () => [{ id: 101, likedByMe: true, dislikedByMe: false,
	replies: [{ id: 104, likedByMe: false, dislikedByMe: true }] }];

async function controller(t) {
	const props = reactive({ postId: 1 }), member = ref({ ...accountA });
	const draft = ref('댓글 초안'), replyDraft = ref('답글 초안'), replyTargetId = ref(101), items = ref(rows());
	const calls = [], cleanup = [], notices = new Map();
	let generation = 0, sequence = 0;
	const raw = await readFile(new URL('../src/features/post/components/CommentSection.vue', import.meta.url), 'utf8');
	const source = raw.slice(raw.indexOf('const signingOut = ref(false);'), raw.indexOf('\nfunction goToLogin()'));
	const scope = effectScope();
	const state = scope.run(() => vm.runInNewContext(`${source}\n;({signOut,signingOut})`, {
		ref, watch, props, member, draft, replyDraft, replyTargetId, items,
		getAuthenticationGeneration: () => generation,
		onBeforeUnmount: callback => cleanup.push(callback),
		signOutMember: () => { const request = deferred(); calls.push(request); return request.promise; },
		notifyError: message => { sequence += 1; notices.set(sequence, message); return sequence; },
		dismissToast: id => notices.delete(id),
	}));
	const unmount = () => { cleanup.splice(0).forEach(callback => callback()); scope.stop(); };
	t.after(unmount);
	return { ...state, props, member, draft, replyDraft, replyTargetId, items, calls, notices, unmount,
		advance: () => { generation += 1; },
		fresh() { draft.value = '새 댓글 초안'; replyDraft.value = '새 답글 초안'; replyTargetId.value = 101; items.value = rows(); },
	};
}

for (const cascades of [1, 2]) {
	test(`confirmed local logout clears the existing editor contract with ${cascades} synchronous auth updates`, async t => {
		const c = await controller(t), pending = c.signOut();
		await c.signOut(); assert.equal(c.calls.length, 1);
		c.advance(); c.member.value = null;
		if (cascades === 2) { c.advance(); }
		await nextTick(); c.calls[0].resolve(); await pending;
		assert.equal(c.draft.value, ''); assert.equal(c.replyDraft.value, ''); assert.equal(c.replyTargetId.value, null);
		assert.equal(c.items.value[0].likedByMe, false);
		assert.equal(c.items.value[0].replies[0].dislikedByMe, false);
		assert.equal(c.signingOut.value, false); assert.equal(c.notices.size, 0);
	});
}

for (const advance of [false, true]) {
	test(`an anonymous refresh before the old logout failure does not show an error (auth advance=${advance})`, async t => {
		const c = await controller(t), pending = c.signOut();
		if (advance) { c.advance(); }
		c.member.value = null; await nextTick();
		assert.equal(c.signingOut.value, true);
		c.calls[0].reject(new Error('익명 확인 전에 시작한 실패')); await pending;
		assert.equal(c.member.value, null); assert.equal(c.notices.size, 0);
		assert.equal(c.signingOut.value, false);
	});
}

for (const fails of [false, true]) {
	for (const boundary of ['member', 'ABA', 'getter', 'same-id-authentication', 'post', 'unmount']) {
		test(`${boundary}: old logout ${fails ? 'failure' : 'resolved success'} cannot touch new input, likes or toast`, async t => {
			const c = await controller(t), pending = c.signOut();
			if (boundary === 'member' || boundary === 'ABA') { c.member.value = { ...accountB }; }
			if (boundary === 'ABA') { c.member.value = { ...accountA }; }
			if (boundary === 'getter' || boundary === 'same-id-authentication') { c.advance(); }
			if (boundary === 'same-id-authentication') { c.member.value = { ...accountA }; }
			if (boundary === 'post') { c.props.postId = 2; }
			if (boundary === 'unmount') { c.unmount(); }
			await nextTick(); c.fresh();
			fails ? c.calls[0].reject(new Error('이전 로그아웃 실패')) : c.calls[0].resolve();
			await pending;
			assert.equal(c.draft.value, '새 댓글 초안'); assert.equal(c.replyDraft.value, '새 답글 초안');
			assert.equal(c.replyTargetId.value, 101);
			assert.equal(c.items.value[0].likedByMe, true);
			assert.equal(c.items.value[0].replies[0].dislikedByMe, true);
			assert.equal(c.signingOut.value, false); assert.equal(c.notices.size, 0);
		});
	}

	test(`old ${fails ? 'catch' : 'success'} and finally preserve the new B logout request`, async t => {
		const c = await controller(t), old = c.signOut();
		c.advance(); c.member.value = { ...accountB }; await nextTick(); c.fresh();
		const current = c.signOut(); assert.equal(c.calls.length, 2);
		fails ? c.calls[0].reject(new Error('A 오류')) : c.calls[0].resolve(); await old;
		assert.equal(c.signingOut.value, true); assert.equal(c.draft.value, '새 댓글 초안');
		assert.equal(c.notices.size, 0);
		c.advance(); c.member.value = null; c.advance(); await nextTick();
		c.calls[1].resolve(); await current;
		assert.equal(c.signingOut.value, false); assert.equal(c.draft.value, '');
	});
}

test('a getter-only change is checked before the next logout despite the old pending flag', async t => {
	const c = await controller(t), old = c.signOut();
	c.advance(); c.fresh(); const current = c.signOut(); assert.equal(c.calls.length, 2);
	c.calls[0].reject(new Error('이전 실패')); await old;
	assert.equal(c.signingOut.value, true); assert.equal(c.notices.size, 0);
	c.calls[1].reject(new Error('현재 실패')); await current;
	assert.equal(c.signingOut.value, false); assert.equal(c.notices.size, 1);
});

test('a resolved store call without a confirmed anonymous state preserves the same-account drafts', async t => {
	const c = await controller(t), pending = c.signOut();
	c.member.value = { ...accountA }; await nextTick();
	assert.equal(c.signingOut.value, true);
	c.calls[0].resolve(); await pending;
	assert.equal(c.draft.value, '댓글 초안'); assert.equal(c.replyDraft.value, '답글 초안');
	assert.equal(c.items.value[0].likedByMe, true); assert.equal(c.signingOut.value, false);
});

test('nickname refresh and a failed logout keep the valid pending context and input', async t => {
	const c = await controller(t), pending = c.signOut();
	c.member.value = { ...accountA, nickname: '새 닉네임' }; await nextTick();
	assert.equal(c.signingOut.value, true);
	c.calls[0].reject(new Error('현재 실패')); await pending;
	assert.equal(c.member.value.id, 7); assert.equal(c.draft.value, '댓글 초안');
	assert.equal(c.notices.size, 1); assert.equal(c.signingOut.value, false);
});

test('identity reset dismisses only this logout error and preserves an unrelated toast', async t => {
	const c = await controller(t), pending = c.signOut();
	c.calls[0].reject(new Error('현재 실패')); await pending;
	c.notices.set(999, '다른 기능 알림'); c.member.value = { ...accountB }; await nextTick();
	assert.deepEqual([...c.notices.values()], ['다른 기능 알림']);
});

test('an old completion cannot dismiss the error from a finished newer logout', async t => {
	const c = await controller(t), old = c.signOut();
	c.member.value = { ...accountB }; await nextTick();
	const current = c.signOut(); assert.equal(c.calls.length, 2);
	c.calls[1].reject(new Error('B 현재 실패')); await current;
	const ids = [...c.notices.keys()];
	c.calls[0].reject(new Error('A 이전 실패')); await old;
	assert.deepEqual([...c.notices.keys()], ids); assert.equal(c.notices.size, 1);
});
