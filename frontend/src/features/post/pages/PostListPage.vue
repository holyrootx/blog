<script setup>
import { computed, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import BlogHeader from '../../home/components/BlogHeader.vue';
import { getCategories, getPosts } from '../api/postApi';
import { getBlogProfile } from '../../home/api/homeApi';

/**
 * 전체 글 목록.
 *
 * <p>대문의 "전체 보기" 와 상단 카테고리가 오는 자리다. 그전까지는 이 화면이 없어서
 * 두 링크가 모두 빈 화면으로 떨어졌다.</p>
 *
 * <p>조회조건을 주소에 둔다. 뒤로 가기로 보던 페이지로 돌아오고, 링크를 그대로 보낼 수 있다.
 * 화면 안 상태로 들고 있으면 둘 다 안 된다.</p>
 */
const route = useRoute();
const router = useRouter();

const SITE_TITLE = 'JSJ.log';
const PAGE_SIZE = 12;

const headerTitle = ref(SITE_TITLE);
const categories = ref([]);
const posts = ref([]);
const totalElements = ref(0);
const totalPages = ref(0);
const loading = ref(true);
const failed = ref(false);

const page = computed(() => {
  const value = Number(route.query.page ?? 1);

  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 1;
});

const categoryId = computed(() => {
  const value = Number(route.query.category);

  return Number.isFinite(value) && value > 0 ? value : null;
});

const sort = computed(() => (route.query.sort === 'popular' ? 'popular' : 'latest'));

const currentCategoryName = computed(
  () => categories.value.find((category) => category.id === categoryId.value)?.name ?? '',
);

/** 앞뒤로 두 칸씩만 보여 준다. 글이 늘어도 번호 줄이 가로로 넘치지 않게 */
const pageNumbers = computed(() => {
  const numbers = [];
  const start = Math.max(1, page.value - 2);
  const end = Math.min(totalPages.value, page.value + 2);

  for (let number = start; number <= end; number += 1) {
    numbers.push(number);
  }

  return numbers;
});

function queryFor(changes) {
  const next = {
    ...route.query,
    ...changes,
  };

  // 기본값은 주소에서 뺀다. /posts?page=1&sort=latest 보다 /posts 가 읽기 좋다
  if (!next.page || Number(next.page) === 1) {
    delete next.page;
  }

  if (next.sort === 'latest') {
    delete next.sort;
  }

  if (!next.category) {
    delete next.category;
  }

  return next;
}

async function load() {
  loading.value = true;
  failed.value = false;

  try {
    const result = await getPosts({
      page: page.value,
      size: PAGE_SIZE,
      categoryId: categoryId.value,
      sort: sort.value,
    });

    posts.value = result.items;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  } catch (error) {
    console.error(error);
    failed.value = true;
    posts.value = [];
  } finally {
    loading.value = false;
  }
}

// 주소가 바뀌면 다시 불러온다. 페이지·카테고리·정렬이 전부 주소에 있어서 이 하나로 끝난다
watch(() => route.fullPath, load, { immediate: true });

getCategories()
  .then((result) => {
    categories.value = Array.isArray(result) ? result : [];
  })
  .catch(() => {
    // 분류를 못 받아도 목록은 보여야 한다. 필터만 빠진다
    categories.value = [];
  });

getBlogProfile()
  .then((profile) => {
    headerTitle.value = profile?.name || SITE_TITLE;
  })
  .catch(() => null);

function goTo(number) {
  router.push({ name: 'post-list', query: queryFor({ page: String(number) }) });
}
</script>

<template>
  <div class="public-shell">
    <BlogHeader :title="headerTitle" />

    <main class="public-shell__main post-list">
      <header class="post-list__head">
        <h1 class="post-list__title">{{ currentCategoryName || '전체 글' }}</h1>
        <p v-if="!loading" class="post-list__count">{{ totalElements }}편</p>
      </header>

      <nav class="post-list__filters" aria-label="분류">
        <RouterLink
          class="post-list__filter"
          :class="{ 'post-list__filter--active': categoryId === null }"
          :to="{ name: 'post-list', query: queryFor({ category: '', page: '' }) }"
        >전체</RouterLink>
        <RouterLink
          v-for="category in categories"
          :key="category.id"
          class="post-list__filter"
          :class="{ 'post-list__filter--active': categoryId === category.id }"
          :to="{ name: 'post-list', query: queryFor({ category: String(category.id), page: '' }) }"
        >{{ category.name }}</RouterLink>

        <span class="post-list__filter-gap"></span>

        <RouterLink
          class="post-list__filter"
          :class="{ 'post-list__filter--active': sort === 'latest' }"
          :to="{ name: 'post-list', query: queryFor({ sort: 'latest', page: '' }) }"
        >최신순</RouterLink>
        <RouterLink
          class="post-list__filter"
          :class="{ 'post-list__filter--active': sort === 'popular' }"
          :to="{ name: 'post-list', query: queryFor({ sort: 'popular', page: '' }) }"
        >인기순</RouterLink>
      </nav>

      <p v-if="loading" class="post-list__empty">불러오는 중…</p>
      <p v-else-if="failed" class="post-list__empty" role="alert">
        글을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
      </p>
      <p v-else-if="posts.length === 0" class="post-list__empty">아직 글이 없습니다.</p>

      <div v-else class="post-grid">
        <RouterLink
          v-for="post in posts"
          :key="post.id"
          class="post-card"
          :to="{ name: 'post-detail', params: { id: post.id } }"
        >
          <img class="post-card__image" :src="post.imageUrl" :alt="post.title" />
          <div class="post-card__body">
            <span class="post-card__category">{{ post.category }}</span>
            <h2 class="post-card__title">{{ post.title }}</h2>
            <div class="post-card__meta">
              <time>{{ post.publishedAt }}</time>
              <span>조회 {{ post.views }}</span>
            </div>
          </div>
        </RouterLink>
      </div>

      <nav v-if="totalPages > 1" class="post-pager" aria-label="페이지">
        <button
          class="post-pager__button"
          type="button"
          :disabled="page <= 1"
          @click="goTo(page - 1)"
        >이전</button>

        <button
          v-for="number in pageNumbers"
          :key="number"
          class="post-pager__number"
          :class="{ 'post-pager__number--active': number === page }"
          type="button"
          :aria-current="number === page ? 'page' : undefined"
          @click="goTo(number)"
        >{{ number }}</button>

        <button
          class="post-pager__button"
          type="button"
          :disabled="page >= totalPages"
          @click="goTo(page + 1)"
        >다음</button>
      </nav>
    </main>

    <footer class="public-footer">
      <RouterLink to="/privacy">개인정보처리방침</RouterLink>
      <RouterLink to="/terms">서비스 이용약관</RouterLink>
    </footer>
  </div>
</template>
