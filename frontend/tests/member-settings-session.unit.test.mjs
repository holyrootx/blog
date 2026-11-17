import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

const accountA = { id: 7, nickname: '회원 A', role: 'ROLE_USER' };
const accountB = { id: 8, nickname: '회원 B', role: 'ROLE_USER' };
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const flush = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick(); };
const stripImports = source => source.replace(/^import[\s\S]*?;\n/gm, '').replace(/^export /gm, '');

async function actualStore({ adminCascade = true } = {}) {
	let serverMember = { ...accountA }, authentication = 0;
	const writes = [], csrf = [];
	const send = (kind, value) => { const pending = deferred(); writes.push({ kind, value, ...pending }); return pending.promise; };
	const source = stripImports(await readFile(new URL('../src/features/member/data/memberAuthStore.js', import.meta.url), 'utf8'));
	const store = vm.runInNewContext(source + '\n;({useMemberAuth,ensureMemberSession,refreshMemberSession,completeSignUp,changeNickname,withdraw,signOutMember,onMemberSessionCleared})', {
		ref, computed,
		getMemberSession: async () => serverMember,
		changeMemberNickname: value => send('nickname', value), withdrawMember: () => send('withdraw'),
		logoutMember: async () => { serverMember = null; }, signUpMember: async () => serverMember,
		refreshCsrfToken: () => csrf.length ? csrf.shift().promise : Promise.resolve(),
		advanceAuthenticationGeneration: () => { authentication += 1; }, clearNotifications() {},
	});
	if (adminCascade) { store.onMemberSessionCleared(() => { authentication += 1; }); }
	await store.ensureMemberSession();
	return { ...store, writes, csrf, member: store.useMemberAuth().member,
		getAuthenticationGeneration: () => authentication,
		advance: () => { authentication += 1; },
		setServer(value) { serverMember = value; },
		async changeAccount(value, { advance = true } = {}) {
			serverMember = value;
			if (advance) { authentication += 1; }
			await store.refreshMemberSession(); await flush();
		},
	};
}

async function actualPage(t, options) {
	const store = await actualStore(options), cleanup = [], mounted = [], notices = new Map();
	let sequence = 0, stopped = false;
	const routeRecords = [], identityScopes = [];
	const router = createRouter({ history: createMemoryHistory(), routes: [
		{ path: '/', name: 'home', component: {} }, { path: '/settings', name: 'member-settings', component: {} },
		{ path: '/login', name: 'member-login', component: {} }, { path: '/terms', name: 'terms', component: {} },
	] });
	await router.push('/settings'); await router.isReady();
	const route = reactive({ get fullPath() { return router.currentRoute.value.fullPath; } });
	const scope = effectScope();
	const unmount = () => { if (stopped) { return; } stopped = true; cleanup.splice(0).forEach(callback => callback()); scope.stop(); };
	t.after(async () => { await router.push('/terms'); unmount(); });
	const nativeAfterEach = router.afterEach;
	nativeAfterEach(to => { if (to.name !== 'member-settings') { void Promise.resolve().then(unmount); } });
	router.afterEach = callback => {
		const record = { active: true, stops: 0 }; routeRecords.push(record);
		const stop = nativeAfterEach(callback);
		return () => { record.stops += 1; record.active = false; stop(); };
	};
	const raw = await readFile(new URL('../src/features/member/pages/MemberSettingsPage.vue', import.meta.url), 'utf8');
	const source = stripImports(raw.match(/<script setup>\n([\s\S]*?)\n<\/script>/)[1]);
	const notify = message => { sequence += 1; notices.set(sequence, message); return sequence; };
	const state = scope.run(() => vm.runInNewContext(source + '\n;({saveNickname,confirmWithdraw,nickname,savingNickname,nicknameError,nicknameSaved,withdrawing,withdrawConfirming,withdrawError})', {
		computed, ref, watch, useRouter: () => router, useRoute: () => route,
		effectScope: detached => { const scope = effectScope(detached); identityScopes.push(scope); return scope; },
		onMounted: callback => mounted.push(callback), onBeforeUnmount: callback => cleanup.push(callback),
		useMemberAuth: store.useMemberAuth, ensureMemberSession: store.ensureMemberSession,
		changeNickname: store.changeNickname, withdraw: store.withdraw,
		getAuthenticationGeneration: store.getAuthenticationGeneration,
		notifyError: notify, notifySuccess: notify, dismissToast: id => notices.delete(id),
		getMyComments: async () => [], getRecentPosts: async () => ({ items: [], page: 0, totalPages: 0 }),
		useSectionLoader: () => ({ loading: reactive({ comments: false, recent: false }), restart() {},
			load: (name, task) => Promise.resolve(task.request()).then(task.apply) }),
	}));
	await Promise.all(mounted.map(callback => callback())); await flush();
	return { ...store, ...state, router, notices, routeRecords, unmount,
		async atRoute(name) {
			if (router.currentRoute.value.name !== name) {
				await new Promise((resolve, reject) => {
					const timer = setTimeout(() => { stop(); reject(new Error('Expected router completion did not arrive')); }, 2000);
					const stop = nativeAfterEach((to, from, failure) => {
						if (!failure && to.name === name) { clearTimeout(timer); stop(); resolve(); }
					});
				});
			}
			await flush();
		},
		otherToast: () => notify('다른 기능의 오류'),
		activeObservers: () => routeRecords.filter(record => record.active).length,
		activeIdentityObservers: () => identityScopes.filter(scope => scope.active).length,
		get disposed() { return stopped; },
	};
}

