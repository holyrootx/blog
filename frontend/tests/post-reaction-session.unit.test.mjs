import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { computed, effectScope, reactive, ref, watch } from 'vue';

const memberA = { id: 1, nickname: '회원 A' };
const memberB = { id: 2, nickname: '회원 B' };
const post = (id = 1, likedByMe = false) => ({ id, likeCount: 3, dislikeCount: 2, likedByMe, dislikedByMe: false });
const reaction = (likedByMe = true) => ({ likeCount: likedByMe ? 4 : 3, dislikeCount: 2, likedByMe, dislikedByMe: false });
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};

async function article(t, liked = false) {
	const props = reactive({ post: post(1, liked) });
	const member = ref({ ...memberA });
	const calls = [], events = [], errors = [], cleanup = [], redirects = [], dismissed = [];
	let generation = 0;
	const source = (await readFile(new URL('../src/features/post/components/PostArticle.vue', import.meta.url), 'utf8'))
		.match(/<script setup>([\s\S]*?)<\/script>/)[1]
		.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '');
	const request = (method, postId, type) => {
		const call = { ...deferred(), method, postId, type };
		calls.push(call);
		return call.promise;
	};
	const scope = effectScope();
	const state = scope.run(() => vm.runInNewContext(`${source}\n;({toggleLike, reactionPending, reactionError})`, {
		ref, watch, computed,
		defineProps: () => props,
		defineEmits: () => (name, value) => {
			events.push({ name, value });
			if (name === 'reaction-changed') {
				props.post = { ...props.post, ...value };
			}
		},
		useMemberAuth: () => ({ member, isSignedIn: computed(() => member.value !== null) }),
		useRoute: () => ({ fullPath: '/posts/1' }),
		useRouter: () => ({ push: async value => redirects.push(value) }),
		rememberReturnPath() {},
		getAuthenticationGeneration: () => generation,
		setPostReaction: (id, type) => request('PUT', id, type),
		removePostReaction: (id, type) => request('DELETE', id, type),
		notifyError: message => errors.push(message),
		dismissToast: id => dismissed.push(id),
		onBeforeUnmount: fn => cleanup.push(fn),
		clearTimeout, setTimeout,
	}));
	const unmount = () => { cleanup.splice(0).forEach(fn => fn()); scope.stop(); };
	t.after(unmount);
	return { ...state, props, member, calls, events, errors, redirects, dismissed, unmount,
		advance: () => { generation += 1; } };
}

test('정상 추가·해제는 해당 글 API를 호출하고 중복 클릭은 병합한다', async (t) => {
	const a = await article(t);
	const first = a.toggleLike();
	await a.toggleLike();
	assert.equal(a.calls.length, 1);
	assert.equal(a.calls[0].method, 'PUT');
	assert.equal(a.calls[0].postId, 1);
	a.calls[0].resolve(reaction()); await first;
	assert.equal(a.props.post.likedByMe, true);
	const removed = a.toggleLike();
	assert.equal(a.calls[1].method, 'DELETE');
	a.calls[1].resolve(reaction(false)); await removed;
	assert.equal(a.props.post.likedByMe, false);
	assert.equal(a.reactionPending.value, false);
});

for (const fails of [false, true]) {
	test(`계정 변경 뒤 이전 ${fails ? '실패' : '성공'}가 현재 표시·오류에 적용되지 않는다`, async (t) => {
		const a = await article(t);
		const pending = a.toggleLike();
		a.member.value = null;
		const events = a.events.length;
		const releasedOnChange = !a.reactionPending.value;
		if (fails) { a.calls[0].reject(new Error('이전 요청 오류')); }
		else { a.calls[0].resolve(reaction()); }
		await pending;
		assert.equal(releasedOnChange, true);
		assert.equal(a.events.length, events);
		assert.equal(a.props.post.likedByMe, false);
		assert.equal(a.props.post.likeCount, 3);
		assert.equal(a.reactionError.value, '');
		assert.deepEqual(a.errors, []);
	});

	test(`이전 ${fails ? '실패' : '성공'}의 finally가 새 회원의 진행 상태를 풀지 않는다`, async (t) => {
		const a = await article(t);
		const old = a.toggleLike();
		a.member.value = { ...memberB };
		const next = a.toggleLike();
		assert.equal(a.calls.length, 2);
		if (fails) { a.calls[0].reject(new Error('A의 오류')); }
		else { a.calls[0].resolve(reaction()); }
		await old;
		assert.equal(a.reactionPending.value, true);
		assert.equal(a.reactionError.value, '');
		a.calls[1].resolve(reaction()); await next;
		assert.equal(a.reactionPending.value, false);
		assert.equal(a.props.post.likedByMe, true);
		assert.deepEqual(a.errors, []);
	});
}

