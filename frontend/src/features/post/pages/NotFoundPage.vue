<script setup>
import { RouterLink } from 'vue-router';

/**
 * 없는 주소로 들어왔을 때.
 *
 * <p>이 화면이 없으면 라우터가 아무것도 그리지 않아 <b>흰 화면</b>이 뜬다. 오류라는 것조차
 * 안 보여서 사이트가 죽은 것처럼 보인다.</p>
 *
 * <p>지운 글, 잘못 친 주소, 오래된 링크가 여기로 온다. 셋 다 돌아갈 곳을 알려 주면 된다.</p>
 */
</script>

<template>
  <main class="not-found">
    <p class="not-found__code">404</p>
    <h1 class="not-found__title">찾는 글이 없습니다</h1>
    <p class="not-found__text">
      주소가 바뀌었거나 글이 내려갔을 수 있습니다.
    </p>

    <div class="not-found__actions">
      <RouterLink class="post-button" :to="{ name: 'home' }">블로그 홈</RouterLink>
      <RouterLink class="post-button post-button--accent" :to="{ name: 'post-list' }">
        전체 글 보기
      </RouterLink>
    </div>
  </main>
</template>
