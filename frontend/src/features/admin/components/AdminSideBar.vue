<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAdminSidebarMenus } from '../data/adminSidebarMenuStore';
import { signOutAdmin, useAdminAuth } from '../data/adminAuthStore';

const route = useRoute();
const router = useRouter();
const { menus } = useAdminSidebarMenus();
const { admin } = useAdminAuth();

const signingOut = ref(false);

async function signOut() {
  if (signingOut.value) {
    return;
  }

  signingOut.value = true;

  try {
    await signOutAdmin();
    await router.replace({ name: 'admin-login' });
  } finally {
    signingOut.value = false;
  }
}

function isActive(menu) {
  return menu.routePath === route.path || menu.routeName === route.name;
}

function isGroup(menu) {
  return menu.menuType === 'GROUP';
}
</script>

<template>
  <aside class="admin-sidebar" aria-label="관리자 메뉴">
    <RouterLink class="admin-sidebar__brand" :to="{ name: 'home' }">
      <strong>정성주의 기록</strong>
      <small>ADMIN</small>
    </RouterLink>

    <nav class="admin-sidebar__nav">
      <template v-for="menu in menus" :key="menu.id">
        <section v-if="isGroup(menu)" class="admin-sidebar__section">
          <span class="admin-sidebar__group">{{ menu.menuName }}</span>

          <template v-for="item in menu.items" :key="item.id">
            <RouterLink
              v-if="item.routePath"
              class="admin-sidebar__item"
              :class="{ 'admin-sidebar__item--active': isActive(item) }"
              :to="item.routePath"
            >
              <span>
                <strong>{{ item.menuName }}</strong>
                <small>{{ item.menuDescription }}</small>
              </span>
            </RouterLink>
            <button
              v-else
              class="admin-sidebar__item admin-sidebar__item--pending"
              type="button"
              disabled
            >
              <span>
                <strong>{{ item.menuName }}</strong>
                <small>{{ item.menuDescription }}</small>
              </span>
              <em>준비중</em>
            </button>
          </template>
        </section>

        <RouterLink
          v-else-if="menu.routePath"
          class="admin-sidebar__item"
          :class="{ 'admin-sidebar__item--active': isActive(menu) }"
          :to="menu.routePath"
        >
          <span>
            <strong>{{ menu.menuName }}</strong>
            <small>{{ menu.menuDescription }}</small>
          </span>
        </RouterLink>
        <button
          v-else
          class="admin-sidebar__item admin-sidebar__item--pending"
          type="button"
          disabled
        >
          <span>
            <strong>{{ menu.menuName }}</strong>
            <small>{{ menu.menuDescription }}</small>
          </span>
          <em>준비중</em>
        </button>
      </template>
    </nav>

    <button
      class="admin-sidebar__footer"
      type="button"
      :disabled="signingOut"
      @click="signOut"
    >
      <span class="admin-sidebar__avatar" aria-hidden="true"></span>
      <span>
        <strong>{{ admin?.username || '관리자' }}</strong>
        <small>{{ signingOut ? '로그아웃 중…' : '로그아웃' }}</small>
      </span>
    </button>
  </aside>
</template>
