import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { effectScope, ref, watch } from 'vue';

const memberA = { id: 7, nickname: '회원 A' };
const memberB = { id: 8, nickname: '회원 B' };
const comment = id => ({ id, content: `댓글 ${id}`, replies: [], mine: true, edited: false });
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};

async function management(t) {
	const postId = ref(1), member = ref({ ...memberA });
	const items = ref([comment(101), comment(202), comment(303)]), total = ref(3);
	const calls = [], cleanups = [], toasts = new Map();
	let generation = 0, sequence = 0;
	const source = (await readFile(new URL('../src/features/post/composables/useCommentManagement.js', import.meta.url), 'utf8'))
		.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '').replace('export function', 'function');
	const request = (method, id, content) => {
		const call = { method, id, content, ...deferred() }; calls.push(call); return call.promise;
	};
	const scope = effectScope();
	const state = scope.run(() => vm.runInNewContext(`${source}\nuseCommentManagement({postId,member,items,total})`, {
		ref, watch, postId, member, items, total,
		onBeforeUnmount: callback => cleanups.push(callback),
		getAuthenticationGeneration: () => generation,
		updatePostComment: (id, content) => request('PUT', id, content),
		deletePostComment: id => request('DELETE', id),
		notifyError: message => { sequence += 1; toasts.set(sequence, message); return sequence; },
		dismissToast: id => toasts.delete(id),
	}));
	const unmount = () => { cleanups.splice(0).forEach(callback => callback()); scope.stop(); };
	t.after(unmount);
	return { ...state, postId, member, items, total, calls, toasts, unmount,
		advance: () => { generation += 1; },
	};
}

function begin(state, kind, target = state.items.value[0], content = '저장할 내용') {
	if (kind === 'edit') {
		state.startEdit(target); state.editDraft.value = content; return state.saveEdit(target);
	}
	state.askDelete(target); return state.confirmDelete(target);
}

for (const kind of ['edit', 'delete']) {
	test(`${kind}: normal completion keeps existing content and count policy and blocks duplicate submissions`, async t => {
		const state = await management(t), target = state.items.value[0];
		const pending = begin(state, kind, target);
		if (kind === 'edit') { await state.saveEdit(target); }
		else { await state.confirmDelete(target); }
		assert.equal(state.calls.length, 1);
		state.calls[0].resolve(); await pending;
		assert.equal(state.total.value, kind === 'edit' ? 3 : 2);
		assert.equal(state.items.value.length, kind === 'edit' ? 3 : 2);
		if (kind === 'edit') { assert.equal(target.content, '저장할 내용'); assert.equal(target.edited, true); }
	});

	for (const fails of [false, true]) {
		for (const boundary of ['member', 'ABA', 'authentication', 'post', 'unmount']) {
			test(`${kind}: ${boundary} ignores old ${fails ? 'failure' : 'success'} without changing replacement rows or totals`, async t => {
				const state = await management(t);
				const pending = begin(state, kind);
				if (boundary === 'member' || boundary === 'ABA') { state.member.value = { ...memberB }; }
				if (boundary === 'ABA') { state.member.value = { ...memberA }; }
				if (boundary === 'authentication') { state.advance(); }
				if (boundary === 'post') { state.postId.value = 2; }
				if (boundary === 'unmount') { state.unmount(); }
				state.items.value = [comment(101), comment(202), comment(303)]; state.total.value = 3;
				if (fails) { state.calls[0].reject(new Error('이전 작업 오류')); }
				else { state.calls[0].resolve(); }
				await pending;
				assert.equal(state.items.value.length, 3);
				assert.equal(state.total.value, 3);
				assert.equal(state.items.value[0].content, '댓글 101');
				assert.equal(state.editError.value, ''); assert.equal(state.deleteError.value, '');
				assert.equal(state.editPending.value, false); assert.equal(state.deletePending.value, false);
				assert.equal(state.toasts.size, 0);
			});
		}

		test(`${kind}: old ${fails ? 'failure' : 'success'} and finally preserve a new member request`, async t => {
			const state = await management(t);
			const old = begin(state, kind);
			state.member.value = { ...memberB };
			state.items.value = [comment(101), comment(202), comment(303)];
			const current = begin(state, kind, state.items.value[1], 'B의 새 초안');
			assert.equal(state.calls.length, 2);
			if (fails) { state.calls[0].reject(new Error('A의 오류')); }
			else { state.calls[0].resolve(); }
			await old;
			assert.equal(state[kind === 'edit' ? 'editPending' : 'deletePending'].value, true);
			assert.equal(state.editError.value, ''); assert.equal(state.deleteError.value, '');
			assert.equal(state.total.value, 3);
			if (kind === 'edit') { assert.equal(state.editDraft.value, 'B의 새 초안'); }
			state.calls[1].resolve(); await current;
			assert.equal(state[kind === 'edit' ? 'editPending' : 'deletePending'].value, false);
			assert.equal(state.toasts.size, 0);
		});
	}

	test(`${kind}: same identity nickname and session refresh preserve a valid pending request`, async t => {
		const state = await management(t);
		const pending = begin(state, kind);
		state.member.value = { ...memberA, nickname: '바뀐 닉네임' };
		assert.equal(state[kind === 'edit' ? 'editPending' : 'deletePending'].value, true);
		state.calls[0].resolve(); await pending;
		assert.equal(state.total.value, kind === 'delete' ? 2 : 3);
	});

	test(`${kind}: identity change clears its persistent error and toast while preserving another toast`, async t => {
		const state = await management(t);
		const pending = begin(state, kind);
		state.calls[0].reject(new Error('내 작업 오류')); await pending;
		assert.ok([...state.toasts.values()].includes('내 작업 오류'));
		state.toasts.set(999, '다른 기능 알림');
		state.member.value = { ...memberB };
		assert.equal(state.editError.value, ''); assert.equal(state.deleteError.value, '');
		assert.deepEqual([...state.toasts.values()], ['다른 기능 알림']);
	});
}

test('deleting a reply or a parent with replies retains existing placeholder and total policy', async t => {
	const state = await management(t), parent = state.items.value[0];
	parent.replies = [comment(104)];
	state.askDelete(parent.replies[0]);
	const removedReply = state.confirmDelete(parent.replies[0], parent);
	state.calls[0].resolve(); await removedReply;
	assert.equal(parent.replies.length, 0); assert.equal(state.total.value, 2);
	parent.replyHasNext = true;
	const removedParent = begin(state, 'delete', parent);
	state.calls[1].resolve(); await removedParent;
	assert.equal(parent.deleted, true); assert.equal(parent.hiddenByAdmin, false);
	assert.equal(parent.mine, false); assert.equal(parent.content, '');
	assert.equal(state.items.value.length, 3); assert.equal(state.total.value, 1);
});

test('authentication getter changes are checked before a stale delete confirmation can send', async t => {
	const state = await management(t), target = state.items.value[0];
	state.askDelete(target); state.advance(); const pending = state.confirmDelete(target);
	assert.equal(state.calls.length, 0);
	await pending;
	assert.equal(state.deletingId.value, null);
});
