<script setup>
import { computed, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import BlogHeader from '../../home/components/BlogHeader.vue';
import HighlightedText from '../../search/components/HighlightedText.vue';
import { getPosts } from '../api/postApi';
import { loadCategories } from '../data/categoryStore';
import { getBlogProfile, getHomePosts } from '../../home/api/homeApi';

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
const fallbackPosts = ref([]);
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

/**
 * 검색어.
 *
 * <p>서버와 같은 최소 길이를 여기서도 본다. 주소창에 {@code ?q=ㄱ} 을 직접 칠 수 있고,
 * 그때 화면이 "검색 결과" 라고 말하면서 전체 목록을 보여 주면 앞뒤가 맞지 않는다.</p>
 */
const MIN_KEYWORD_LENGTH = 2;

const keyword = computed(() => {
  const typed = (route.query.q ?? '').trim();

  return typed.length >= MIN_KEYWORD_LENGTH ? typed : '';
});

const currentCategoryName = computed(
  () => categories.value.find((category) => category.id === categoryId.value)?.name ?? '',
);

const heading = computed(() => {
  if (keyword.value) {
    return `‘${keyword.value}’ 검색 결과`;
  }

  return currentCategoryName.value || '전체 글';
});

/**
 * 번호를 열 개씩 묶어 보여 준다.
 *
 * <p>전에는 현재 쪽 앞뒤로 두 칸씩만 그렸다. 줄이 넘치지는 않았지만 누를 때마다 번호가
 * 한 칸씩 밀려서, 아까 본 쪽을 다시 찾기 어려웠다. 묶음은 자리가 고정이라 8쪽이
 * 어디쯤인지 눈으로 익힌 자리가 그대로 남는다.</p>
 */
const BLOCK_SIZE = 10;

const blockStart = computed(() => Math.floor((page.value - 1) / BLOCK_SIZE) * BLOCK_SIZE + 1);
const blockEnd = computed(() => Math.min(blockStart.value + BLOCK_SIZE - 1, totalPages.value));

const hasPreviousBlock = computed(() => blockStart.value > 1);
const hasNextBlock = computed(() => blockEnd.value < totalPages.value);

const pageNumbers = computed(() => {
  const numbers = [];

  for (let number = blockStart.value; number <= blockEnd.value; number += 1) {
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

  if (!next.q) {
    delete next.q;
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
      keyword: keyword.value,
    });

    posts.value = result.items;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;

    await loadFallbackIfEmpty();
  } catch (error) {
    console.error(error);
    failed.value = true;
    posts.value = [];
  } finally {
    loading.value = false;
  }
}

/**
 * 찾는 글이 없을 때 최근 글을 같이 내준다.
 *
 * <p>"없습니다" 만 있는 화면은 막다른 길이다. 찾던 것이 없다는 사실은 알려야 하지만,
 * 거기서 나갈 길도 같이 줘야 뒤로가기 말고 할 일이 생긴다.</p>
 */
async function loadFallbackIfEmpty() {
  if (posts.value.length > 0 || !keyword.value) {
    fallbackPosts.value = [];
    return;
  }

  // 여기서 실패해도 검색 결과 화면 자체는 이미 멀쩡하다. 덤이 빠질 뿐이다
  fallbackPosts.value = await getHomePosts('latest').catch(() => []);
}

function clearKeyword() {
  router.push({ name: 'post-list', query: queryFor({ q: '', page: '' }) });
}

// 주소가 바뀌면 다시 불러온다. 페이지·카테고리·정렬이 전부 주소에 있어서 이 하나로 끝난다
watch(() => route.fullPath, load, { immediate: true });

// 헤더도 같은 값을 쓴다. 같이 쓰는 자리를 거치므로 한 화면에 요청이 한 번만 나간다
loadCategories().then((found) => {
  categories.value = found;
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
        <h1 class="post-list__title">{{ heading }}</h1>
        <p v-if="!loading" class="post-list__count">{{ totalElements }}편</p>
        <button
          v-if="keyword"
          class="post-list__clear"
          type="button"
          @click="clearKeyword"
        >검색 지우기</button>
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
      <template v-else-if="posts.length === 0">
        <!--
          검색어 뒤에 조사를 붙이지 않는다. 와/과, 이/가 는 앞말 받침에 따라 달라지는데
          검색어는 사람이 치는 값이라 무엇이 올지 모른다. 붙이면 반드시 틀리는 경우가 생긴다
        -->
        <p class="post-list__empty">
          <template v-if="keyword">‘{{ keyword }}’ 검색 결과가 없습니다.</template>
          <template v-else>아직 글이 없습니다.</template>
        </p>

        <!-- 검색이 빈손일 때만. 막다른 길 대신 읽을거리를 준다 -->
        <section v-if="fallbackPosts.length > 0" class="post-list__fallback">
          <h2 class="post-list__fallback-title">대신 최근 글은 어떠세요</h2>
          <div class="post-grid">
            <RouterLink
              v-for="post in fallbackPosts"
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
            <h2 class="post-card__title">
              <HighlightedText :text="post.title" :keyword="keyword" />
            </h2>
            <div class="post-card__meta">
              <time>{{ post.publishedAt }}</time>
              <span>조회 {{ post.views }}</span>
            </div>
          </div>
        </RouterLink>
      </div>

      <nav v-if="totalPages > 1" class="post-pager" aria-label="페이지">
        <!--
          앞 묶음으로는 그 묶음의 마지막 쪽으로 간다. 10쪽에서 11쪽으로 넘어왔다면
          돌아갈 곳도 10쪽이다 — 1쪽으로 보내면 읽던 자리를 잃는다
        -->
        <button
          v-if="hasPreviousBlock"
          class="post-pager__button"
          type="button"
          title="1쪽으로"
          @click="goTo(1)"
        >처음</button>
        <button
          v-if="hasPreviousBlock"
          class="post-pager__button"
          type="button"
          @click="goTo(blockStart - 1)"
        >‹ 이전 {{ BLOCK_SIZE }}개</button>

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
          v-if="hasNextBlock"
          class="post-pager__button"
          type="button"
          @click="goTo(blockEnd + 1)"
        >다음 {{ BLOCK_SIZE }}개 ›</button>
        <button
          v-if="hasNextBlock"
          class="post-pager__button"
          type="button"
          :title="`${totalPages}쪽으로`"
          @click="goTo(totalPages)"
        >끝</button>
      </nav>
    </main>

    <footer class="public-footer">
      <RouterLink to="/privacy">개인정보처리방침</RouterLink>
      <RouterLink to="/terms">서비스 이용약관</RouterLink>
    </footer>
  </div>
</template>
