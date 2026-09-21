import { ref } from 'vue';

import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notificationApi';

/**
 * 알림 상태.
 *
 * <p>실시간으로 밀어 받지 않는다. 앱이 뜰 때와 화면을 옮길 때 개수를 한 번씩 확인하고,
 * 목록은 종을 열었을 때만 가져온다. 답글은 몇 시간 늦게 봐도 아무 문제가 없고,
 * 폴링이나 SSE 는 이 규모에서 서버만 축낸다.</p>
 *
 * <p>개수와 목록을 따로 두는 이유는 부르는 빈도가 다르기 때문이다. 숫자는 자주,
 * 목록은 가끔이다.</p>
 */
const unreadCount = ref(0);
const notifications = ref([]);
const loading = ref(false);

export function useNotifications() {
  return { unreadCount, notifications, loading };
}

/**
 * 종에 붙는 숫자만 갱신한다.
 *
 * 로그인하지 않았으면 서버가 401 을 준다. 그건 오류가 아니라 정상이라 조용히 0 으로 둔다.
 */
export async function refreshUnreadCount() {
  try {
    unreadCount.value = await getUnreadCount();
  } catch {
    unreadCount.value = 0;
  }
}

export async function loadNotifications() {
  loading.value = true;

  try {
    notifications.value = await getNotifications();
    unreadCount.value = notifications.value.filter((item) => item.unread).length;
  } catch {
    notifications.value = [];
  } finally {
    loading.value = false;
  }
}

/**
 * 알림 하나를 읽음으로 바꾼다.
 *
 * 서버 응답을 기다리지 않고 화면부터 바꾼다 — 누르면 글로 이동하기 때문에, 기다리면
 * 화면이 넘어간 뒤에 숫자가 줄어 눈에 띄지 않는다. 실패해도 다음 갱신에서 제자리를 찾는다.
 */
export async function readNotification(notificationId) {
  const target = notifications.value.find((item) => item.id === notificationId);

  if (target?.unread) {
    target.unread = false;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }

  await markNotificationRead(notificationId).catch(() => null);
}

export async function readAllNotifications() {
  notifications.value = notifications.value.map((item) => ({ ...item, unread: false }));
  unreadCount.value = 0;

  await markAllNotificationsRead().catch(() => null);
}

export function clearNotifications() {
  unreadCount.value = 0;
  notifications.value = [];
}