test('store: a current withdrawal resolves true and a failed CSRF refresh keeps that success contract', async () => {
	for (const csrfFails of [false, true]) {
		const c = await actualStore(), token = deferred(); c.csrf.push(token);
		const request = c.withdraw(); c.setServer(null); c.writes[0].resolve(); await flush();
		assert.equal(c.member.value, null);
		csrfFails ? token.reject(new Error('가짜 CSRF 실패')) : token.resolve();
		assert.equal(await request, true);
	}
});

test('store: an old HTTP success resolves false without clearing replacement B', async () => {
	const c = await actualStore(), request = c.withdraw();
	await c.changeAccount({ ...accountB }); c.writes[0].resolve();
	assert.equal(await request, false); assert.equal(c.member.value.id, 8);
});

for (const boundary of ['login B', 'anonymous refresh']) {
	test(`store: ${boundary} while the post-clear CSRF is held makes the old result false`, async () => {
		const c = await actualStore(), token = deferred(); c.csrf.push(token);
		const request = c.withdraw(); c.setServer(null); c.writes[0].resolve(); await flush();
		if (boundary === 'login B') { c.setServer({ ...accountB }); await c.completeSignUp('회원 B'); }
		else { await c.refreshMemberSession(); }
		token.resolve(); assert.equal(await request, false);
		assert.equal(c.member.value?.id ?? null, boundary === 'login B' ? 8 : null);
	});
}

test('store: a failed withdrawal rejects and preserves the existing member', async () => {
	const c = await actualStore(), request = c.withdraw(); c.writes[0].reject(new Error('가짜 탈퇴 실패'));
	await assert.rejects(request, /가짜 탈퇴 실패/); assert.equal(c.member.value.id, 7);
});

test('store: the public auth getter alone does not cancel private-generation work', async () => {
	const c = await actualStore(), request = c.withdraw(); c.advance(); c.writes[0].resolve();
	assert.equal(await request, true); assert.equal(c.member.value, null);
});

