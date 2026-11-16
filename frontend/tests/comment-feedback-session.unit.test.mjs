import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { computed, effectScope, ref, toRef, reactive, watch } from 'vue';

const accountA = { id: 7, nickname: '회원 A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '회원 B', role: 'ROLE_USER' };
const comment = id => ({ id, author: `회원${id}`, content: `댓글${id}`, likeCount: 3, dislikeCount: 0,
	likedByMe: false, dislikedByMe: false, reportedByMe: false });
const reaction = { likeCount: 4, dislikeCount: 0, likedByMe: true, dislikedByMe: false };
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const stripImports = source => source.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '');

async function feedback(t) {
	const member = ref({ ...accountA }), postId = ref(1), calls = [], cleanup = [], dismissed = [];
	const notices = new Map(); let generation = 0, sequence = 0, redirects = 0;
	const request = (kind, ...args) => {
		const call = { kind, args, ...deferred() }; calls.push(call); return call.promise;
	};
	const source = stripImports(await readFile(new URL('../src/features/post/composables/useCommentFeedback.js', import.meta.url), 'utf8'))
		.replace('export function useCommentFeedback', 'function useCommentFeedback');
	const scope = effectScope();
	const state = scope.run(() => vm.runInNewContext(`${source}\n;useCommentFeedback({postId, member, isSignedIn, goToLogin})`, {
		ref, watch, postId, member, isSignedIn: computed(() => member.value !== null), goToLogin: () => { redirects += 1; },
		onBeforeUnmount: fn => cleanup.push(fn), getAuthenticationGeneration: () => generation,
		setCommentReaction: (...args) => request('add', ...args), removeCommentReaction: (...args) => request('remove', ...args),
		reportComment: (...args) => request('report', ...args),
		notifyError: message => { const id = ++sequence; notices.set(id, { message, kind: 'error' }); return id; },
		notifySuccess: message => { const id = ++sequence; notices.set(id, { message, kind: 'success' }); return id; },
		dismissToast: id => { dismissed.push(id); notices.delete(id); },
	}));
	const unmount = () => { cleanup.splice(0).forEach(fn => fn()); scope.stop(); };
	t.after(unmount);
	return { ...state, member, postId, calls, notices, dismissed, unmount, advance: () => { generation += 1; },
		otherToast: () => { const id = ++sequence; notices.set(id, { message: '다른 기능의 오류', kind: 'error' }); return id; },
		redirects: () => redirects };
}

const actions = [
	{ name: 'reaction', start: (f, item) => f.reactToComment(item, 'LIKE'), pending: (f, id) => f.isReactionPending(id), error: f => f.reactionError.value },
	{ name: 'report', start: (f, item) => { f.openReport(item); return f.submitReport({ reason: 'SPAM', detail: null }); }, pending: f => f.reportPending.value, error: f => f.reportError.value },
];

