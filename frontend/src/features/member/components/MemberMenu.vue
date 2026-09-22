<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import { signOutMember, useMemberAuth } from '../data/memberAuthStore';
import { rememberReturnPath } from '../data/memberReturnPath';

/**
 * 헤더의 회원 영역. 대문과 글보기가 같이 쓴다.
 *
 * <p>상태에 따라 셋으로 갈린다.</p>
 *
 * <pre>
 * 확인 중     아무것도 안 그림
 * 비로그인    [로그인]
 * 로그인      (아바타) 닉네임 ▾
 * </pre>
 *
 * <p>확인 중에 비워 두는 이유는 깜빡임 때문이다. 앱이 뜰 때 {@code /me} 를 기다리지 않고
 * 부르는데, 그 사이를 "비로그인" 으로 그리면 로그인한 사람에게 [로그인] 이 한 번 보였다가
 * 닉네임으로 바뀐다.</p>
 */
const route = useRoute();
const router = useRouter();
const { member, sessionResolved, isSignedIn } = useMemberAuth();

const open = ref(false);
const root = ref(null);

const isAdmin = computed(() => member.value?.role === 'ROLE_ADMIN');

const initial = computed(() => Array.from(member.value?.nickname ?? '')[0] ?? '');

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
});

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutside);
  document.removeEventListener('keydown', closeOnEscape);
});

// 화면을 옮기면 닫는다. 열어 둔 채로 이동하면 새 화면 위에 그대로 떠 있다
watch(() => route.fullPath, () => {
  open.value = false;
});

function goToLogin() {
  // 로그인은 제공자 화면까지 다녀오는 길이라 라우터 상태가 남지 않는다.
  // 보던 자리를 적어 두지 않으면 돌아왔을 때 홈으로 떨어진다
  rememberReturnPath(route.fullPath);
  router.push({ name: 'member-login' });
}

async function signOut() {
  open.value = false;

  await signOutMember();

  // 로그인해야 볼 수 있는 화면에서 로그아웃했으면 그 자리에 남을 수 없다
  if (route.name === 'member-settings') {
    await router.replace({ name: 'home' });
  }
}
</script>

<template>
  <button
    v-if="sessionResolved && !isSignedIn"
    class="member-menu__signin"
    type="button"
    @click="goToLogin"
  >로그인</button>

  <div v-else-if="isSignedIn" ref="root" class="member-menu">
    <button
      class="member-menu__trigger"
      type="button"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="open = !open"
    >
      <img
        v-if="member.profileImageUrl"
        class="member-menu__avatar"
        :src="member.profileImageUrl"
        alt=""
        referrerpolicy="no-referrer"
      />
      <span v-else class="member-menu__avatar">{{ initial }}</span>
      <span class="member-menu__nickname">{{ member.nickname }}</span>
    </button>

    <div v-if="open" class="member-menu__dropdown" role="menu">
      <RouterLink class="member-menu__item" role="menuitem" :to="{ name: 'member-settings' }">
        내 설정
      </RouterLink>
      <RouterLink
        v-if="isAdmin"
        class="member-menu__item"
        role="menuitem"
        :to="{ name: 'admin-dashboard' }"
      >
        관리자
      </RouterLink>
      <button class="member-menu__item" type="button" role="menuitem" @click="signOut">
        로그아웃
      </button>
    </div>
  </div>
</template>