for (const fails of [false, true]) {
	for (const boundary of ['member', 'ABA', 'same-id authentication', 'getter only', 'route', 'unmount']) {
		test(`nickname ${boundary}: old ${fails ? 'failure' : 'success'} cannot alter the current draft or feedback`, async t => {
			const c = await actualPage(t); c.nickname.value = 'A 요청'; const old = c.saveNickname();
			if (boundary === 'member' || boundary === 'ABA') { await c.changeAccount({ ...accountB }); }
			if (boundary === 'ABA') { await c.changeAccount({ ...accountA }); }
			if (boundary === 'same-id authentication') { await c.changeAccount({ ...accountA }); }
			if (boundary === 'getter only') { c.advance(); }
			if (boundary === 'route') { await c.router.push('/settings?view=other'); }
			if (boundary === 'unmount') { await c.router.push('/terms'); await flush(); }
			c.nickname.value = '현재 초안';
			fails ? c.writes[0].reject(new Error('이전 오류')) : c.writes[0].resolve({ ...accountA, nickname: '이전 응답' });
			await old;
			assert.equal(c.nickname.value, '현재 초안'); assert.equal(c.nicknameSaved.value, false);
			assert.equal(c.nicknameError.value, ''); assert.equal(c.savingNickname.value, false); assert.equal(c.notices.size, 0);
		});
	}
	test(`nickname: old ${fails ? 'catch' : 'finally'} cannot release B pending`, async t => {
		const c = await actualPage(t); c.nickname.value = 'A 요청'; const old = c.saveNickname();
		await c.changeAccount({ ...accountB }); c.nickname.value = 'B 요청'; const current = c.saveNickname();
		assert.equal(c.writes.length, 2);
		fails ? c.writes[0].reject(new Error('이전 오류')) : c.writes[0].resolve({ ...accountA }); await old;
		assert.equal(c.savingNickname.value, true); assert.equal(c.nickname.value, 'B 요청');
		c.writes[1].resolve({ ...accountB, nickname: 'B 요청' }); await current;
		assert.equal(c.savingNickname.value, false); assert.equal(c.nicknameSaved.value, true);
	});
	test(`withdrawal: old ${fails ? 'failure' : 'success'} cannot release B pending and all route observers finish`, async t => {
		const c = await actualPage(t); const old = c.confirmWithdraw(); assert.equal(c.activeObservers(), 1);
		await c.changeAccount({ ...accountB }); assert.equal(c.activeObservers(), 0);
		const current = c.confirmWithdraw(); assert.equal(c.activeObservers(), 1);
		fails ? c.writes[0].reject(new Error('이전 오류')) : c.writes[0].resolve(); await old;
		assert.equal(c.withdrawing.value, true); assert.equal(c.activeObservers(), 1);
		c.writes[1].reject(new Error('B 현재 오류')); await current;
		assert.equal(c.withdrawing.value, false); assert.equal(c.withdrawError.value, 'B 현재 오류');
		assert.equal(c.activeObservers(), 0); assert.ok(c.routeRecords.every(record => record.stops === 1));
		assert.equal(c.activeIdentityObservers(), 0);
	});
}

for (const adminCascade of [false, true]) {
	test(`own withdrawal keeps final home across its expected login exit (admin cascade=${adminCascade})`, async t => {
		const c = await actualPage(t, { adminCascade }), token = deferred(); c.csrf.push(token);
		const request = c.confirmWithdraw(); c.setServer(null); c.writes[0].resolve(); await flush();
		await c.atRoute('member-login');
		assert.equal(c.router.currentRoute.value.name, 'member-login'); assert.equal(c.disposed, true);
		assert.equal(c.activeObservers(), 1);
		token.resolve(); await request; assert.equal(c.router.currentRoute.value.name, 'home');
		assert.equal(c.activeObservers(), 0); assert.equal(c.routeRecords[0].stops, 1);
		assert.equal(c.activeIdentityObservers(), 0);
	});
}

for (const boundary of ['route ABA', 'new member', 'getter only']) {
	test(`own anonymous exit: ${boundary} during CSRF prevents the old final home and removes its observer`, async t => {
		const c = await actualPage(t), token = deferred(); c.csrf.push(token);
		const request = c.confirmWithdraw(); c.setServer(null); c.writes[0].resolve(); await flush(); await flush();
		await c.atRoute('member-login'); assert.equal(c.router.currentRoute.value.name, 'member-login');
		if (boundary === 'route ABA') { await c.router.push('/terms'); await c.router.push('/login'); }
		if (boundary === 'new member') {
			c.setServer({ ...accountB }); await c.completeSignUp('회원 B');
			assert.equal(c.activeObservers(), 0, 'A new account removes the detached login-exit observer before the old request settles');
			assert.equal(c.activeIdentityObservers(), 0);
		}
		if (boundary === 'getter only') { c.advance(); }
		token.resolve(); await request;
		assert.equal(c.router.currentRoute.value.name, 'member-login'); assert.equal(c.activeObservers(), 0);
		assert.equal(c.routeRecords[0].stops, 1);
		assert.equal(c.activeIdentityObservers(), 0);
	});
}

