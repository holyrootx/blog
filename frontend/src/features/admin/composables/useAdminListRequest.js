import { onScopeDispose, ref } from 'vue';

/** 목록 조회의 성공·실패·로딩 상태는 마지막으로 시작한 요청만 반영한다. */
export function useAdminListRequest({ request, apply, errorMessage, onError }) {
	const loading = ref(false);
	const loadError = ref('');
	let latestRequest = 0;
	let disposed = false;

	onScopeDispose(() => {
		disposed = true;
		latestRequest += 1;
		loading.value = false;
	});

	async function load(params) {
		if (disposed) {
			return;
		}

		const currentRequest = ++latestRequest;
		loading.value = true;
		loadError.value = '';

		try {
			const result = await request(params);
			if (currentRequest !== latestRequest) {
				return;
			}
			await apply(result, params);
		} catch (error) {
			if (currentRequest !== latestRequest) {
				return;
			}
			loadError.value = typeof errorMessage === 'function' ? errorMessage(error) : errorMessage;
			onError?.(error);
		} finally {
			if (currentRequest === latestRequest) {
				loading.value = false;
			}
		}
	}

	return { loading, loadError, load };
}
