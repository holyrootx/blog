<script setup>
import { computed, onMounted, ref } from 'vue';

import { getAdminViewStats, getAdminViewStatsPosts } from '../api/adminApi';
import { formatSeoulDate, formatSeoulDateTime } from '../../../shared/time/seoulTime';
import AdminDailyViewsChart from './AdminDailyViewsChart.vue';
import AdminDataGrid from './AdminDataGrid.vue';
import AdminPagination from './AdminPagination.vue';
import AdminSegmented from './AdminSegmented.vue';

/**
 * 대시보드의 조회 통계.
 *
 * <p>따로 주소를 두지 않고 대시보드에 둔다. 새 관리자 주소는 운영 Nginx 에 경로를 손으로 더해야
 * 새로고침해도 열리고, 사이드바 메뉴도 DB 에 한 줄 넣어야 한다.</p>
 *
 * <p>요약과 글 목록은 따로 받는다. 한쪽이 늦어도 먼저 온 쪽부터 보인다. 기간을 바꾸면 그려 둔 것을
 * 흐리게 남긴 채 새로 받는다 — 뼈대로 갈아 끼우면 같은 자리의 숫자가 깜빡여 비교하기 어렵다.</p>
 */
const PERIODS = [
  { value: 7, label: '최근 7일' },
  { value: 30, label: '최근 30일' },
];

const DEVICE_LABELS = {
  MOBILE: '휴대폰',
  TABLET: '태블릿',
  DESKTOP: '데스크톱',
  UNKNOWN: '알 수 없음',
};

const STATUS_LABELS = {
  PRIVATE: '비공개',
  DRAFT: '임시저장',
  SCHEDULED: '예약',
};

const POST_PAGE_SIZE = 10;

// * 은 기간과 상관없는 지금 값이다. 기간 값과 나란히 있어서 표시가 없으면 같은 기간의 숫자로 읽힌다
const POST_COLUMNS = [
  { key: 'rank', label: '순위', width: '56px', align: 'center' },
  { key: 'title', label: '제목' },
  { key: 'pageViews', label: '조회 기록', width: '92px', align: 'right' },
  { key: 'uniqueMembers', label: '고유 회원', width: '92px', align: 'right' },
  { key: 'anonymousViews', label: '비회원 방문', width: '100px', align: 'right' },
  { key: 'totalViews', label: '누적 조회*', width: '96px', align: 'right' },
  { key: 'commentCount', label: '댓글*', width: '72px', align: 'right' },
  { key: 'likeCount', label: '좋아요*', width: '80px', align: 'right' },
];

const days = ref(7);

const summary = ref(null);
const summaryLoading = ref(true);
const summaryError = ref('');
let summarySeq = 0;

const posts = ref([]);
const postsPage = ref(1);
const postsTotalPages = ref(0);
const postsLoaded = ref(false);
const postsLoading = ref(true);
const postsError = ref('');
let postsSeq = 0;

const tiles = computed(() => {
  const value = summary.value;
  if (!value) return [];

  return [
    { id: 'views', label: '조회 기록', value: `${formatCount(value.pageViews)}건`,
      helper: '같은 사람이 30분 안에 다시 연 글은 한 번' },
    { id: 'members', label: '로그인 회원', value: `${formatCount(value.uniqueMembers)}명`,
      helper: `회원 조회 ${formatCount(value.memberViews)}건` },
    { id: 'anonymous', label: '비회원 방문', value: `${formatCount(value.anonymousViews)}건`,
      helper: '사람 수가 아니라 방문 건수' },
    { id: 'excluded', label: '통계에서 뺀 조회',
      value: `${formatCount(value.excludedAdminViews + value.excludedBotViews)}건`,
      helper: `관리자 본인 ${formatCount(value.excludedAdminViews)}건 · 검색 봇 ${formatCount(value.excludedBotViews)}건` },
  ];
});

const deviceTotal = computed(() => (summary.value?.devices ?? [])
  .reduce((total, device) => total + device.pageViews, 0));