test('A에서 B를 거쳐 A로 돌아와도 이전 A 요청은 다시 유효해지지 않는다', async (t) => {
	const a = await article(t);
	const pending = a.toggleLike();
	a.member.value = { ...memberB };
	a.member.value = { ...memberA };
	const events = a.events.length;
	a.calls[0].resolve(reaction()); await pending;
	assert.equal(a.events.length, events);
	assert.equal(a.props.post.likedByMe, false);
});

for (const refresh of [false, true]) {
	test(`같은 ID의 인증 세대 변경은 ${refresh ? '세션 재조회 후에도' : '응답 경계에서'} 이전 요청을 무효화한다`, async (t) => {
		const a = await article(t);
		const pending = a.toggleLike();
		a.advance();
		if (refresh) { a.member.value = { ...memberA }; }
		a.calls[0].resolve(reaction()); await pending;
		assert.equal(a.props.post.likedByMe, false);
		assert.equal(a.props.post.likeCount, 3);
		assert.equal(a.reactionPending.value, false);
	});
}

test('회원 교체는 이전 내 반응과 오류만 비우고 서버 합계를 추정 변경하지 않는다', async (t) => {
	const a = await article(t, true);
	const pending = a.toggleLike();
	a.calls[0].reject(new Error('현재 요청 오류')); await pending;
	assert.equal(a.reactionError.value, '현재 요청 오류');
	a.member.value = { ...memberB };
	assert.equal(a.props.post.likedByMe, false);
	assert.equal(a.props.post.likeCount, 3);
	assert.equal(a.props.post.dislikeCount, 2);
	assert.equal(a.reactionError.value, '');
	assert.deepEqual(a.dismissed, [1]);
});

for (const fails of [false, true]) {
	test(`글 ID 변경 뒤 이전 ${fails ? '실패' : '성공'} 응답이 새 글을 바꾸지 않는다`, async (t) => {
		const a = await article(t);
		const pending = a.toggleLike();
		a.props.post = { ...post(2), likeCount: 20 };
		const events = a.events.length;
		if (fails) { a.calls[0].reject(new Error('옛 글 오류')); }
		else { a.calls[0].resolve(reaction()); }
		await pending;
		assert.equal(a.props.post.likeCount, 20);
		assert.equal(a.events.length, events);
		assert.equal(a.reactionError.value, '');
		assert.equal(a.reactionPending.value, false);
	});

	test(`언마운트 뒤 이전 ${fails ? '실패' : '성공'} 응답은 이벤트·오류 알림을 내보내지 않는다`, async (t) => {
		const a = await article(t);
		const pending = a.toggleLike();
		a.unmount();
		if (fails) { a.calls[0].reject(new Error('떠난 화면 오류')); }
		else { a.calls[0].resolve(reaction()); }
		await pending;
		assert.equal(a.events.length, 0);
		assert.deepEqual(a.errors, []);
	});
}

test('같은 ID·같은 인증 세대의 닉네임 갱신과 세션 재확인은 정상 진행을 보존한다', async (t) => {
	const a = await article(t);
	const pending = a.toggleLike();
	a.member.value = { ...memberA, nickname: '새 닉네임' };
	assert.equal(a.reactionPending.value, true);
	a.calls[0].resolve(reaction()); await pending;
	assert.equal(a.props.post.likedByMe, true);
	assert.equal(a.events.length, 1);
});

test('새 글에서 시작한 요청도 이전 글의 finally가 진행 상태를 풀지 못한다', async (t) => {
	const a = await article(t);
	const old = a.toggleLike();
	a.props.post = post(2);
	const next = a.toggleLike();
	assert.equal(a.calls.length, 2);
	assert.equal(a.calls[1].postId, 2);
	a.calls[0].resolve(reaction()); await old;
	assert.equal(a.reactionPending.value, true);
	assert.equal(a.props.post.likedByMe, false);
	a.calls[1].resolve(reaction()); await next;
	assert.equal(a.props.post.likedByMe, true);
});

test('비회원은 반응 API 대신 로그인 안내로 이동한다', async (t) => {
	const a = await article(t);
	a.member.value = null;
	await a.toggleLike();
	assert.equal(a.calls.length, 0);
	assert.equal(a.redirects[0].name, 'member-login');
});
