<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getAdminComments } from '../api/adminApi';
import { canReply } from '../data/adminCommentRules';
import AdminCommentDetailModal from '../components/AdminCommentDetailModal.vue';
import AdminCommentReplyDialog from '../components/AdminCommentReplyDialog.vue';
import AdminCommentRow from '../components/AdminCommentRow.vue';
import AdminCommentVisibilityDialog from '../components/AdminCommentVisibilityDialog.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import { notifySuccess } from '../../../shared/toast/toastStore';

const FILTERS = ['ALL', 'UNANSWERED', 'REPORTED', 'HIDDEN'];
const EMPTY_CONDITION = { status: 'ALL', keyword: '' };

const route = useRoute();
const router = useRouter();
const condition = reactive({
  ...EMPTY_CONDITION,
  status: FILTERS.includes(String(route.query.status)) ? String(route.query.status) : 'ALL',
});
const applied = ref({ ...condition });

// 회원 관리에서 댓글 수를 눌러 들어오면 그 회원 댓글만 본다. 검색 칸과 따로 둔다 —
// 초기화를 눌러도 풀리면 왜 목록이 바뀌었는지 모른다
const memberFilter = ref(toId(route.query.member));
const comments = ref([]);
const statusCounts = ref({ all: 0, unanswered: 0, hidden: 0, reported: 0 });
const totalElements = ref(0);
const totalPages = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const loadError = ref('');

const replyTarget = ref(null);
const visibilityTarget = ref(null);

/** 상세 창에 띄운 댓글 번호. 신고 배지를 눌러도 같은 창이 열린다 */
const detailId = ref(null);

const memberLabel = computed(() => {
  const nickname = comments.value[0]?.nickname;
  return nickname ? `${nickname} 회원` : `회원 #${memberFilter.value}`;
});

const statusOptions = computed(() => [
  { value: 'ALL', label: `전체 ${formatCount(statusCounts.value.all)}` },
  { value: 'UNANSWERED', label: `미답변 ${formatCount(statusCounts.value.unanswered)}` },
  { value: 'REPORTED', label: `신고 ${formatCount(statusCounts.value.reported)}` },
  { value: 'HIDDEN', label: `숨김 ${formatCount(statusCounts.value.hidden)}` },
]);

onMounted(async () => {
  await loadComments(applied.value);

  const requestedReplyId = Number(route.query.reply);
  if (Number.isFinite(requestedReplyId)) {
    const target = comments.value.find((comment) => comment.id === requestedReplyId);
    if (target && canReply(target)) {
      openReply(target);
    }

    const query = { ...route.query };
    delete query.reply;
    await router.replace({ query });
  }

  // 신고 알림에서 눌러 들어온 경우다. 목록만 열어 주면 어느 댓글이었는지 다시 찾아야
  // 하고, 신고 건수와 사유는 이 창을 열어야 보인다. 상세는 번호로 불러오므로
  // 지금 페이지 목록에 없어도 열린다
  const requestedReportId = toId(route.query.report);
  if (requestedReportId !== null) {
    openDetail(requestedReportId);

    const query = { ...route.query };
    delete query.report;
    await router.replace({ query });
  }
});

