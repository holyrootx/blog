import { ref } from 'vue';

/**
 * 화면 전체가 같이 쓰는 결과 알림.
 *
 * <p>화면마다 따로 만들지 않고 한 곳에 모은다. 각자 만들면 새 화면을 붙일 때마다
 * 빠뜨리는 곳이 생긴다.</p>
 *
 * <p><b>성공은 잠깐 떴다 사라지고 실패는 남는다.</b> 성공은 놓쳐도 화면 상태로 확인되지만
 * (뱃지가 바뀌었다, 목록에서 빠졌다), 실패를 놓치면 저장된 줄 알고 나가게 된다.
 * 저절로 사라지는 알림은 눈이 불편한 사람에게는 없는 것과 같아서, 실패에는 닫는
 * 단추를 달고 부르는 쪽에서 인라인 문구도 같이 남긴다.</p>
 *
 * <p><b>결과가 화면에 바로 보이면 부르지 않는다.</b> 댓글을 쓰면 댓글이 목록에 붙고,
 * 좋아요를 누르면 숫자가 오른다 — 그게 이미 알림이다. 거기에 토스트까지 띄우면
 * 글 하나 쓸 때마다 화면이 번쩍인다. 부르는 자리는 <b>화면에 티가 안 나는 일</b>
 * (닉네임 변경, 신고 접수) 과 <b>모든 실패</b> 다.</p>
 */
const SUCCESS_DURATION = 3000;

const toasts = ref([]);

let sequence = 0;

export function useToasts() {
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
