import { ref } from 'vue';

/**
 * 관리자 화면의 결과 알림.
 *
 * 화면마다 따로 만들지 않고 한 곳에 모은다. 각자 만들면 새 화면을 붙일 때마다
 * 빠뜨리는 곳이 생기고, 실제로 지금 12곳 중 한 곳에만 있었다.
 *
 * 성공은 잠깐 떴다 사라지고 실패는 남는다. 성공은 놓쳐도 화면 상태로 확인되지만
 * (뱃지가 바뀌었다, 목록에서 빠졌다), 실패를 놓치면 저장된 줄 알고 나가게 된다.
 */
const SUCCESS_DURATION = 3000;

const toasts = ref([]);

let sequence = 0;

export function useAdminToasts() {
  return { toasts };
}

export function notifySuccess(message) {
  return push(message, 'success');
}

export function notifyError(message) {
  return push(message, 'error');
}

export function dismissToast(id) {
  toasts.value = toasts.value.filter((toast) => toast.id !== id);
}

function push(message, kind) {
  sequence += 1;

  const toast = { id: sequence, message, kind };
  toasts.value = [...toasts.value, toast];

  if (kind === 'success') {
    setTimeout(() => dismissToast(toast.id), SUCCESS_DURATION);
  }

  return toast.id;
}