// 기록을 시작하기 전 날짜는 0 으로 보인다. 그 0 이 "아무도 안 봤다" 로 읽히지 않게 적어 준다
const periodNote = computed(() => {
  const value = summary.value;
  if (!value) return '';
  if (!value.recordingStartedAt) return '아직 조회 기록이 없습니다.';

  const startedOn = formatSeoulDate(value.recordingStartedAt);
  const periodStart = value.from.replaceAll('-', '.');
  if (startedOn > periodStart) {
    return `${formatSeoulDateTime(value.recordingStartedAt)}부터 기록했습니다. 그 전 날짜는 기록이 없어 0으로 보입니다.`;
  }
  return `${periodStart} ~ ${value.to.replaceAll('-', '.')} (서울 기준)`;
});

onMounted(() => {
  loadSummary();
  loadPosts();
});

function changeDays(next) {
  if (next === days.value) return;
  days.value = next;
  postsPage.value = 1;
  loadSummary();
  loadPosts();
}

function changePostsPage(next) {
  postsPage.value = next;
  loadPosts();
}

async function loadSummary() {
  const seq = ++summarySeq;
  summaryLoading.value = true;
  summaryError.value = '';

  try {
    const next = await getAdminViewStats(days.value);
    if (seq !== summarySeq) return;
    summary.value = next;
  } catch (error) {
    if (seq !== summarySeq) return;
    console.error(error);
    summaryError.value = error?.message ?? '조회 통계를 불러오지 못했습니다.';
  } finally {
    if (seq === summarySeq) summaryLoading.value = false;
  }
}

async function loadPosts() {
  const seq = ++postsSeq;
  postsLoading.value = true;
  postsError.value = '';

  try {
    const next = await getAdminViewStatsPosts({ days: days.value, page: postsPage.value, size: POST_PAGE_SIZE });
    if (seq !== postsSeq) return;
    const offset = (next.page - 1) * POST_PAGE_SIZE;
    posts.value = next.items.map((post, index) => ({ ...post, rank: offset + index + 1 }));
    postsTotalPages.value = next.totalPages;
    postsLoaded.value = true;
  } catch (error) {
    if (seq !== postsSeq) return;
    console.error(error);
    postsError.value = error?.message ?? '글별 조회 기록을 불러오지 못했습니다.';
  } finally {
    if (seq === postsSeq) postsLoading.value = false;
  }
}

function devicePercent(device) {
  return deviceTotal.value === 0 ? 0 : Math.round((device.pageViews / deviceTotal.value) * 100);
}

function postBadge(post) {
  if (post.deleted) return '삭제됨';
  return STATUS_LABELS[post.status] ?? '';
}

function formatCount(value) {
  return (value ?? 0).toLocaleString('ko-KR');
}
</script>

