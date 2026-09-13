<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { useAdminSidebarMenus, findMenuGroupName } from '../data/adminSidebarMenuStore';

// 관리자 화면의 제목 줄.
// 긴 안내 문장을 본문에 깔지 않고, "어느 메뉴 아래인지"만 제목 위에 작게 둔다.
// 위치 표시는 눌러서 이동하는 링크가 아니다 (그룹은 갈 수 있는 화면이 아니므로)
const props = defineProps({
  title: {
    type: String,
    default: '',
  },
  // 메뉴 API 에서 못 찾을 때 쓸 그룹 이름 (숨긴 메뉴 등)
  group: {
    type: String,
    default: '',
  },
});

const route = useRoute();
const { menus } = useAdminSidebarMenus();

const pageTitle = computed(() => props.title || route.meta?.title || '');

// 메뉴 API 를 먼저 보고, 없으면 라우트에 적어둔 이름으로 떨어진다
const groupName = computed(() => {
  void menus.value;

  return findMenuGroupName(route.path)
    || props.group
    || route.meta?.group
    || '';
});
</script>

<template>
  <header class="admin-page-header">
    <div class="admin-page-header__main">
      <span v-if="groupName" class="admin-page-header__location">{{ groupName }}</span>
      <h1 class="admin-page-header__title">{{ pageTitle }}</h1>

      <!-- 화면을 설명하는 문장이 아니라, 데이터가 실린 한 줄에만 쓴다 -->
      <p v-if="$slots.meta" class="admin-page-header__meta">
        <slot name="meta" />
      </p>
    </div>

    <div v-if="$slots.actions" class="admin-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
