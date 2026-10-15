import { ref } from 'vue';

import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notificationApi';

// 최근 30개 목록과 전체 미확인 수는 서로 다른 조회 결과다.
const unreadCount = ref(0);
const notifications = ref([]);
const loading = ref(false);
const loaded = ref(false);
const loadError = ref('');
const countError = ref('');
const readError = ref('');
const reading = ref(false);
let sessionGeneration = 0;
let listRequest = 0;
let countRequest = 0;
let readRevision = 0;
const pendingReads = new Map();

export function useNotifications() {
  return { unreadCount, notifications, loading, loaded, loadError, countError, readError, reading };
}

export async function refreshUnreadCount() {
  // 읽음 요청 도중의 집계는 곧 낡는다. 마지막 읽음 요청이 끝난 뒤 다시 확인한다.
  if (pendingReads.size) return;
  const generation = sessionGeneration;
  const request = ++countRequest;
  const revision = readRevision;
  const current = () => generation === sessionGeneration && request === countRequest && revision === readRevision;
  try {
    const count = await getUnreadCount();
    if (!current()) return;
    unreadCount.value = count;
    countError.value = '';
  } catch {
    if (current()) countError.value = '미확인 알림 수를 갱신하지 못했습니다.';
  }
}

export async function loadNotifications() {
  const generation = sessionGeneration;
  const request = ++listRequest;
  const revision = readRevision;
  const current = () => generation === sessionGeneration && request === listRequest;
  loading.value = true;
  loadError.value = '';
  try {
    const items = await getNotifications();
    if (!current() || revision !== readRevision || pendingReads.size) return;
    notifications.value = items;
    loaded.value = true;
  } catch {
    if (current() && revision === readRevision) {
      loadError.value = loaded.value
        ? '알림을 새로 불러오지 못했습니다. 이전에 불러온 목록을 표시합니다.'
        : '알림을 불러오지 못했습니다.';
    }
  } finally {
    if (current()) loading.value = false;
  }
}

export function readNotification(notificationId) {
  return markRead(notificationId, () => markNotificationRead(notificationId), () => {
    const target = notifications.value.find((item) => item.id === notificationId);
    if (target?.unread) {
      target.unread = false;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
  });
}

export function readAllNotifications() {
  if (pendingReads.size) return Promise.resolve(null);
  return markRead('all', markAllNotificationsRead, () => {
    notifications.value = notifications.value.map((item) => ({ ...item, unread: false }));
    unreadCount.value = 0;
  });
}

function markRead(key, request, apply) {
  if (pendingReads.has('all')) return pendingReads.get('all');
  if (pendingReads.has(key)) return pendingReads.get(key);
  const generation = sessionGeneration;
  readRevision += 1;
  reading.value = true;
  readError.value = '';

  // 실패한 쓰기를 성공한 것처럼 표시하지 않는다. 화면 이동은 호출 쪽에서 즉시 진행한다.
  const operation = Promise.resolve().then(() => {
    if (generation === sessionGeneration) return request();
  }).then(() => {
    if (generation !== sessionGeneration) return null;
    apply();
    return true;
  }).catch(() => {
    if (generation !== sessionGeneration) return null;
    readError.value = '읽음 처리를 완료하지 못했습니다. 다시 시도해 주세요.';
    return false;
  }).finally(() => {
    if (generation !== sessionGeneration) return;
    pendingReads.delete(key);
    reading.value = pendingReads.size > 0;
    if (!reading.value) {
      // 응답을 잃은 쓰기와 다른 화면에서 읽은 알림까지 서버 값으로 맞춘다.
      void refreshUnreadCount();
      void loadNotifications();
    }
  });
  pendingReads.set(key, operation);
  return operation;
}

export function clearNotifications() {
  sessionGeneration += 1;
  pendingReads.clear();
  unreadCount.value = 0;
  notifications.value = [];
  loading.value = false;
  loaded.value = false;
  loadError.value = '';
  countError.value = '';
  readError.value = '';
  reading.value = false;
}