async function loadComments(searchCondition) {
  loading.value = true;
  loadError.value = '';

  try {
    const result = await getAdminComments({
      ...searchCondition,
      memberId: memberFilter.value,
      page: page.value - 1,
      size: pageSize.value,
    });

    comments.value = result.items;
    statusCounts.value = result.statusCounts;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  } catch (error) {
    console.error(error);
    comments.value = [];
    loadError.value = '댓글 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  applied.value = { ...condition };
  return loadComments(applied.value);
}

function reload() {
  return loadComments(applied.value);
}

function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

async function clearMemberFilter() {
  const query = { ...route.query };
  delete query.member;
  await router.replace({ query });

  memberFilter.value = null;
  page.value = 1;
  await reload();
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

function openReply(comment) {
  replyTarget.value = comment;
}

async function onReplied() {
  replyTarget.value = null;
  await reload();
  notifySuccess('답글을 등록했습니다.');
}

function askVisibility(comment) {
  visibilityTarget.value = comment;
}

async function onVisibilityChanged(hidden) {
  visibilityTarget.value = null;
  await reload();
  notifySuccess(hidden ? '댓글을 숨겼습니다.' : '댓글을 다시 공개했습니다.');
}

function openDetail(commentId) {
  detailId.value = commentId;
}

function closeDetail() {
  detailId.value = null;
}

// 가리기 확인창을 상세 창 위에 겹치지 않는다. 상세를 닫고 확인창만 띄운다
function hideFromDetail(target) {
  closeDetail();
  askVisibility(target);
}

function toId(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function formatCount(value) {
  return Number(value ?? 0).toLocaleString('ko-KR');
}
</script>

<template>
  <div class="admin-comment-page">
    <AdminPageHeader title="댓글 관리" group="콘텐츠">
      <template #meta>
        전체 {{ formatCount(statusCounts.all) }}개 · 미답변 {{ formatCount(statusCounts.unanswered) }}개
      </template>
    </AdminPageHeader>

    <AdminSearchPanel :collapsible="false" @search="search">
      <AdminTextInput
        v-model="condition.keyword"
        label="검색"
        placeholder="작성자, 댓글 내용, 글 제목"
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
        <span v-if="memberFilter !== null" class="admin-comment-member-filter">
          {{ memberLabel }}의 댓글만 보는 중
          <button type="button" @click="clearMemberFilter">전체 보기</button>
        </span>
      </template>
      <template #right>
        <button class="admin-button" type="button" @click="reset">초기화</button>
        <button class="admin-button admin-button--solid" type="button" @click="search">조회</button>
      </template>
    </AdminGridToolbar>

    <div class="admin-comment-feed" aria-live="polite">
      <div v-if="loading" class="admin-comment-feed__skeleton" aria-hidden="true">
        <AdminCommentRow v-for="index in 5" :key="index" />
      </div>
      <div v-else-if="loadError" class="admin-comment-feed__message admin-comment-feed__message--error">
        <span>{{ loadError }}</span>
        <button type="button" @click="reload">다시 시도</button>
      </div>
      <div v-else-if="comments.length === 0" class="admin-comment-feed__message">
        조건에 맞는 댓글이 없습니다.
      </div>

      <AdminCommentRow
        v-for="comment in comments"
        v-else
        :key="comment.id"
        :comment="comment"
        @open="openDetail"
        @reply="openReply"
        @visibility="askVisibility"
      />
    </div>

    <AdminPagination
      :page="page"
      :total-pages="totalPages"
      @update:page="changePage"
    />

    <AdminCommentReplyDialog
      :target="replyTarget"
      @close="replyTarget = null"
      @replied="onReplied"
    />

    <AdminCommentVisibilityDialog
      :target="visibilityTarget"
      @close="visibilityTarget = null"
      @changed="onVisibilityChanged"
    />

    <AdminCommentDetailModal
      :comment-id="detailId"
      @close="closeDetail"
      @hide="hideFromDetail"
      @changed="reload"
    />
  </div>
</template>

<style scoped>
/* ── 댓글 관리 ─────────────────────────────── */

.admin-comment-member-filter {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: var(--admin-meta);
  font-size: 13px;
}

.admin-comment-member-filter button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--admin-title);
  font: inherit;
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}

.admin-comment-page {
  width: 100%;
}

.admin-comment-feed {
  border-top: 1px solid var(--admin-card-line);
  border-bottom: 1px solid var(--admin-card-line);
  background: var(--admin-card);
}

.admin-comment-feed__message {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 180px;
  color: var(--admin-text-muted);
  font-size: 13px;
}

.admin-comment-feed__message--error {
  color: var(--admin-error-text);
}

.admin-comment-feed__message button {
  border: 0;
  background: none;
  color: var(--admin-accent-dark);
  font-weight: 700;
  cursor: pointer;
}
</style>
