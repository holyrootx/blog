<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getAdminGuestbook } from '../api/adminApi';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminGuestbookDetailModal from '../components/AdminGuestbookDetailModal.vue';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import { useAdminListRequest } from '../composables/useAdminListRequest';
import { notifySuccess } from '../../../shared/toast/toastStore';

/**
 * 방명록 관리.
 *
 * <p>"답글 없음" 거르기와 숫자는 두지 않는다. 방명록은 답할 의무가 있는 자리가 아니라서,
 * 안 단 답글을 밀린 일처럼 세면 들어올 때마다 독촉을 보는 셈이 된다.</p>
 */
const FILTERS = ['ALL', 'REPORTED', 'HIDDEN', 'DELETED'];
const EMPTY_CONDITION = { status: 'ALL', keyword: '' };
const COLUMNS = [
  { key: 'nickname', label: '쓴 사람', width: '140px' },
  { key: 'content', label: '내용' },
  { key: 'state', label: '상태', width: '120px', align: 'center' },
  { key: 'reports', label: '신고', width: '120px', align: 'center' },
  { key: 'createdAt', label: '쓴 시각', width: '150px' },
];

const route = useRoute();
const router = useRouter();
const condition = reactive({
  ...EMPTY_CONDITION,
  status: FILTERS.includes(String(route.query.status)) ? String(route.query.status) : 'ALL',
});
const applied = ref({ ...condition });

const entries = ref([]);
const statusCounts = ref({ all: 0, reported: 0, hidden: 0, deleted: 0 });
const totalElements = ref(0);
const totalPages = ref(0);
const page = ref(1);
const pageSize = ref(20);
const detailId = ref(null);

const { loading, loadError, load: requestEntries } = useAdminListRequest({
  request: getAdminGuestbook,
  apply(result) {
    entries.value = result.items;
    statusCounts.value = result.statusCounts;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  },
  errorMessage: '방명록 목록을 불러오지 못했습니다.',
  onError(error) {
    console.error(error);
    entries.value = [];
  },
});

const statusOptions = computed(() => [
  { value: 'ALL', label: `전체 ${formatCount(statusCounts.value.all)}` },
  { value: 'REPORTED', label: `신고 ${formatCount(statusCounts.value.reported)}` },
  { value: 'HIDDEN', label: `가림 ${formatCount(statusCounts.value.hidden)}` },
  { value: 'DELETED', label: `지움 ${formatCount(statusCounts.value.deleted)}` },
]);

onMounted(async () => {
  await loadEntries(applied.value);

  // 알림에서 눌러 들어온 경우다. 목록만 열면 어느 글이었는지 다시 찾아야 한다.
  // 상세는 번호로 불러오므로 지금 쪽에 없어도 열린다
  const requestedId = toId(route.query.entry);
  if (requestedId !== null) {
    detailId.value = requestedId;
    const query = { ...route.query };
    delete query.entry;
    await router.replace({ query });
  }
});

function loadEntries(searchCondition) {
  return requestEntries({
    ...searchCondition,
    page: page.value - 1,
    size: pageSize.value,
  });
}

function search() {
  page.value = 1;
  applied.value = { ...condition };
  return loadEntries(applied.value);
}

function reload() {
  return loadEntries(applied.value);
}

function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

function changePage(nextPage) {
  page.value = nextPage;
  return reload();
}

function changePageSize(nextSize) {
  pageSize.value = nextSize;
  page.value = 1;
  return reload();
}

function openDetail(row) {
  detailId.value = row.id;
}

async function onChanged(message) {
  notifySuccess(message);
  await reload();
}

function stateLabel(row) {
  if (row.contentPurged) return '파기됨';
  // 쓴 사람이 지운 뒤 운영자가 가린 글도 "지움" 이다. 되살릴 수 없는 글이라 "가림" 과 갈라 둔다
  if (row.deletedByWriter) return '지움';
  if (row.hiddenByAdmin) return '가림';
  return '공개';
}

