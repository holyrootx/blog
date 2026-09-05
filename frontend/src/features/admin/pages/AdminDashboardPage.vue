<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import AdminSideBar from '../components/AdminSideBar.vue';
import { getAdminDashboard } from '../api/adminApi';

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
    id: 'comments',
    label: '답변 대기 댓글',
    value: formatCount(dashboard.unansweredCommentCount, '개'),
    helper: dashboard.oldestUnansweredAt
      ? `가장 오래된 댓글 ${formatRelativeTime(dashboard.oldestUnansweredAt)}`
      : '관리자 댓글 API 연결 필요',
    tone: 'accent',
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
    id: 'comments',
    title: '답변 기다리는 댓글',
    value: formatCount(dashboard.unansweredCommentCount, '개'),
    detail: dashboard.oldestUnansweredAt
      ? `가장 오래된 댓글 ${formatRelativeTime(dashboard.oldestUnansweredAt)}`
      : '답변 대기 시간을 계산하려면 댓글 API가 필요합니다.',
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
    value: formatCount(dashboard.daysSinceStart, '일'),
    detail: '프로필의 시작일 기준으로 계산합니다.',
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
    unansweredCommentCount: null,
    oldestUnansweredAt: null,
    mostViewedCategory: null,
    categoryShares: [],
    unansweredComments: [],
  };
}

function formatCount(value, unit) {
  if (value === null || value === undefined) {
    return '연결 전';
  }

  return `${new Intl.NumberFormat('ko-KR').format(value)}${unit}`;
}

function formatDashboardIntro() {
  if (dashboard.daysSinceStart === null || dashboard.daysSinceStart === undefined) {
    return '관리자 API를 연결하면 오늘 볼 일을 바로 확인할 수 있습니다.';
  }

  return `기록 ${dashboard.daysSinceStart}일째. 오늘도 한 줄 남겨볼까요?`;
}

function formatPostSummary() {
  if (dashboard.publishedPostCount === null || dashboard.publishedPostCount === undefined) {
    return '연결 전';
  }

  return `${new Intl.NumberFormat('ko-KR').format(dashboard.publishedPostCount)}편`;
}

function formatDateTime(value) {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function formatRelativeTime(value) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '확인 필요';
  }

  const diffMs = Date.now() - date.getTime();
  const diffDays = Math.floor(diffMs / 1000 / 60 / 60 / 24);

  if (diffDays <= 0) {
    return '오늘';
  }

  return `${diffDays}일 전`;
}

function getCategoryPercent(category) {
  if (categoryTotal.value <= 0) {
    return 0;
  }

  return Math.round((category.postCount / categoryTotal.value) * 100);
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <AdminSideBar />

      <main class="admin-main">
        <header class="admin-dashboard__header">
          <div>
            <h1 class="admin-dashboard__title">대시보드</h1>
            <p class="admin-dashboard__intro">
              {{ loading ? '기록 상태를 불러오는 중입니다.' : formatDashboardIntro() }}
            </p>
          </div>

          <div class="admin-dashboard__actions">
            <RouterLink class="admin-button admin-button--ghost" :to="{ name: 'home' }">
              블로그 보기
            </RouterLink>
            <button class="admin-button admin-button--solid" type="button">
              새 글 쓰기
            </button>
          </div>
        </header>

        <section v-if="loadFailed" class="admin-notice" aria-live="polite">
          <strong>관리자 대시보드 API 연결 전입니다.</strong>
          <span>
            백엔드에 <code>GET /api/v1/admin/blog/dashboard</code>를 만들면 이 화면이 실제 데이터로 바뀝니다.
          </span>
        </section>

        <section class="admin-summary-grid" aria-label="운영 요약">
          <article
            v-for="card in summaryCards"
            :key="card.id"
            class="admin-summary-card"
            :class="`admin-summary-card--${card.tone}`"
          >
            <span class="admin-summary-card__label">{{ card.label }}</span>
            <strong class="admin-summary-card__value">
              {{ loading ? '불러오는 중' : card.value }}
            </strong>
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

            <ul class="admin-operation-list">
              <li v-for="item in operationItems" :key="item.id">
                <span>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.detail }}</small>
                </span>
                <em>{{ loading ? '확인 중' : item.value }}</em>
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

            <ul v-if="dashboard.categoryShares.length > 0" class="admin-category-list">
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
              <strong>{{ loading ? '분포를 계산하는 중입니다.' : '카테고리 데이터 연결 전입니다.' }}</strong>
              <p>관리자 대시보드 API에서 <code>categoryShares</code>를 내려주면 표시됩니다.</p>
            </div>
          </article>

          <article class="admin-panel admin-panel--wide">
          <div class="admin-panel__header">
            <div>
              <h2>답변 기다리는 댓글</h2>
              <p>오래 기다린 댓글부터 확인합니다.</p>
            </div>
            <button class="admin-panel__link" type="button" disabled>
              댓글 관리로
            </button>
          </div>

          <ul v-if="dashboard.unansweredComments.length > 0" class="admin-comment-list">
            <li
              v-for="comment in dashboard.unansweredComments"
              :key="comment.id"
              class="admin-comment-item"
            >
              <span class="admin-comment-item__avatar" aria-hidden="true"></span>
              <div class="admin-comment-item__body">
                <div class="admin-comment-item__meta">
                  <strong>{{ comment.guestNickname }}</strong>
                  <span>{{ formatDateTime(comment.createdAt) }}</span>
                </div>
                <p class="admin-comment-item__content">{{ comment.content }}</p>
                <span class="admin-comment-item__post">{{ comment.postTitle }}</span>
              </div>
              <button class="admin-comment-item__reply" type="button" disabled>답글</button>
            </li>
          </ul>

          <div v-else class="admin-empty-state">
            <strong>{{ loading ? '댓글을 불러오는 중입니다.' : '확인할 댓글이 없습니다.' }}</strong>
            <p>
              관리자 댓글 목록 API가 붙으면 미답변 댓글 3건이 이곳에 먼저 표시됩니다.
            </p>
          </div>
          </article>
        </section>
      </main>
    </div>
  </div>
</template>
