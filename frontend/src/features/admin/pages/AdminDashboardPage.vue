<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { getAdminDashboard } from '../api/adminApi';
import AdminPageHeader from '../components/AdminPageHeader.vue';

const dashboard = reactive(createEmptyDashboard());
const loading = ref(true);
const loadFailed = ref(false);

const summaryCards = computed(() => [
  {
    id: 'draft',
    label: '임시저장 글',
    value: formatCount(dashboard.draftPostCount, '편'),
    helper: '마저 정리해야 할 글',
    tone: 'quiet',
  },
  {
    id: 'views',
    label: '누적 조회',
    value: formatCount(dashboard.totalViews, '회'),
    helper: '글별 조회수 합계',
    tone: 'quiet',
  },
  {
    id: 'reports',
    label: '신고 댓글',
    value: formatCount(dashboard.reportedCommentCount, '개'),
    helper: dashboard.reportedCommentCount > 0
      ? '확인이 필요한 신고 댓글'
      : '처리할 신고 댓글이 없습니다.',
    tone: dashboard.reportedCommentCount > 0 ? 'accent' : 'quiet',
  },
  {
    id: 'published',
    label: '쓴 글',
    value: formatPostSummary(),
    helper: dashboard.postCountThisMonth === null
      ? '이번 달 작성 수 연결 필요'
      : `이번 달 ${dashboard.postCountThisMonth}편`,
    tone: 'quiet',
  },
]);

const categoryTotal = computed(() => (
  dashboard.categoryShares.reduce((total, category) => total + category.postCount, 0)
));

const operationItems = computed(() => [
  {
    id: 'reports',
    title: '신고 댓글',
    value: formatCount(dashboard.reportedCommentCount, '개'),
    detail: dashboard.reportedCommentCount > 0
      ? '확인이 필요한 미처리 신고입니다.'
      : '처리할 신고 댓글이 없습니다.',
  },
  {
    id: 'category',
    title: '조회가 몰린 카테고리',
    value: dashboard.mostViewedCategory ?? '연결 전',
    detail: '카테고리별 조회수 합계 기준입니다.',
  },
  {
    id: 'history',
    title: '블로그 운영 기간',
    value: dashboard.daysSinceStart === null || dashboard.daysSinceStart === undefined
      ? '시작일 미설정'
      : formatCount(dashboard.daysSinceStart, '일'),
    detail: dashboard.daysSinceStart === null || dashboard.daysSinceStart === undefined
      ? '프로필에 블로그 시작일을 넣으면 계산합니다.'
      : '프로필의 시작일 기준으로 계산합니다.',
  },
]);

onMounted(async () => {
  try {
    const nextDashboard = await getAdminDashboard();
    Object.assign(dashboard, nextDashboard);
  } catch (error) {
    loadFailed.value = true;
    console.warn(error);
  } finally {
    loading.value = false;
  }
});

function createEmptyDashboard() {
  return {
    daysSinceStart: null,
    draftPostCount: null,
    totalViews: null,
    publishedPostCount: null,
    postCountThisMonth: null,
    reportedCommentCount: null,
    mostViewedCategory: null,
    categoryShares: [],
  };
}

function formatCount(value, unit) {
  if (value === null || value === undefined) {
    return '연결 전';
  }

  return `${new Intl.NumberFormat('ko-KR').format(value)}${unit}`;
}

function formatDashboardIntro() {
  // daysSinceStart 가 없는 이유는 API 미연결이 아니라 블로그 시작일이 없어서다.
  // 틀린 이유를 말하면 사용자가 엉뚱한 곳을 고치러 간다
  if (dashboard.daysSinceStart === null || dashboard.daysSinceStart === undefined) {
    return '오늘도 한 줄 남겨볼까요?';
  }

  return `기록 ${dashboard.daysSinceStart}일째. 오늘도 한 줄 남겨볼까요?`;
}

function formatPostSummary() {
  if (dashboard.publishedPostCount === null || dashboard.publishedPostCount === undefined) {
    return '연결 전';
  }

  return `${new Intl.NumberFormat('ko-KR').format(dashboard.publishedPostCount)}편`;
}

function getCategoryPercent(category) {
  if (categoryTotal.value <= 0) {
    return 0;
  }

  return Math.round((category.postCount / categoryTotal.value) * 100);
}

