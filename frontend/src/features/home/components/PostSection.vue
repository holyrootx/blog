<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  posts: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section class="home-section" :aria-labelledby="`${title}-title`">
    <div class="home-section__header">
      <h2 :id="`${title}-title`" class="home-section__title">{{ title }}</h2>
      <a class="home-section__more" href="/posts">전체 보기</a>
    </div>

    <div class="post-grid">
      <RouterLink
        v-for="post in posts"
        :key="post.id"
        class="post-card"
        :to="{ name: 'post-detail', params: { id: post.id } }"
      >
        <img class="post-card__image" :src="post.imageUrl" :alt="post.title" />
        <div class="post-card__body">
          <span class="post-card__category">{{ post.category }}</span>
          <h3 class="post-card__title">{{ post.title }}</h3>
          <div class="post-card__meta">
            <time>{{ post.publishedAt }}</time>
            <span>조회 {{ post.views }}</span>
          </div>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
