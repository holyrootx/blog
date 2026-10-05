<script setup>
import { computed, onMounted } from 'vue';
import { RouterLink, RouterView } from 'vue-router';

import BlogHeader from '../features/home/components/BlogHeader.vue';
import { blogProfile, refreshBlogProfile } from '../features/home/data/blogProfileStore';

/**
 * 공개 화면의 껍데기. 헤더와 푸터를 여기서 한 번만 그린다.
 *
 * 전에는 대문·목록·글 상세가 각자 헤더를 그려서, 화면을 옮길 때마다 헤더가 새로 만들어졌다.
 * 로그인·설정·약관·404 화면에는 아예 없어서 알림과 메뉴를 쓸 수 없었다.
 */
const SITE_TITLE = 'JSJ.log';

const title = computed(() => blogProfile.value?.name || SITE_TITLE);

onMounted(refreshBlogProfile);
</script>

<template>
  <div class="public-shell">
    <BlogHeader :title="title" />
    <RouterView />
    <footer class="public-footer">
      <RouterLink to="/privacy">개인정보처리방침</RouterLink>
      <RouterLink to="/terms">서비스 이용약관</RouterLink>
    </footer>
  </div>
</template>
