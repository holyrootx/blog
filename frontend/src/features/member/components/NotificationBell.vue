<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  loadNotifications,
  readAllNotifications,
  readNotification,
  refreshUnreadCount,
  useNotifications,
} from '../data/notificationStore';
import { useMemberAuth } from '../data/memberAuthStore';

/**
 * 헤더의 종.
 *
 * <p>로그인한 사람에게만 보인다. 회원 시스템을 만든 값을 회수하는 유일한 통로다 —
 * 댓글을 쓴 사람이 답글을 받아도 모르면 다시 오지 않는다.</p>
 *
 * <p>이메일은 통로가 될 수 없다. 최소수집으로 널을 허용해서 없는 회원이 있다.</p>
 */
const route = useRoute();
const router = useRouter();

const { isSignedIn } = useMemberAuth();
const { unreadCount, notifications, loading } = useNotifications();

const open = ref(false);
const root = ref(null);

const badge = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)));

const label = computed(() =>
  unreadCount.value > 0 ? `알림 ${unreadCount.value}개` : '알림');

function messageOf(notification) {
  return notification.type === 'REPLY'
    ? `${notification.actorNickname} 님이 내 댓글에 답글을 남겼습니다`
    : `${notification.actorNickname} 님이 내 글에 댓글을 남겼습니다`;
}

async function toggle() {
  open.value = !open.value;

  if (open.value) {
    await loadNotifications();
  }
}

async function go(notification) {
  open.value = false;

  await readNotification(notification.id);
  await router.push({
    name: 'post-detail',
    params: { id: notification.postId },
    hash: `#comment-${notification.commentId}`,
  });
}

function closeOnOutside(event) {
  if (open.value && root.value && !root.value.contains(event.target)) {
    open.value = false;
  }
}

function closeOnEscape(event) {
  if (event.key === 'Escape') {
    open.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', closeOnOutside);
  document.addEventListener('keydown', closeOnEscape);

  if (isSignedIn.value) {
    refreshUnreadCount();
  }
});

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutside);
  document.removeEventListener('keydown', closeOnEscape);
});

// 화면을 옮길 때마다 숫자만 다시 받는다. 폴링 대신 쓰는 갱신 시점이다
watch(() => route.fullPath, () => {
  open.value = false;

  if (isSignedIn.value) {
    refreshUnreadCount();
  }
});

// 로그인이 늦게 확인되는 경우가 있다. 그때도 한 번 받아 둔다
watch(isSignedIn, (signedIn) => {
  if (signedIn) {
    refreshUnreadCount();
  }
});
</script>

<template>
  <div v-if="isSignedIn" ref="root" class="notification-bell">
    <button
      class="notification-bell__trigger"
      type="button"
      :aria-label="label"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="toggle"
    >
      <span class="notification-bell__icon" aria-hidden="true">🔔</span>
      <span v-if="unreadCount > 0" class="notification-bell__badge">{{ badge }}</span>
    </button>

    <div v-if="open" class="notification-bell__dropdown" role="menu">
      <div class="notification-bell__head">
        <span class="notification-bell__title">알림</span>
        <button
          v-if="unreadCount > 0"
          class="notification-bell__read-all"
          type="button"
          @click="readAllNotifications"
        >모두 읽음</button>
      </div>

      <p v-if="loading" class="notification-bell__empty">불러오는 중…</p>
      <p v-else-if="notifications.length === 0" class="notification-bell__empty">
        아직 알림이 없습니다.
      </p>

      <ul v-else class="notification-bell__list">
        <li v-for="notification in notifications" :key="notification.id">
          <button
            class="notification-bell__item"
            :class="{ 'notification-bell__item--unread': notification.unread }"
            type="button"
            role="menuitem"
            @click="go(notification)"
          >
            <span class="notification-bell__message">{{ messageOf(notification) }}</span>
            <span class="notification-bell__preview">{{ notification.preview }}</span>
            <span class="notification-bell__post">{{ notification.postTitle }}</span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
