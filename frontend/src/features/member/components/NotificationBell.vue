<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { formatExactTime, formatRelativeTime } from '../../../shared/time/relativeTime';

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

/**
 * 알림 문구.
 *
 * <p>서버는 타입만 주고 말은 여기서 고른다. 문구를 서버가 정하면 말투를 다듬을 때마다
 * 배포해야 한다.</p>
 *
 * <p>{@code actorNickname} 은 타입마다 누구인지가 다르다. 답글·글댓글은 상대,
 * 신고는 신고당한 댓글을 쓴 사람이고, <b>가려짐은 받는 사람 자신</b>이라 쓰지 않는다 —
 * "정성주 님이 내 댓글을 가렸습니다" 가 되어 버린다.</p>
 */
function messageOf(notification) {
  if (notification.type === 'REPLY') {
    return `${notification.actorNickname} 님이 내 댓글에 답글을 남겼습니다`;
  }

  if (notification.type === 'POST_COMMENT') {
    return `${notification.actorNickname} 님이 내 글에 댓글을 남겼습니다`;
  }

  if (notification.type === 'REPORT_RECEIVED') {
    return `${notification.actorNickname} 님의 댓글이 신고되었습니다`;
  }

  // 사유는 서버가 보내지 않는다. 어떤 댓글에 누가 왜 신고했는지가 좁혀지기 때문이다
  return '내 댓글이 운영 기준에 따라 가려졌습니다';
}

async function toggle() {
  open.value = !open.value;

  if (open.value) {
    await loadNotifications();
  }
}

/**
 * 알림을 누르면 가는 곳.
 *
 * <p>타입마다 다르다. 답글·글댓글은 그 댓글 자리로 가면 되지만, 나머지 둘은 그 자리에
 * 볼 것이 없다.</p>
 *
 * <ul>
 *   <li>신고 — 공개 화면에는 신고 건수도 사유도 없다. 판단에 필요한 것은 관리자 쪽에 있다</li>
 *   <li>가려짐 — 가려진 댓글은 답글이 없으면 공개 화면에 아예 그려지지 않는다.
 *       글로 보내면 빈 화면으로 떨어진다. 내 댓글 목록에는 가려진 것도 남아 있다</li>
 * </ul>
 */
async function go(notification) {
  open.value = false;

  await readNotification(notification.id);

  if (notification.type === 'REPORT_RECEIVED') {
    await router.push({ name: 'admin-comments', query: { report: notification.commentId } });
    return;
  }

  if (notification.type === 'COMMENT_HIDDEN') {
    await router.push({ name: 'member-settings' });
    return;
  }

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
          <!--
            정확한 시각은 브라우저 기본 툴팁(title) 대신 직접 그린다. 기본 툴팁은
            환경에 따라 아예 안 뜨고, 뜨더라도 1초를 멈춰 있어야 한다.

            마우스가 없는 기기에서는 툴팁 자리가 없으므로 시각을 아예 같이 적는다 —
            그쪽은 hover 라는 것이 존재하지 않는다.
          -->
          <button
            class="notification-bell__item"
            :class="{ 'notification-bell__item--unread': notification.unread }"
            type="button"
            role="menuitem"
            :data-exact-time="formatExactTime(notification.createdAt)"
            @click="go(notification)"
          >
            <span class="notification-bell__message">{{ messageOf(notification) }}</span>
            <span class="notification-bell__preview">{{ notification.preview }}</span>
            <span class="notification-bell__foot">
              <span class="notification-bell__post">{{ notification.postTitle }}</span>
              <!-- 상대 시각. 알림에서 궁금한 것은 몇 시 몇 분인가가 아니라 새 것인가다 -->
              <time class="notification-bell__time" :datetime="notification.createdAt">
                {{ formatRelativeTime(notification.createdAt) }}
                <span class="notification-bell__exact">
                  · {{ formatExactTime(notification.createdAt) }}
                </span>
              </time>
            </span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
