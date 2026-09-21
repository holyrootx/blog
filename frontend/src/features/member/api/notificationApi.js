import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';

const NOTIFICATION_API_BASE = '/api/v1/auth/me/notifications';

/**
 * 알림 목록.
 *
 * 문구는 서버가 만들지 않는다. {@code type} 을 보고 화면이 말을 고른다 —
 * 서버가 문구를 정하면 말투 하나 고치는 데 배포가 필요해진다.
 */
export async function getNotifications() {
  const notifications = await getApiData(NOTIFICATION_API_BASE);

  return Array.isArray(notifications) ? notifications.map(toNotification) : [];
}

/** 종에 붙는 숫자. 목록보다 훨씬 자주 부른다 */
export async function getUnreadCount() {
  const count = await getApiData(`${NOTIFICATION_API_BASE}/unread-count`);

  return typeof count === 'number' ? count : 0;
}

export function markNotificationRead(notificationId) {
  return sendApiData(`${NOTIFICATION_API_BASE}/${notificationId}/read`, { method: 'PUT' });
}

export function markAllNotificationsRead() {
  return sendApiData(`${NOTIFICATION_API_BASE}/read`, { method: 'PUT' });
}

function toNotification(notification) {
  return {
    id: notification?.id ?? null,
    type: notification?.type ?? '',
    actorNickname: notification?.actorNickname ?? '',
    postId: notification?.postId ?? null,
    postTitle: notification?.postTitle ?? '',
    commentId: notification?.commentId ?? null,
    preview: notification?.preview ?? '',
    createdAt: notification?.createdAt ?? '',
    unread: Boolean(notification?.unread),
  };
}
