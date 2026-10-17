<script setup>
import { computed, ref, watch } from 'vue';

import { getAdminPostViews } from '../api/adminApi';
import { formatSeoulDateTime } from '../../../shared/time/seoulTime';
import BaseModal from '../../../shared/components/BaseModal.vue';
import AdminDataGrid from './AdminDataGrid.vue';
import AdminPagination from './AdminPagination.vue';
import AdminSegmented from './AdminSegmented.vue';

/**
 * 글 하나의 조회 기록.
 *
 * <p>누적 조회수와 조회 기록을 나란히 둔다. 둘은 세는 기준이 달라 숫자가 다르다 —
 * 누적 조회수는 세션마다 한 번, 기록 시작 전부터 쌓인 값이고, 조회 기록은 같은 사람이
 * 30분 안에 다시 연 것만 하나로 친다. 기준을 적어 두지 않으면 둘 중 하나가 틀렸다고 읽는다.</p>
 */
const props = defineProps({
  // { id, title, views } — 비우면 닫힌다
  post: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['close']);

const PAGE_SIZE = 20;

const COLUMNS = [
  { key: 'viewedAt', label: '본 시각', width: '170px' },
  { key: 'viewerType', label: '구분', width: '90px', align: 'center' },
  { key: 'member', label: '회원' },
];

const VIEWER_LABELS = {
  MEMBER: '회원',
  ADMIN: '관리자',
  ANONYMOUS: '비회원',
};

const EMPTY_RESULT = {
  totalViews: 0,
  recordingStartedAt: null,
  counts: { all: 0, member: 0, admin: 0, anonymous: 0 },
  items: [],
  totalElements: 0,
  totalPages: 0,
};

const viewer = ref('');
const page = ref(1);
const result = ref({ ...EMPTY_RESULT });
const loaded = ref(false);
const loading = ref(false);
const loadError = ref('');
let requestSeq = 0;

const viewerOptions = computed(() => [
  { value: '', label: `전체 ${formatCount(result.value.counts.all)}` },
  { value: 'MEMBER', label: `회원 ${formatCount(result.value.counts.member)}` },
  { value: 'ANONYMOUS', label: `비회원 ${formatCount(result.value.counts.anonymous)}` },
  { value: 'ADMIN', label: `관리자 ${formatCount(result.value.counts.admin)}` },
]);

const emptyText = computed(() => {
  if (result.value.counts.all === 0) {
    return '아직 이 글의 조회 기록이 없습니다.';
  }
  return '이 구분의 조회 기록이 없습니다.';
});

watch(() => props.post?.id, (postId) => {
  viewer.value = '';
  page.value = 1;
  result.value = { ...EMPTY_RESULT };
  loaded.value = false;
  loadError.value = '';
  if (postId) load();
}, { immediate: true });

function changeViewer(next) {
  viewer.value = next;
  page.value = 1;
  load();
}

function changePage(next) {
  page.value = next;
  load();
}

async function load() {
  const postId = props.post?.id;
  if (!postId) return;

  const seq = ++requestSeq;
  loading.value = true;
  loadError.value = '';

  try {
    const next = await getAdminPostViews(postId, { viewer: viewer.value, page: page.value, size: PAGE_SIZE });
    if (seq !== requestSeq) return;
    result.value = next;
    loaded.value = true;
  } catch (error) {
    if (seq !== requestSeq) return;
    console.error(error);
    loadError.value = error?.message ?? '조회 기록을 불러오지 못했습니다.';
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

function formatCount(count) {
  return (count ?? 0).toLocaleString('ko-KR');
}
</script>

<template>
  <BaseModal
    variant="admin"
    :open="post !== null"
    title="조회 기록"
    :description="post?.title ?? ''"
    size="large"
    @close="emit('close')"
  >
    <div class="admin-post-views">
      <dl class="admin-post-views__summary" :aria-busy="!loaded">
        <div class="admin-post-views__figure">
          <dt>누적 조회수</dt>
          <dd>
            <span v-if="!loaded" class="ui-skeleton admin-post-views__value-skeleton" aria-hidden="true"></span>
            <template v-else>{{ formatCount(result.totalViews) }}</template>
          </dd>
          <dd class="admin-post-views__hint">세션마다 글 하나에 한 번 오르는 값입니다. 기록을 시작하기 전부터 쌓였습니다.</dd>
        </div>
        <div class="admin-post-views__figure">
          <dt>조회 기록</dt>
          <dd>
            <span v-if="!loaded" class="ui-skeleton admin-post-views__value-skeleton" aria-hidden="true"></span>
            <template v-else>{{ formatCount(result.counts.all) }}건</template>
          </dd>
          <dd class="admin-post-views__hint">같은 사람이 같은 글을 30분 안에 다시 열면 한 번으로 셉니다. 비회원은 사람 수가 아니라 방문 건수입니다.</dd>
        </div>
      </dl>

      <p class="admin-post-views__since">
        <template v-if="!loaded">&nbsp;</template>
        <template v-else-if="result.recordingStartedAt">
          {{ formatSeoulDateTime(result.recordingStartedAt) }}부터 기록했습니다. 그 전 방문은 기록에 없습니다.
        </template>
        <template v-else>아직 블로그 전체에 조회 기록이 없습니다.</template>
      </p>

      <AdminSegmented
        :model-value="viewer"
        label="구분"
        hide-label
        :options="viewerOptions"
        @update:model-value="changeViewer"
      />

      <AdminDataGrid
        :columns="COLUMNS"
        :rows="result.items"
        :loading="loading && !loadError"
        :error-text="loadError"
        :empty-text="emptyText"
        min-width="560px"
        @retry="load"
      >
        <template #cell-viewedAt="{ value }">
          <time :datetime="value">{{ formatSeoulDateTime(value) }}</time>
        </template>

        <template #cell-viewerType="{ value }">
          <span
            class="admin-badge"
            :class="value === 'ANONYMOUS' ? 'admin-badge--off' : 'admin-badge--on'"
          >{{ VIEWER_LABELS[value] ?? value }}</span>
        </template>

        <template #cell-member="{ row }">
          <span v-if="row.viewerType === 'ANONYMOUS'" class="admin-post-views__anonymous">비회원 방문</span>
          <span v-else class="admin-post-views__member">
            <span>{{ row.nickname || '알 수 없는 회원' }}</span>
            <em>#{{ row.memberId }}</em>
            <span
              v-if="row.memberStatus && row.memberStatus !== 'ACTIVE'"
              class="admin-badge admin-badge--off"
            >{{ row.memberStatusName || row.memberStatus }}</span>
          </span>
        </template>
      </AdminDataGrid>

      <AdminPagination :page="page" :total-pages="result.totalPages" @update:page="changePage" />
    </div>
  </BaseModal>
</template>
