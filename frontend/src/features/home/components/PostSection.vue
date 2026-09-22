<script setup>
import { computed } from 'vue';
import { RouterLink } from 'vue-router';

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  posts: {
    type: Array,
    required: true,
  },
  /**
   * 이 묶음을 고른 순서. 목록 화면의 sort 값과 같은 말을 쓴다.
   *
   * "전체 보기" 가 이걸 그대로 들고 가야 한다. 안 들고 가면 인기글에서 눌렀는데
   * 최신순 목록이 열려서, 방금 보던 넉 장이 첫 화면에 없다.
   */
  sort: {
    type: String,
    default: 'latest',
    validator: (value) => value === 'latest' || value === 'popular',
  },
});

// 최신순은 목록의 기본값이라 주소에서 뺀다. /posts?sort=latest 보다 /posts 가 낫다
const moreQuery = computed(() => (props.sort === 'popular' ? { sort: 'popular' } : {}));
</script>

<template>
  <section class="home-section" :aria-labelledby="`${title}-title`">
    <div class="home-section__header">
      <h2 :id="`${title}-title`" class="home-section__title">{{ title }}</h2>
      <RouterLink class="home-section__more" :to="{ name: 'post-list', query: moreQuery }">
        전체 보기
      </RouterLink>
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