<template>
  <section class="admin-panel admin-view-stats" aria-labelledby="admin-view-stats-title">
    <div class="admin-panel__header">
      <div>
        <h2 id="admin-view-stats-title">조회 통계</h2>
        <p>
          새 조회 기록 기준입니다. 같은 사람이 같은 글을 30분 안에 다시 연 것은 한 번으로 세고,
          관리자 본인 조회와 검색 봇은 뺐습니다. 위의 누적 조회와는 세는 기준이 다릅니다.
        </p>
      </div>
      <AdminSegmented
        :model-value="days"
        label="기간"
        hide-label
        :options="PERIODS"
        @update:model-value="changeDays"
      />
    </div>

    <div
      class="admin-view-stats__summary"
      :class="{ 'admin-view-stats__summary--refreshing': summary && summaryLoading }"
      :aria-busy="summaryLoading"
    >
      <div v-if="summaryError" class="admin-view-stats__error" role="alert">
        <span>{{ summaryError }}</span>
        <button class="admin-button admin-button--ghost admin-button--small" type="button" @click="loadSummary">
          다시 시도
        </button>
      </div>

      <template v-if="!summary && summaryLoading">
        <div class="admin-view-stats__tiles" aria-hidden="true">
          <div v-for="index in 4" :key="index" class="admin-view-stats__tile">
            <span class="ui-skeleton admin-view-stats__skeleton-label"></span>
            <span class="ui-skeleton admin-view-stats__skeleton-value"></span>
            <span class="ui-skeleton admin-view-stats__skeleton-helper"></span>
          </div>
        </div>
        <div class="admin-view-stats__charts" aria-hidden="true">
          <div class="admin-view-stats__chart-card">
            <span class="ui-skeleton admin-view-stats__skeleton-chart"></span>
          </div>
          <div class="admin-view-stats__chart-card">
            <span class="ui-skeleton admin-view-stats__skeleton-chart"></span>
          </div>
        </div>
      </template>

      <template v-else-if="summary">
        <p class="admin-view-stats__note">{{ periodNote }}</p>

        <dl class="admin-view-stats__tiles">
          <div v-for="tile in tiles" :key="tile.id" class="admin-view-stats__tile">
            <dt>{{ tile.label }}</dt>
            <dd class="admin-view-stats__tile-value">{{ tile.value }}</dd>
            <dd class="admin-view-stats__tile-helper">{{ tile.helper }}</dd>
          </div>
        </dl>

        <div class="admin-view-stats__charts">
          <div class="admin-view-stats__chart-card">
            <h3>날짜별 조회 기록</h3>
            <AdminDailyViewsChart :days="summary.daily" />
          </div>

          <div class="admin-view-stats__chart-card">
            <h3>기기</h3>
            <ul class="admin-view-stats__devices">
              <li v-for="device in summary.devices" :key="device.deviceType">
                <div class="admin-view-stats__device-row">
                  <span>{{ DEVICE_LABELS[device.deviceType] ?? device.deviceType }}</span>
                  <span>{{ formatCount(device.pageViews) }}건 · {{ devicePercent(device) }}%</span>
                </div>
                <div class="admin-view-stats__device-track" aria-hidden="true">
                  <span :style="{ width: `${devicePercent(device)}%` }"></span>
                </div>
              </li>
            </ul>
            <p class="admin-view-stats__hint">
              브라우저 정보로 거칠게 나눈 값입니다. 아이패드는 데스크톱으로 잡힐 수 있고,
              기기를 밝히지 않는 요청은 알 수 없음으로 셉니다. 검색 봇은 여기에 넣지 않습니다.
            </p>
          </div>
        </div>
      </template>
    </div>

    <div
      class="admin-view-stats__posts"
      :class="{ 'admin-view-stats__posts--refreshing': postsLoaded && postsLoading }"
      :aria-busy="postsLoading"
    >
      <div class="admin-view-stats__posts-head">
        <h3>글별 조회 기록</h3>
        <p>기간 안 기록이 많은 글부터입니다. *는 기간과 상관없는 지금 값입니다 — 그 기간에 생긴 댓글·좋아요가 아닙니다.</p>
      </div>

      <AdminDataGrid
        :columns="POST_COLUMNS"
        :rows="posts"
        :loading="postsLoading && !postsLoaded"
        :error-text="postsError"
        empty-text="이 기간에 조회 기록이 있는 글이 없습니다."
        min-width="760px"
        @retry="loadPosts"
      >
        <template #cell-title="{ row }">
          <span class="admin-view-stats__post-title">
            <span>{{ row.title }}</span>
            <span v-if="postBadge(row)" class="admin-badge admin-badge--off">{{ postBadge(row) }}</span>
          </span>
        </template>
        <template #cell-pageViews="{ value }">{{ formatCount(value) }}</template>
        <template #cell-uniqueMembers="{ value }">{{ formatCount(value) }}</template>
        <template #cell-anonymousViews="{ value }">{{ formatCount(value) }}</template>
        <template #cell-totalViews="{ value }">{{ formatCount(value) }}</template>
        <template #cell-commentCount="{ value }">{{ formatCount(value) }}</template>
        <template #cell-likeCount="{ value }">{{ formatCount(value) }}</template>
      </AdminDataGrid>

      <AdminPagination :page="postsPage" :total-pages="postsTotalPages" @update:page="changePostsPage" />
    </div>
  </section>
</template>
