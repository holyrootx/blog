<script setup>
import { RouterLink } from 'vue-router';

import MemberMenu from '../../member/components/MemberMenu.vue';
import NotificationBell from '../../member/components/NotificationBell.vue';

defineProps({
  title: {
    type: String,
    required: true,
  },
  categories: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <header class="post-header">
    <!-- RouterLink 다. a 태그로 두면 눌렀을 때 화면 전체가 다시 뜬다 -->
    <RouterLink class="post-header__brand" :to="{ name: 'home' }">{{ title }}</RouterLink>

    <nav class="post-header__nav" aria-label="카테고리">
      <!-- TODO 카테고리별 목록 화면이 없어서 아직 갈 곳이 없다 (docs/2026-09-21-public-screen-todo.md) -->
      <a v-for="category in categories" :key="category.id" class="post-header__link" href="#">
        {{ category.name }}
      </a>

      <span class="post-header__divider"></span>

      <!-- TODO 검색·메뉴는 아직 동작하지 않는다 -->
      <button class="post-header__icon" type="button" aria-label="검색">
        <span class="post-header__search"></span>
      </button>
      <button class="post-header__icon" type="button" aria-label="메뉴">
        <span class="post-header__menu"></span>
      </button>

      <NotificationBell />
      <MemberMenu />
    </nav>
  </header>
</template>