function toId(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function formatCount(value) {
  return Number(value ?? 0).toLocaleString('ko-KR');
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}
</script>

<template>
  <div class="admin-guestbook-page">
    <AdminPageHeader title="방명록 관리" group="콘텐츠">
      <template #meta>
        전체 {{ formatCount(statusCounts.all) }}개 · 신고 대기 {{ formatCount(statusCounts.reported) }}개
      </template>
    </AdminPageHeader>

    <AdminSearchPanel :collapsible="false" @search="search">
      <AdminTextInput
        v-model="condition.keyword"
        label="검색"
        placeholder="쓴 사람, 내용"
      />
      <AdminSegmented
        v-model="condition.status"
        label="상태"
        :options="statusOptions"
      />
    </AdminSearchPanel>

    <AdminGridToolbar :total-count="totalElements">
      <template #left>
        <AdminPageSize :model-value="pageSize" @update:model-value="changePageSize" />
      </template>
      <template #right>
        <button class="admin-button" type="button" @click="reset">초기화</button>
        <button class="admin-button admin-button--solid" type="button" @click="search">조회</button>
      </template>
    </AdminGridToolbar>

    <AdminDataGrid
      :columns="COLUMNS"
      :rows="entries"
      :loading="loading"
      :error-text="loadError"
      empty-text="조건에 맞는 방명록 글이 없습니다."
      row-clickable
      @row-click="openDetail"
      @retry="reload"
    >
      <template #cell-nickname="{ row }">
        <span class="admin-guestbook__writer">
          <strong>{{ row.nickname }}</strong>
          <small>{{ row.writtenByAdmin ? '관리자' : '회원' }}</small>
        </span>
      </template>

      <template #cell-content="{ row }">
        <span class="admin-guestbook__content">
          <span class="admin-guestbook__flags">
            <span v-if="row.secret" class="admin-badge admin-badge--off">비밀글</span>
            <span v-if="row.edited" class="admin-badge admin-badge--off">수정됨</span>
            <span v-if="row.replyContent" class="admin-badge admin-badge--on">답글 있음</span>
          </span>
          <span v-if="row.contentPurged" class="admin-guestbook__purged">보관 기간이 지나 원문을 파기했습니다.</span>
          <span v-else class="admin-guestbook__text">{{ row.content }}</span>
        </span>
      </template>

      <template #cell-state="{ row }">
        <span class="admin-badge" :class="row.deleted ? 'admin-badge--off' : 'admin-badge--on'">
          {{ stateLabel(row) }}
        </span>
      </template>

      <!-- 처리한 것과 대기를 나눈다. 전체만 보이면 처리해도 숫자가 그대로라 무엇을 안 봤는지 모른다 -->
      <template #cell-reports="{ row }">
        <span
          v-if="row.reportCount > 0"
          class="admin-badge admin-badge--report"
          :class="{ 'admin-badge--report-pending': row.unhandledReportCount > 0 }"
        >
          {{ row.unhandledReportCount > 0 ? `${row.unhandledReportCount}건 대기` : `${row.reportCount}건 처리됨` }}
        </span>
        <span v-else class="admin-guestbook__none">-</span>
      </template>

      <template #cell-createdAt="{ value }">
        {{ formatDateTime(value) }}
      </template>
    </AdminDataGrid>

    <AdminPagination :page="page" :total-pages="totalPages" @update:page="changePage" />

    <AdminGuestbookDetailModal
      :entry-id="detailId"
      @close="detailId = null"
      @changed="onChanged"
    />
  </div>
</template>

<style scoped>
.admin-guestbook__writer {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admin-guestbook__writer small {
  color: var(--admin-meta);
  font-size: 11.5px;
}

.admin-guestbook__content {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.admin-guestbook__flags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.admin-guestbook__flags:empty {
  display: none;
}

/* 표 한 줄이 글 하나라 두 줄까지만 보인다. 전체는 상세 창에서 읽는다 */
.admin-guestbook__text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.6;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.admin-guestbook__purged,
.admin-guestbook__none {
  color: var(--admin-meta);
}
</style>
