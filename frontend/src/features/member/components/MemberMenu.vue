<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import { signOutMember, useMemberAuth } from '../data/memberAuthStore';

/**
 * 헤더의 회원 영역. 대문과 글보기가 같이 쓴다.
 *
 * <p><b>로그인하지 않았으면 아무것도 그리지 않는다.</b> 이 블로그에서 로그인의 쓸모는
 * 댓글뿐이고, 그 안내는 댓글칸이 제 자리에서 하고 있다. 헤더에 로그인 버튼을 두면
 * 눌러도 늘어나는 것이 없어 "왜 가입을 시키지" 라는 인상만 남는다.</p>
 *
 * <p>덤으로 깜빡임도 없어진다. 앱이 뜰 때 {@code /me} 를 기다리지 않고 부르는데,
 * 로그인 전 모습과 확인 중 모습이 똑같아서 응답이 와도 화면이 바뀌지 않는다.</p>
 *
 * <p>반대로 로그인했으면 반드시 보여야 한다. 지금까지 로그아웃이 댓글칸에만 있어서
 * 대문에서는 나갈 방법이 없었다.</p>
 */
const route = useRoute();
const router = useRouter();
const { member, isSignedIn } = useMemberAuth();

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
  <div v-if="isSignedIn" ref="root" class="member-menu">
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
