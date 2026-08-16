<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
});
</script>

<template>
  <header class="blog-header">
    <a class="blog-header__brand" href="/">{{ title }}</a>
    <nav class="blog-header__actions" aria-label="블로그 도구">
      <button class="blog-header__button" type="button" aria-label="검색">검색</button>
      <button class="blog-header__button" type="button" aria-label="메뉴">메뉴</button>
    </nav>
  </header>
</template>
