<script setup>
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
    <a class="post-header__brand" href="/">{{ title }}</a>

    <nav class="post-header__nav" aria-label="카테고리">
      <a v-for="category in categories" :key="category.id" class="post-header__link" href="#">
        {{ category.name }}
      </a>

      <span class="post-header__divider"></span>

      <button class="post-header__icon" type="button" aria-label="검색">
        <span class="post-header__search"></span>
      </button>
      <button class="post-header__icon" type="button" aria-label="메뉴">
        <span class="post-header__menu"></span>
      </button>
    </nav>
  </header>
</template>