for (const action of actions) {
	for (const fails of [false, true]) {
		test(`${action.name}: old ${fails ? 'failure' : 'success'} cannot change B request, pending, input or toast`, async t => {
			const f = await feedback(t), oldItem = comment(1), currentItem = comment(1);
			const old = action.start(f, oldItem);
			f.member.value = { ...accountB };
			assert.equal(action.pending(f, 1), false);
			const current = action.start(f, currentItem);
			assert.equal(f.calls.length, 2);
			fails ? f.calls[0].reject(new Error('A 이전 오류')) : f.calls[0].resolve(reaction);
			await old;
			assert.equal(action.pending(f, 1), true);
			assert.equal(action.error(f), ''); assert.equal(f.notices.size, 0);
			assert.equal(currentItem.likeCount, 3); assert.equal(currentItem.reportedByMe, false);
			if (action.name === 'report') { assert.equal(f.reportTarget.value.id, 1); assert.equal(f.reportDone.value, ''); }
			f.calls[1].resolve(reaction); await current;
			assert.equal(action.pending(f, 1), false);
		});
	}
	for (const scenario of ['logout', 'ABA', 'post', 'unmount', 'getter response', 'same-id authentication refresh']) {
		for (const fails of [false, true]) {
			test(`${action.name}: ${scenario} drops old ${fails ? 'failure' : 'success'}`, async t => {
				const f = await feedback(t), target = comment(1), pending = action.start(f, target);
				if (scenario === 'logout') { f.member.value = null; }
				if (scenario === 'ABA') { f.member.value = accountB; f.member.value = accountA; }
				if (scenario === 'post') { f.postId.value = 2; }
				if (scenario === 'unmount') { f.unmount(); }
				if (scenario.includes('getter') || scenario.includes('authentication')) { f.advance(); }
				if (scenario === 'same-id authentication refresh') { f.member.value = { ...accountA }; }
				fails ? f.calls[0].reject(new Error('이전 요청 오류')) : f.calls[0].resolve(reaction);
				await pending;
				assert.equal(target.likeCount, 3); assert.equal(target.reportedByMe, false);
				assert.equal(action.error(f), ''); assert.equal(f.notices.size, 0);
				if (scenario !== 'unmount') { assert.equal(action.pending(f, 1), false); }
			});
		}
	}
	test(`${action.name}: getter-only change is noticed before the next operation`, async t => {
		const f = await feedback(t), old = action.start(f, comment(1));
		f.advance();
		const current = action.start(f, comment(1));
		assert.equal(f.calls.length, 2);
		f.calls[0].resolve(reaction); await old;
		assert.equal(action.pending(f, 1), true);
		f.calls[1].resolve(reaction); await current;
		assert.equal(action.pending(f, 1), false);
	});
	test(`${action.name}: nickname and same-account refresh preserve the valid request`, async t => {
		const f = await feedback(t), target = comment(1), pending = action.start(f, target);
		f.member.value = { ...accountA, nickname: '새 닉네임' };
		assert.equal(action.pending(f, 1), true);
		f.member.value = { ...f.member.value };
		f.calls[0].resolve(reaction); await pending;
		assert.equal(action.pending(f, 1), false);
		assert.equal(action.name === 'reaction' ? target.likedByMe : target.reportedByMe, true);
	});
	test(`${action.name}: identity reset dismisses only this feedback instance's persistent errors`, async t => {
		const f = await feedback(t), other = f.otherToast();
		const pending = action.start(f, comment(1)); f.calls[0].reject(new Error('현재 댓글 오류')); await pending;
		assert.equal(f.notices.size, 2);
		f.member.value = accountB;
		assert.equal(action.error(f), ''); assert.equal(f.notices.size, 1); assert.equal(f.notices.has(other), true);
		assert.equal(f.dismissed.includes(other), false);
	});
}

test('reaction additions/removals and independent comment requests retain their normal API behavior', async t => {
	const f = await feedback(t), a = comment(1), b = comment(2);
	const first = f.reactToComment(a, 'LIKE'); await f.reactToComment(a, 'DISLIKE');
	const second = f.reactToComment(b, 'DISLIKE'); assert.equal(f.calls.length, 2);
	f.calls[0].resolve(reaction); await first; assert.equal(f.isReactionPending(2), true);
	f.calls[1].resolve({ ...reaction, likedByMe: false, dislikedByMe: true }); await second;
	const remove = f.reactToComment(a, 'LIKE'); assert.equal(f.calls[2].kind, 'remove');
	f.calls[2].resolve({ ...reaction, likedByMe: false }); await remove;
	assert.equal(a.likedByMe, false); assert.equal(b.dislikedByMe, true);
});

test('anonymous reaction goes to login without making a request', async t => {
	const f = await feedback(t); f.member.value = null;
	await f.reactToComment(comment(1), 'LIKE'); assert.equal(f.calls.length, 0); assert.equal(f.redirects(), 1);
});

