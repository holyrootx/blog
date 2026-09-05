<script setup>
import { computed, ref } from 'vue';

import AdminSideBar from '../components/AdminSideBar.vue';
import {
  flattenAdminMenus,
  getVisibleAdminMenus,
  sortAdminMenus,
} from '../data/adminMenus';

const menus = ref([]);

const visibleMenus = computed(() => getVisibleAdminMenus(menus.value));
const visibleItemCount = computed(() => flattenAdminMenus(visibleMenus.value)
  .filter((menu) => menu.menuType !== 'GROUP').length);
const sortedMenus = computed(() => flattenAdminMenus(menus.value));

</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <AdminSideBar />

      <main class="admin-main">
      <header class="admin-dashboard__header">
        <div>
          <h1 class="admin-dashboard__title">메뉴 관리</h1>
          <p class="admin-dashboard__intro">
            관리자 사이드바에 노출할 메뉴를 관리합니다. 지금은 연결될 API 기준으로 화면을 먼저 잡아둡니다.
          </p>
        </div>

        <div class="admin-dashboard__actions">
          <button class="admin-button admin-button--ghost" type="button" disabled>
            순서 저장
          </button>
          <button class="admin-button admin-button--solid" type="button" disabled>
            메뉴 추가
          </button>
        </div>
      </header>

      <section class="admin-notice" aria-live="polite">
        <strong>현재 사이드바에는 노출 메뉴 {{ visibleItemCount }}개만 표시됩니다.</strong>
        <span>
          메뉴 관리 API 연결 전입니다.
        </span>
      </section>

      <section class="admin-panel">
        <div class="admin-panel__header">
          <div>
            <h2>사이드바 메뉴</h2>
            <p>활성 상태인 메뉴만 왼쪽 사이드바에 표시됩니다.</p>
          </div>
        </div>

        <div class="admin-menu-list">
          <article
            v-for="menu in sortedMenus"
            :key="menu.id"
            class="admin-menu-item"
            :class="{ 'admin-menu-item--disabled': !menu.visible }"
          >
            <div class="admin-menu-item__order">
              {{ menu.sortOrder }}
            </div>

            <div class="admin-menu-item__body">
              <div class="admin-menu-item__title-row">
                <strong>{{ menu.menuName }}</strong>
                <span
                  class="admin-menu-item__badge"
                  :class="{ 'admin-menu-item__badge--disabled': !menu.visible }"
                >
                  {{ menu.visible ? '노출' : '숨김' }}
                </span>
              </div>
              <p>{{ menu.menuDescription }}</p>
              <dl class="admin-menu-item__meta">
                <div>
                  <dt>path</dt>
                  <dd>{{ menu.routePath || '-' }}</dd>
                </div>
                <div>
                  <dt>type</dt>
                  <dd>{{ menu.menuType }}</dd>
                </div>
                <div>
                  <dt>source</dt>
                  <dd>{{ menu.system ? 'system' : 'custom' }}</dd>
                </div>
              </dl>
            </div>

            <button class="admin-menu-item__toggle" type="button" disabled>
              {{ menu.visible ? '끄기' : '켜기' }}
            </button>
          </article>
        </div>
      </section>
      </main>
    </div>
  </div>
</template>
