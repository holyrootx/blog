import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue';

const member = id => ({ id, nickname: `가짜 회원 ${id}`, role: 'ROLE_USER' });
const pending = (state = 'SIGNUP_REQUIRED') => ({ loginState: state, suggestedNickname: '가짜 제안 이름' });
const failure = (status = 500) => Object.assign(new Error('가짜 이전 요청 오류'), { status, code: status === 409 ? 'RESOURCE_BUSY' : 'INTERNAL_SERVER_ERROR' });
const unit = (name, body) => test(name, { timeout: 2000 }, body);

function gate() {
	let resolve, reject, enter;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	const entered = new Promise(yes => { enter = yes; });
	return { promise, resolve, reject, enter, entered };
}

async function harness(result = 'success') {
	const queues = {}, calls = [];
	let authentication = 0, serverMember = null, returnPath = '/privacy?first', clears = 0;
	const route = reactive({ fullPath: `/oauth/callback?result=${result}&_cb=first`, query: { result } });
	const navigations = [];
	const router = { replace(target) {
		const path = typeof target === 'string' ? target : target.name === 'home' ? '/' : '/login';
		navigations.push(path); route.fullPath = path;
		return Promise.resolve();
	} };
	function request(kind, ...args) {
		calls.push({ kind, args });
		const next = queues[kind]?.shift();
		if (next?.promise) { next.enter(); return next.promise; }
		if (next instanceof Error) { return Promise.reject(next); }
		return Promise.resolve(next ?? (kind === 'me' ? serverMember : kind === 'pending' ? pending() : member(7)));
	}
	const storeSource = await readFile(new URL('../src/features/member/data/memberAuthStore.js', import.meta.url), 'utf8');
	const strip = source => source.replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '').replace(/\bexport (?=(async )?function|const|class)/g, '');
	const store = vm.runInNewContext(`${strip(storeSource)};({useMemberAuth,ensureMemberSession,refreshMemberSession,clearMemberSession,completeSignUp,completeReactivation,completeRejoin,onMemberSessionCleared})`, {
		computed, ref,
		getMemberSession: () => request('me'), refreshCsrfToken: () => request('csrf'),
		signUpMember: nickname => request('signup', nickname), reactivateMember: () => request('reactivate'), rejoinMember: nickname => request('rejoin', nickname),
		advanceAuthenticationGeneration: () => { authentication += 1; }, clearNotifications() {},
	});
	await store.ensureMemberSession();
	const unmounts = [], scope = effectScope();
	const pageSource = (await readFile(new URL('../src/features/member/pages/OAuthCallbackPage.vue', import.meta.url), 'utf8')).match(/<script setup>([\s\S]*?)<\/script>/)[1];
	const context = vm.createContext({ computed, ref, watch, onMounted() {}, onBeforeUnmount(fn) { unmounts.push(fn); },
		useRoute: () => route, useRouter: () => router, useMemberAuth: store.useMemberAuth,
		clearMemberSession() { clears += 1; store.clearMemberSession(); },
		ensureMemberSession: store.ensureMemberSession, completeSignUp: store.completeSignUp, completeReactivation: store.completeReactivation, completeRejoin: store.completeRejoin,
		getOAuthPending: () => request('pending'), refreshCsrfToken: () => request('csrf'),
		getAuthenticationGeneration: () => authentication,
		takeReturnPath() { calls.push({ kind: 'takeReturnPath' }); const path = returnPath; returnPath = null; return path; },
	});
	const view = scope.run(() => vm.runInContext(`${strip(pageSource)};({start,loadPending,submitSignUp,submitReactivate,submitRejoin,startRejoin,backToChoice,goHome,retryLogin,phase,submitting,checkingPending,pendingError,errorMessage,signupNickname,rejoinNickname,notice})`, context));
	return { view, store, route, calls, navigations,
		hold(kind) { const held = gate(); (queues[kind] ??= []).push(held); return held; },
		fail(kind, error) { (queues[kind] ??= []).push(error); },
		respond(kind, value) { (queues[kind] ??= []).push(value); },
		advance() { authentication += 1; },
		async switchMember(id, advance = true) { if (advance) { authentication += 1; } serverMember = id === null ? null : member(id); await store.refreshMemberSession(); },
		get memberId() { return store.useMemberAuth().member.value?.id ?? null; },
		get authentication() { return authentication; }, get clears() { return clears; },
		get returnPath() { return returnPath; }, set returnPath(value) { returnPath = value; },
		unmount() { unmounts.forEach(fn => fn()); scope.stop(); },
	};
}