for (const fails of [false, true]) {
	test(`external logout then old withdrawal ${fails ? 'failure' : 'success'} stays on login without stale feedback`, async t => {
		const c = await actualPage(t), request = c.confirmWithdraw(); await c.signOutMember(); await c.atRoute('member-login');
		fails ? c.writes[0].reject(new Error('이전 오류')) : c.writes[0].resolve(); await request;
		assert.equal(c.router.currentRoute.value.name, 'member-login'); assert.equal(c.notices.size, 0);
		assert.equal(c.activeObservers(), 0); assert.equal(c.routeRecords[0].stops, 1);
		assert.equal(c.activeIdentityObservers(), 0);
	});
}

test('same-id nickname and session refresh preserve pending and the newer draft', async t => {
	const c = await actualPage(t); c.nickname.value = '요청 닉네임'; const request = c.saveNickname();
	await c.changeAccount({ ...accountA, nickname: '같은 회원 새 표시명' }, { advance: false });
	c.nickname.value = '더 최신 초안'; assert.equal(c.savingNickname.value, true);
	c.writes[0].resolve({ ...accountA, nickname: '요청 닉네임' }); await request;
	assert.equal(c.nickname.value, '더 최신 초안'); assert.equal(c.nicknameSaved.value, true);
});

test('identity change dismisses only this page owned persistent errors', async t => {
	const c = await actualPage(t); c.otherToast(); c.nickname.value = '변경';
	const nickname = c.saveNickname(); c.writes[0].reject(new Error('닉네임 오류')); await nickname;
	const withdrawal = c.confirmWithdraw(); c.writes[1].reject(new Error('탈퇴 오류')); await withdrawal;
	assert.equal(c.notices.size, 3);
	await c.changeAccount({ ...accountB }); assert.equal(c.notices.size, 1);
	assert.equal([...c.notices.values()][0], '다른 기능의 오류');
	assert.equal(c.nicknameError.value, ''); assert.equal(c.withdrawError.value, '');
	assert.equal(c.withdrawConfirming.value, false); assert.equal(c.activeObservers(), 0);
});

test('retrying nickname only clears its own error while withdrawal and unrelated errors remain', async t => {
	const c = await actualPage(t); c.otherToast(); c.nickname.value = '변경';
	const nickname = c.saveNickname(); c.writes[0].reject(new Error('닉네임 오류')); await nickname;
	const withdrawal = c.confirmWithdraw(); c.writes[1].reject(new Error('탈퇴 오류')); await withdrawal;
	const retry = c.saveNickname(); assert.equal(c.notices.size, 2); assert.equal(c.withdrawError.value, '탈퇴 오류');
	c.writes[2].resolve({ ...accountA, nickname: '변경' }); await retry;
	assert.ok([...c.notices.values()].includes('다른 기능의 오류'));
	assert.ok([...c.notices.values()].includes('탈퇴 오류'));
});

test('getter-only changes are rechecked before the next nickname action without erasing its draft', async t => {
	const c = await actualPage(t); c.nickname.value = 'A 요청'; const old = c.saveNickname();
	c.advance(); c.nickname.value = '새 요청 초안'; const current = c.saveNickname();
	assert.equal(c.writes.length, 2); c.writes[0].resolve({ ...accountA, nickname: '이전 응답' }); await old;
	assert.equal(c.savingNickname.value, true); assert.equal(c.nickname.value, '새 요청 초안');
	c.writes[1].resolve({ ...accountA, nickname: '새 요청 초안' }); await current;
	assert.equal(c.nicknameSaved.value, true);
});

for (const boundary of ['ABA', 'same-id authentication', 'getter only']) {
	for (const fails of [false, true]) {
		test(`withdrawal ${boundary} drops local completion while retaining existing store semantics (${fails})`, async t => {
			const c = await actualPage(t), request = c.confirmWithdraw();
			if (boundary === 'ABA') { await c.changeAccount({ ...accountB }); await c.changeAccount({ ...accountA }); }
			if (boundary === 'same-id authentication') { await c.changeAccount({ ...accountA }); }
			if (boundary === 'getter only') { c.advance(); }
			c.nickname.value = '현재 초안';
			fails ? c.writes[0].reject(new Error('이전 오류')) : c.writes[0].resolve(); await request;
			if (boundary === 'getter only' && !fails) { await c.atRoute('member-login'); }
			else { assert.equal(c.router.currentRoute.value.name, 'member-settings'); assert.equal(c.nickname.value, '현재 초안'); }
			assert.equal(c.notices.size, 0); assert.equal(c.activeObservers(), 0); assert.equal(c.activeIdentityObservers(), 0);
		});
	}
}