</script>

<template>
  <div class="admin-dashboard">
        <AdminPageHeader>
          <template #meta>
            <span v-if="loading" class="ui-skeleton admin-dashboard-skeleton__intro" aria-hidden="true"></span>
            <template v-else>{{ formatDashboardIntro() }}</template>
          </template>

          <template #actions>
            <RouterLink class="admin-button admin-button--ghost" :to="{ name: 'home' }">
              블로그 보기
            </RouterLink>
            <RouterLink class="admin-button admin-button--solid" :to="{ name: 'admin-post-new' }">
              새 글 쓰기
            </RouterLink>
          </template>
        </AdminPageHeader>

        <section v-if="loadFailed" class="admin-notice" aria-live="polite">
          <strong>관리자 대시보드 API 연결 전입니다.</strong>
          <span>
            백엔드에 <code>GET /api/v1/admin/blog/dashboard</code>를 만들면 이 화면이 실제 데이터로 바뀝니다.
          </span>
        </section>

        <section v-if="loading" class="admin-summary-grid" aria-label="운영 요약" aria-busy="true">
          <article
            v-for="index in 4"
            :key="index"
            class="admin-summary-card admin-summary-card--quiet admin-summary-card--skeleton"
            aria-hidden="true"
          >
            <span class="ui-skeleton"></span>
            <span class="ui-skeleton"></span>
            <span class="ui-skeleton"></span>
          </article>
        </section>
        <section v-else class="admin-summary-grid" aria-label="운영 요약">
          <article
            v-for="card in summaryCards"
            :key="card.id"
            class="admin-summary-card"
            :class="[`admin-summary-card--${card.tone}`, { 'admin-summary-card--views': card.id === 'views' }]"
          >
            <span class="admin-summary-card__label">{{ card.label }}</span>
            <strong class="admin-summary-card__value">{{ card.value }}</strong>
            <span class="admin-summary-card__helper">{{ card.helper }}</span>
          </article>
        </section>

        <section class="admin-dashboard__grid">
          <article class="admin-panel">
            <div class="admin-panel__header">
              <div>
                <h2>작업 큐</h2>
                <p>오늘 바로 볼 운영 지표입니다.</p>
              </div>
            </div>

            <ul v-if="loading" class="admin-operation-list admin-operation-list--skeleton" aria-hidden="true">
              <li v-for="index in 3" :key="index">
                <span><span class="ui-skeleton"></span><span class="ui-skeleton"></span></span>
                <span class="ui-skeleton"></span>
              </li>
            </ul>
            <ul v-else class="admin-operation-list">
              <li v-for="item in operationItems" :key="item.id">
                <span>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.detail }}</small>
                </span>
                <em>{{ item.value }}</em>
              </li>
            </ul>
          </article>

          <article class="admin-panel">
            <div class="admin-panel__header">
              <div>
                <h2>카테고리 비중</h2>
                <p>발행 글 기준으로 분포를 봅니다.</p>
              </div>
            </div>

            <ul v-if="loading" class="admin-category-list admin-category-list--skeleton" aria-hidden="true">
              <li v-for="index in 4" :key="index">
                <div class="admin-category-list__row">
                  <span class="ui-skeleton"></span>
                  <span class="ui-skeleton"></span>
                </div>
                <span class="ui-skeleton"></span>
              </li>
            </ul>
            <ul v-else-if="dashboard.categoryShares.length > 0" class="admin-category-list">
              <li v-for="category in dashboard.categoryShares" :key="category.categoryId">
                <div class="admin-category-list__row">
                  <strong>{{ category.name }}</strong>
                  <span>{{ category.postCount }}편</span>
                </div>
                <div class="admin-category-list__bar" aria-hidden="true">
                  <span :style="{ width: `${getCategoryPercent(category)}%` }"></span>
                </div>
              </li>
            </ul>

            <div v-else class="admin-empty-state admin-empty-state--compact">
              <strong>카테고리 데이터 연결 전입니다.</strong>
              <p>관리자 대시보드 API에서 <code>categoryShares</code>를 내려주면 표시됩니다.</p>
            </div>
          </article>
        </section>
  </div>
</template>
