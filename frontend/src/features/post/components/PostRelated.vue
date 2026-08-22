<script setup>
defineProps({
  posts: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section v-if="posts.length > 0" class="post-related" aria-labelledby="post-related-title">
    <div class="post-related__header">
      <h2 id="post-related-title" class="post-related__title">함께 읽으면 좋은 글</h2>
      <a class="post-related__more" href="/posts">전체 보기 →</a>
    </div>

    <div class="post-related__grid">
      <RouterLink
        v-for="post in posts"
        :key="post.id"
        class="post-related__card post-card"
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