for (const status of [200, 500]) {
	for (const advance of [false, true]) {
		unit(`actual store stale session HTTP${status} preserves new B (public advance=${advance})`, async () => {
			const h = await harness(); const old = h.hold('me'); const work = h.view.start();
			await old.entered;
			await h.switchMember(8, advance);
			h.returnPath = '/privacy?new-flow';
			if (status === 200) { old.resolve(member(7)); } else { old.reject(failure()); }
			await work;
			assert.equal(h.memberId, 8); assert.equal(h.clears, 1);
			assert.equal(h.returnPath, '/privacy?new-flow'); assert.deepEqual(h.navigations, []);
			h.unmount();
		});
	}
}

unit('an unmounted old session success may finish in the store but cannot navigate or consume another return path', async () => {
	const h = await harness(); const old = h.hold('me'); const work = h.view.start(); await old.entered;
	h.route.fullPath = '/privacy'; h.unmount(); h.returnPath = '/privacy?new-flow';
	old.resolve(member(7)); await work;
	assert.equal(h.memberId, 7, 'This page guard does not cancel a valid shared store response');
	assert.equal(h.returnPath, '/privacy?new-flow'); assert.deepEqual(h.navigations, []);
});

unit('normal existing login adopts its own member and a synchronous admin clear generation chain', async () => {
	const h = await harness(); h.store.onMemberSessionCleared(h.advance); h.respond('me', member(7));
	await h.view.start();
	assert.equal(h.memberId, 7); assert.equal(h.authentication, 2); assert.deepEqual(h.navigations, ['/privacy?first']);
	assert.equal(h.returnPath, null); h.unmount();
});

unit('normal existing login CSRF failure retains its original notice and clears only its own session', async () => {
	const h = await harness(); h.respond('me', member(7)); h.respond('csrf', {}); h.fail('csrf', failure());
	await h.view.start();
	assert.equal(h.memberId, null); assert.equal(h.view.phase.value, 'notice'); assert.equal(h.clears, 2);
	assert.equal(h.returnPath, '/privacy?first'); assert.deepEqual(h.navigations, []); h.unmount();
});

unit('leaving while CSRF is pending prevents the next pending GET', async () => {
	const h = await harness('signup-required'); const csrf = h.hold('csrf'); const work = h.view.start(); await csrf.entered;
	h.route.fullPath = '/privacy'; h.unmount(); csrf.resolve({}); await work;
	assert.equal(h.calls.filter(c => c.kind === 'pending').length, 0);
	assert.equal(h.returnPath, '/privacy?first');
});

unit('route fullPath A to B to A cannot revive the first pending result or change its draft', async () => {
	const h = await harness('signup-required'); await h.view.start();
	h.view.signupNickname.value = '현재 초안'; const old = h.hold('pending'); const work = h.view.loadPending(); await old.entered;
	const original = h.route.fullPath; h.route.fullPath = original+'&other=1'; h.route.fullPath = original;
	old.resolve(pending('REACTIVATION_REQUIRED')); await work;
	assert.equal(h.view.phase.value, 'signup'); assert.equal(h.view.signupNickname.value, '현재 초안'); h.unmount();
});

for (const status of [200, 500]) {
	unit(`an old pending HTTP${status} finally preserves a newer pending read and its current draft`, async () => {
		const h = await harness('signup-required'); await h.view.start();
		const old = h.hold('pending'); const a = h.view.loadPending(); await old.entered;
		h.route.fullPath += '&next=1';
		const fresh = h.hold('pending'); const b = h.view.loadPending(); await fresh.entered;
		h.view.signupNickname.value = '새로 고친 초안';
		if (status === 200) { old.resolve(pending('REACTIVATION_REQUIRED')); } else { old.reject(failure()); }
		await a;
		assert.equal(h.view.checkingPending.value, true); assert.equal(h.view.signupNickname.value, '새로 고친 초안'); assert.equal(h.view.pendingError.value, '');
		fresh.resolve(pending()); await b; assert.equal(h.view.checkingPending.value, false); h.unmount();
	});
}

for (const action of ['signup', 'reactivate', 'rejoin']) {
	unit(`normal ${action} accepts its own two authentication advances and preserves the return path contract`, async () => {
		const h = await harness('signup-required'); await h.view.start();
		if (action === 'signup') { await h.view.submitSignUp('새 이름'); }
		if (action === 'reactivate') { await h.view.submitReactivate(); }
		if (action === 'rejoin') { await h.view.submitRejoin('새 이름'); }
		assert.equal(h.authentication, 2); assert.equal(h.memberId, 7); assert.deepEqual(h.navigations, ['/privacy?first']);
		assert.equal(h.returnPath, null); h.unmount();
	});
}

