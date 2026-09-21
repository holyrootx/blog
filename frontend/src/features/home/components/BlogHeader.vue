<script setup>
import { RouterLink } from 'vue-router';

import MemberMenu from '../../member/components/MemberMenu.vue';
import NotificationBell from '../../member/components/NotificationBell.vue';

defineProps({
  title: {
    type: String,
    required: true,
  },
});
</script>

<template>
  <header class="blog-header">
    <!-- RouterLink 다. a 태그로 두면 눌렀을 때 화면 전체가 다시 뜬다 -->
    <RouterLink class="blog-header__brand" :to="{ name: 'home' }">{{ title }}</RouterLink>

    <nav class="blog-header__actions" aria-label="블로그 도구">
      <!-- TODO 검색·메뉴는 아직 동작하지 않는다. 무엇을 담을지 정한 뒤 붙이거나 지운다 -->
      <button class="blog-header__button" type="button" aria-label="검색">검색</button>
      <button class="blog-header__button" type="button" aria-label="메뉴">메뉴</button>

      <NotificationBell />
      <MemberMenu />
    </nav>
  </header>
</template>
