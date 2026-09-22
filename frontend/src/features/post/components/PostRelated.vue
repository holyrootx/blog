<script setup>
import { computed } from 'vue';
import { RouterLink } from 'vue-router';

const props = defineProps({
  posts: {
    type: Array,
    required: true,
  },
  /**
   * 여기 걸린 글들의 분류. "전체 보기" 가 그대로 들고 간다.
   *
   * 같은 분류의 글을 보여 주고서 전체 목록으로 보내면, 누른 사람이 기대한 "이런 글 더"
   * 가 아니라 아무 글이나 나온다. 대문 인기글의 전체 보기와 같은 규칙이다.
   */
  categoryId: {
    type: Number,
    default: null,
  },
});

const moreQuery = computed(() => (props.categoryId ? { category: String(props.categoryId) } : {}));
</script>

<template>
  <section v-if="posts.length > 0" class="post-related" aria-labelledby="post-related-title">
    <div class="post-related__header">
      <h2 id="post-related-title" class="post-related__title">함께 읽으면 좋은 글</h2>
      <RouterLink class="post-related__more" :to="{ name: 'post-list', query: moreQuery }">
        전체 보기 →
      </RouterLink>
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