for (const status of [200, 409, 500]) {
	unit(`old submit HTTP${status} after B cannot navigate, recheck pending, or overwrite current errors`, async () => {
		const h = await harness('signup-required'); await h.view.start(); const post = h.hold('signup'); const work = h.view.submitSignUp('이전 입력'); await post.entered;
		await h.switchMember(8); h.returnPath = '/privacy?new-flow';
		if (status === 200) { post.resolve(member(7)); } else { post.reject(failure(status)); }
		await work;
		assert.equal(h.memberId, 8); assert.equal(h.view.errorMessage.value, ''); assert.equal(h.returnPath, '/privacy?new-flow');
		assert.equal(h.calls.filter(c => c.kind === 'pending').length, 1); assert.deepEqual(h.navigations, []); h.unmount();
	});
}

unit('a getter-only authentication change is checked after successful submit without pretending the store was cancelled', async () => {
	const h = await harness('signup-required'); await h.view.start(); const post = h.hold('signup'); const work = h.view.submitSignUp('이전 입력'); await post.entered;
	h.advance(); h.returnPath = '/privacy?new-flow'; post.resolve(member(7)); await work;
	assert.equal(h.memberId, 7); assert.equal(h.returnPath, '/privacy?new-flow'); assert.deepEqual(h.navigations, []); h.unmount();
});

unit('a current409 rechecks the server state under the same parent request and preserves nickname edits', async () => {
	const h = await harness('signup-required'); await h.view.start(); h.view.signupNickname.value = '수정한 초안'; h.fail('signup', failure(409));
	await h.view.submitSignUp('수정한 초안'); await nextTick();
	assert.equal(h.calls.filter(c => c.kind === 'pending').length, 2); assert.equal(h.view.signupNickname.value, '수정한 초안');
	assert.equal(h.view.submitting.value, false); assert.equal(h.view.checkingPending.value, false); assert.match(h.view.errorMessage.value, /다시 확인/); h.unmount();
});

for (const action of ['home', 'retry']) {
	unit(`new notice ${action} intent is independent of the old authentication snapshot`, async () => {
		const h = await harness('login-failed'); await h.view.start(); await h.switchMember(8); h.returnPath = '/privacy?new-flow';
		await (action === 'home' ? h.view.goHome() : h.view.retryLogin());
		assert.deepEqual(h.navigations, [action === 'home' ? '/privacy?new-flow' : '/login']);
		assert.equal(h.memberId, 8); h.unmount();
	});
}
for (const action of ['choose', 'back', 'submit']) {
	unit(`new ${action} intent rechecks stale server state before choosing or writing`, async () => {
		const h = await harness('signup-required'); h.respond('pending', pending('REACTIVATION_REQUIRED')); await h.view.start();
		if (action === 'back') { await h.view.startRejoin(); }
		await h.switchMember(8); h.respond('pending', pending());
		if (action === 'choose') { await h.view.startRejoin(); }
		if (action === 'back') { await h.view.backToChoice(); }
		if (action === 'submit') { await h.view.submitReactivate(); }
		assert.equal(h.view.phase.value, 'signup'); assert.equal(h.calls.filter(c => c.kind === 'pending').length, 2);
		assert.equal(h.calls.filter(c => ['signup', 'reactivate', 'rejoin'].includes(c.kind)).length, 0); h.unmount();
	});
}
unit('private member to anonymous ABA permanently invalidates an old pending request', async () => {
	const h = await harness('signup-required'); await h.view.start();
	const held = h.hold('pending'), work = h.view.loadPending(); await held.entered;
	await h.switchMember(8, false); await h.switchMember(null, false);
	h.view.signupNickname.value = 'ABA 이후 현재 이름'; held.resolve(pending('REACTIVATION_REQUIRED')); await work;
	assert.equal(h.view.phase.value, 'signup'); assert.equal(h.view.signupNickname.value, 'ABA 이후 현재 이름'); h.unmount();
});

for (const kind of ['me', 'pending']) {
	unit(`a stale initial ${kind} result leaves a working explicit recheck instead of endless loading`, async () => {
		const h = await harness(kind === 'me' ? 'success' : 'signup-required'), held = h.hold(kind), work = h.view.start();
		await held.entered; await h.switchMember(8, false);
		held.resolve(kind === 'me' ? member(7) : pending()); await work;
		assert.equal(h.view.phase.value, 'pending-error'); assert.equal(h.view.checkingPending.value, false);
		await h.view.loadPending(); assert.equal(h.view.phase.value, 'signup'); assert.equal(h.memberId, 8); h.unmount();
	});
}
unit('an explicit invalid old navigation token cannot turn into a fresh user request', async () => {
	const h = await harness('login-failed'); await h.view.start(); await h.view.goHome(null);
	assert.deepEqual(h.navigations, []); assert.equal(h.returnPath, '/privacy?first'); h.unmount();
});
