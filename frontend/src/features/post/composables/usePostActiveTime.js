import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { getAuthenticationGeneration } from '../../../shared/api/blogApiClient';
import { refreshCsrfToken } from '../../../shared/api/csrfApi';
import { savePostActiveTime, startPostActiveTime } from '../api/postApi';
import { ACTIVE_TIME_LIMIT_MS, createActiveTimeClock } from './activeTimeClock';

const SEND_INTERVAL_MS = 15_000;
const INPUT_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'scroll', 'touchstart'];

/** 공개 글의 화면 활성 시간 추정. 측정 실패는 글 조회와 분리한다. */
export function usePostActiveTime({ postId, ready, memberId, isAdmin }) {
	const mounted = ref(false);
	let current = null;
	let timer = null;

	watch(() => [postId.value, ready.value, memberId.value, isAdmin.value, mounted.value], () => {
		stop();
		if (!mounted.value || !ready.value || !postId.value || isAdmin.value) {
			return;
		}
		current = { postId: postId.value, memberId: memberId.value, auth: getAuthenticationGeneration(),
			clock: createActiveTimeClock(), token: null, starting: false, attempts: 0,
			acknowledged: -1, pending: 0, disabled: false };
		begin(current);
	}, { immediate: true, flush: 'sync' });

	onMounted(() => {
		window.addEventListener('focus', resume);
		window.addEventListener('blur', pause);
		window.addEventListener('pagehide', pause);
		window.addEventListener('pageshow', resume);
		document.addEventListener('visibilitychange', visibilityChanged);
		INPUT_EVENTS.forEach(event => window.addEventListener(event, interact, { passive: true }));
		timer = setInterval(() => {
			if (!current) {
				return;
			}
			if (!current.token) {
				begin(current);
			} else {
				flush(current);
			}
		}, SEND_INTERVAL_MS);
		mounted.value = true;
	});

	onBeforeUnmount(() => {
		stop();
		clearInterval(timer);
		window.removeEventListener('focus', resume);
		window.removeEventListener('blur', pause);
		window.removeEventListener('pagehide', pause);
		window.removeEventListener('pageshow', resume);
		document.removeEventListener('visibilitychange', visibilityChanged);
		INPUT_EVENTS.forEach(event => window.removeEventListener(event, interact));
	});

	function sameOwner(context) {
		return context.memberId === memberId.value && context.auth === getAuthenticationGeneration();
	}

	async function begin(context) {
		if (context.starting || context.disabled || context.attempts >= 3 || !sameOwner(context)) {
			return;
		}
		context.starting = true;
		context.attempts += 1;
		try {
			// 비회원도 쓰기 요청은 동일한 CSRF 검사를 받는다.
			await refreshCsrfToken();
			if (current !== context || !sameOwner(context)) {
				return;
			}
			const measurement = await startPostActiveTime(context.postId);
			if (current !== context || !sameOwner(context)) {
				return;
			}
			context.token = measurement?.token ?? null;
			context.disabled = !context.token;
			resume();
		} catch {
			// 다음 주기에 재시도한다. 통계 오류를 독자에게 띄우지 않는다.
		} finally {
			context.starting = false;
		}
	}

	function isForeground() {
		return document.visibilityState === 'visible' && document.hasFocus();
	}

	function resume() {
		if (current?.token && isForeground()) {
			current.clock.resume();
		}
	}

	function pause() {
		if (current) {
			current.clock.pause();
			flush(current, true);
		}
	}

	function visibilityChanged() {
		if (isForeground()) {
			resume();
		} else {
			pause();
		}
	}

	function interact() {
		if (current?.token && isForeground()) {
			current.clock.interact();
		}
	}

	function flush(context, keepalive = false) {
		if (!context.token || context.disabled || !sameOwner(context) || !context.clock.hasMeasured()
				|| (context.pending && !keepalive)) {
			return;
		}
		const activeTimeMillis = context.clock.sample();
		if (activeTimeMillis <= context.acknowledged) {
			return;
		}
		context.pending += 1;
		savePostActiveTime(context.postId, { token: context.token, activeTimeMillis }, { keepalive })
			.then(() => {
				context.acknowledged = Math.max(context.acknowledged, activeTimeMillis);
				if (activeTimeMillis >= ACTIVE_TIME_LIMIT_MS) {
					context.disabled = true;
				}
			})
			.catch(() => { /* 다음 누적값에 미전송분을 포함한다. */ })
			.finally(() => { context.pending -= 1; });
	}

	function stop() {
		if (current) {
			current.clock.pause();
			flush(current, true);
			current = null;
		}
	}
}