test('changing the report target within the same session keeps the replacement target open', async t => {
	const f = await feedback(t), a = comment(1), b = comment(2);
	f.openReport(a); const pending = f.submitReport({ reason: 'SPAM', detail: null });
	f.closeReport(); f.openReport(b);
	f.calls[0].resolve(); await pending;
	assert.equal(a.reportedByMe, true); assert.equal(f.reportTarget.value.id, 2); assert.equal(f.reportDone.value, '');
	assert.equal(f.notices.size, 0);
});

async function section(t) {
	const props = reactive({ postId: 1, comments: { total: 0, items: [] } });
	const member = ref({ ...accountA }), cleanup = [], events = [];
	let generation = 0;
	const raw = (await readFile(new URL('../src/features/post/components/CommentSection.vue', import.meta.url), 'utf8')).match(/<script setup>([\s\S]*?)<\/script>/)[1];
	const scope = effectScope();
	const state = scope.run(() => vm.runInNewContext(`${stripImports(raw)}\n;({mutations, changeComment})`, {
		ref, watch, computed, reactive, toRef, defineProps: () => props, defineEmits: () => (...args) => events.push(args), defineExpose() {},
		useRoute: () => ({ fullPath: '/posts/1' }), useRouter: () => ({ push() {} }),
		useMemberAuth: () => ({ member, isSignedIn: computed(() => member.value !== null) }),
		getAuthenticationGeneration: () => generation, onBeforeUnmount: fn => cleanup.push(fn),
		useCommentFeed: () => ({ items: ref([]), total: ref(0) }), useCommentTarget: () => ({}),
		useCommentComposer: () => ({ draft: ref(''), replyDraft: ref(''), replyTargetId: ref(null) }),
		useCommentFeedback: () => ({}), useCommentManagement: () => ({}), notifyError() {},
	}));
	const unmount = () => { cleanup.splice(0).forEach(fn => fn()); scope.stop(); };
	t.after(unmount);
	return { ...state, props, member, events, unmount, advance: () => { generation += 1; } };
}

for (const scenario of ['member', 'ABA', 'post', 'getter']) {
	test(`counter: ${scenario} separates A work and old finally cannot release B`, async t => {
		const s = await section(t), a = deferred(), b = deferred();
		const old = s.changeComment(() => a.promise);
		if (scenario === 'member') { s.member.value = accountB; }
		if (scenario === 'ABA') { s.member.value = accountB; s.member.value = accountA; }
		if (scenario === 'post') { s.props.postId = 2; }
		if (scenario === 'getter') { s.advance(); }
		if (scenario !== 'getter') { assert.equal(s.mutations.value, 0); }
		const current = s.changeComment(() => b.promise); assert.equal(s.mutations.value, 1);
		a.resolve(); await old; assert.equal(s.mutations.value, 1);
		b.resolve(); await current; assert.equal(s.mutations.value, 0);
		assert.equal(s.events.filter(([name]) => name === 'cancel-reload').length, 2);
	});
}

test('counter: concurrent same-account writes remain 2 then 1 then 0 and preserve cancel-reload', async t => {
	const s = await section(t), a = deferred(), b = deferred();
	const first = s.changeComment(() => a.promise), second = s.changeComment(() => b.promise);
	assert.equal(s.mutations.value, 2);
	s.member.value = { ...accountA, nickname: '새 닉네임' }; assert.equal(s.mutations.value, 2);
	a.resolve(); await first; assert.equal(s.mutations.value, 1);
	b.resolve(); await second; assert.equal(s.mutations.value, 0);
	assert.equal(s.events.filter(([name]) => name === 'cancel-reload').length, 2);
});

test('counter: getter-only response boundary clears obsolete work without a next request', async t => {
	const s = await section(t), response = deferred(), pending = s.changeComment(() => response.promise);
	s.advance(); response.resolve(); await pending; assert.equal(s.mutations.value, 0);
});

test('counter: unmount prevents a late finally from touching component state', async t => {
	const s = await section(t), response = deferred(), pending = s.changeComment(() => response.promise);
	s.unmount(); const before = s.mutations.value; response.resolve(); await pending; assert.equal(s.mutations.value, before);
});
