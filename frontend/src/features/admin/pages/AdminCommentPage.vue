<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  getAdminComments,
  replyAdminComment,
  updateAdminCommentVisibility,
} from '../api/adminApi';
import AdminCommentDetailModal from '../components/AdminCommentDetailModal.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';

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
const replyContent = ref('');
const replyError = ref('');
const replySubmitting = ref(false);

const visibilityTarget = ref(null);
const visibilityError = ref('');
const visibilitySubmitting = ref(false);

/** 가릴 때 남기는 메모. 조치 이력에 함께 쌓인다 */
const visibilityReason = ref('');

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

const visibilityText = computed(() => {
  if (!visibilityTarget.value) {
    return { title: '', description: '', action: '' };
  }

  return visibilityTarget.value.hidden
    ? {
      title: '이 댓글을 다시 공개할까요?',
      description: '블로그 댓글 영역에 내용과 작성자가 다시 표시됩니다.',
      action: '복구',
    }
    : {
      title: '이 댓글을 숨길까요?',
      description: '답글 구조는 유지하고 공개 화면에서는 내용을 감춥니다.',
      action: '숨김',
    };
});

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

function canReply(comment) {
  return comment.parentId === null
    && comment.memberRole !== 'ADMIN'
    && !comment.postPurged
    && !comment.hidden;
}

function openReply(comment) {
  replyTarget.value = comment;
  replyContent.value = '';
  replyError.value = '';
}

function closeReply() {
  if (replySubmitting.value) {
    return;
  }

  replyTarget.value = null;
  replyContent.value = '';
  replyError.value = '';
}

async function submitReply() {
  const content = replyContent.value.trim();
  if (!content) {
    replyError.value = '답글 내용을 입력해 주세요.';
    return;
  }
  if (replySubmitting.value) {
    return;
  }

  replySubmitting.value = true;
  replyError.value = '';

  try {
    await replyAdminComment(replyTarget.value.id, content);
    replyTarget.value = null;
    replyContent.value = '';
    await reload();
    notifySuccess('답글을 등록했습니다.');
  } catch (error) {
    replyError.value = error?.message ?? '답글을 등록하지 못했습니다.';
  } finally {
    replySubmitting.value = false;
  }
}

function askVisibility(comment) {
  visibilityTarget.value = comment;
  visibilityError.value = '';
  visibilityReason.value = '';
}

function closeVisibility() {
  if (visibilitySubmitting.value) {
    return;
  }

  visibilityTarget.value = null;
  visibilityError.value = '';
}

async function changeVisibility() {
  if (!visibilityTarget.value || visibilitySubmitting.value) {
    return;
  }

  visibilitySubmitting.value = true;
  visibilityError.value = '';

  try {
    const hidden = !visibilityTarget.value.hidden;
    await updateAdminCommentVisibility(
      visibilityTarget.value.id,
      hidden,
      visibilityReason.value || null,
    );
    visibilityTarget.value = null;
    visibilityReason.value = '';
    await reload();
    notifySuccess(hidden ? '댓글을 숨겼습니다.' : '댓글을 다시 공개했습니다.');
  } catch (error) {
    visibilityError.value = error?.message ?? '댓글 상태를 변경하지 못했습니다.';
    notifyError(visibilityError.value);
  } finally {
    visibilitySubmitting.value = false;
  }
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

function commentState(comment) {
  if (comment.contentPurged) return '원문 파기';
  if (comment.postPurged) return '보관 중';
  if (comment.hidden) return '숨김';
  if (comment.parentId !== null) return '답글';
  if (comment.memberRole === 'ADMIN') return '관리자 댓글';
  return comment.answered ? '답변 완료' : '미답변';
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
        <article v-for="index in 5" :key="index" class="admin-comment-row">
          <div class="admin-comment-row__identity">
            <span class="ui-skeleton ui-skeleton--circle admin-comment-row__avatar"></span>
            <div>
              <span class="ui-skeleton"></span>
              <span class="ui-skeleton"></span>
            </div>
          </div>
          <div class="admin-comment-row__body">
            <span class="ui-skeleton"></span>
            <span class="ui-skeleton"></span>
            <span class="ui-skeleton"></span>
          </div>
          <span class="ui-skeleton admin-comment-row__action-skeleton"></span>
        </article>
      </div>
      <div v-else-if="loadError" class="admin-comment-feed__message admin-comment-feed__message--error">
        <span>{{ loadError }}</span>
        <button type="button" @click="reload">다시 시도</button>
      </div>
      <div v-else-if="comments.length === 0" class="admin-comment-feed__message">
        조건에 맞는 댓글이 없습니다.
      </div>

      <article
        v-for="comment in comments"
        v-else
        :key="comment.id"
        class="admin-comment-row admin-comment-row--clickable"
        :class="{ 'admin-comment-row--hidden': comment.hidden }"
        tabindex="0"
        @click="openDetail(comment.id)"
        @keydown.enter.self="openDetail(comment.id)"
      >
        <div class="admin-comment-row__identity">
          <span class="admin-comment-row__avatar" aria-hidden="true">
            {{ Array.from(comment.nickname)[0] ?? '' }}
          </span>
          <div>
            <strong>{{ comment.nickname }}</strong>
            <span>{{ comment.memberRole === 'ADMIN' ? '관리자' : '회원' }}</span>
          </div>
        </div>

        <div class="admin-comment-row__body">
          <div class="admin-comment-row__meta">
            <span
              class="admin-badge"
              :class="comment.hidden || comment.postPurged ? 'admin-badge--off' : 'admin-badge--on'"
            >
              {{ commentState(comment) }}
            </span>
            <!--
              미처리와 전체를 나눠 보여 준다. 전체만 보이면 처리해도 숫자가 그대로라
              무엇을 아직 안 봤는지 알 수 없다
            -->
            <button
              v-if="comment.reportCount > 0"
              class="admin-badge admin-badge--report"
              :class="{ 'admin-badge--report-pending': comment.unhandledReportCount > 0 }"
              type="button"
              @click.stop="openDetail(comment.id)"
            >
              신고 {{ comment.unhandledReportCount > 0
                ? `${comment.unhandledReportCount}건 대기`
                : `${comment.reportCount}건 처리됨` }}
            </button>
            <span>{{ formatDateTime(comment.createdAt) }}</span>
            <span v-if="comment.postPurged">원글 영구 삭제</span>
            <a v-else :href="`/posts/${comment.postId}`" target="_blank" rel="noopener noreferrer" @click.stop>
              {{ comment.postTitle }}
            </a>
          </div>
          <p v-if="comment.contentPurged" class="admin-comment-row__purged">
            보관 기간이 지나 원문을 파기했습니다.
          </p>
          <p v-else>{{ comment.content }}</p>
          <span v-if="comment.parentId !== null" class="admin-comment-row__reply-mark">
            답글 · 원댓글 #{{ comment.parentId }}
          </span>
        </div>

        <div class="admin-comment-row__actions" @click.stop>
          <button
            v-if="canReply(comment)"
            class="admin-button admin-button--small"
            type="button"
            @click="openReply(comment)"
          >
            답글
          </button>
          <!-- 원문을 파기한 댓글은 되살릴 내용이 없다. 서버도 거절한다 -->
          <button
            class="admin-button admin-button--small"
            :class="{ 'admin-button--danger': !comment.hidden }"
            type="button"
            :disabled="comment.contentPurged || comment.postPurged"
            :title="comment.postPurged ? '원글이 영구 삭제되어 댓글 상태를 변경할 수 없습니다' : comment.contentPurged ? '원문을 파기해 되살릴 수 없습니다' : undefined"
            @click="askVisibility(comment)"
          >
            {{ comment.hidden ? '복구' : '숨김' }}
          </button>
        </div>
      </article>
    </div>

    <AdminPagination
      :page="page"
      :total-pages="totalPages"
      @update:page="changePage"
    />

    <BaseModal variant="admin"
      :open="Boolean(replyTarget)"
      title="답글 작성"
      :description="replyTarget ? `${replyTarget.nickname} 님의 댓글에 답글을 남깁니다.` : ''"
      :close-on-backdrop="false"
      @close="closeReply"
    >
      <div class="admin-comment-reply-form">
        <blockquote v-if="replyTarget">{{ replyTarget.content }}</blockquote>
        <label for="admin-comment-reply">답글 내용</label>
        <textarea
          id="admin-comment-reply"
          v-model="replyContent"
          maxlength="1000"
          rows="6"
          placeholder="답글을 입력하세요"
        ></textarea>
        <div class="admin-comment-reply-form__meta">
          <span>{{ replyContent.length }} / 1000</span>
          <span v-if="replyError" class="admin-comment-reply-form__error" role="alert">
            {{ replyError }}
          </span>
        </div>
      </div>
      <template #footer><button
          class="admin-button admin-button--solid"
          type="button"
          :disabled="replySubmitting"
          @click="submitReply"
        >
          {{ replySubmitting ? '등록 중' : '답글 등록' }}
        </button>
      
        <button class="admin-button" type="button" :disabled="replySubmitting" @click="closeReply">
          취소
        </button>
        </template>
    </BaseModal>

    <BaseModal variant="admin"
      :open="Boolean(visibilityTarget)"
      :title="visibilityText.title"
      :description="visibilityText.description"
      size="small"
      @close="closeVisibility"
    >
      <!-- 나중에 글쓴이가 "왜 사라졌냐" 물었을 때 가리킬 것은 여기 적은 말뿐이다 -->
      <AdminTextInput
        v-model="visibilityReason"
        label="사유 (선택)"
        placeholder="조치 이력에 남습니다"
        :maxlength="200"
      />
      <p v-if="visibilityError" class="admin-comment-reply-form__error" role="alert">
        {{ visibilityError }}
      </p>
      <template #footer><button
          class="admin-button"
          :class="visibilityTarget?.hidden ? 'admin-button--solid' : 'admin-button--danger'"
          type="button"
          :disabled="visibilitySubmitting"
          @click="changeVisibility"
        >
          {{ visibilitySubmitting ? '처리 중' : visibilityText.action }}
        </button>
      
        <button class="admin-button" type="button" :disabled="visibilitySubmitting" @click="closeVisibility">
          취소
        </button>
        </template>
    </BaseModal>

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

/* 줄을 누르면 상세 창이 열린다. 줄 안의 단추와 글 제목 링크는 제 일만 한다 */
.admin-comment-row--clickable {
  cursor: pointer;
}

.admin-comment-row--clickable:hover {
  background: var(--admin-surface-hover);
}

.admin-comment-row--clickable:focus-visible {
  outline: 2px solid var(--admin-control-accent);
  outline-offset: -2px;
}

.admin-comment-row__body > p.admin-comment-row__purged {
  color: var(--admin-meta);
  font-style: italic;
}

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
  color: #9b3d2d;
}

.admin-comment-feed__message button {
  border: 0;
  background: none;
  color: var(--admin-accent-dark);
  font-weight: 700;
  cursor: pointer;
}

.admin-comment-feed__skeleton .admin-comment-row__identity > div,
.admin-comment-feed__skeleton .admin-comment-row__body {
  display: grid;
  gap: 9px;
}

.admin-comment-feed__skeleton .admin-comment-row__avatar {
  width: 38px;
  height: 38px;
}

.admin-comment-feed__skeleton .admin-comment-row__identity > div .ui-skeleton:first-child {
  width: 82px;
  height: 15px;
}

.admin-comment-feed__skeleton .admin-comment-row__identity > div .ui-skeleton:last-child {
  width: 52px;
  height: 13px;
}

.admin-comment-feed__skeleton .admin-comment-row__body .ui-skeleton:nth-child(1) {
  width: 120px;
  height: 18px;
}

.admin-comment-feed__skeleton .admin-comment-row__body .ui-skeleton:nth-child(2) {
  width: min(620px, 90%);
  height: 20px;
}

.admin-comment-feed__skeleton .admin-comment-row__body .ui-skeleton:nth-child(3) {
  width: 44%;
  height: 14px;
}

.admin-comment-row__action-skeleton {
  width: 70px;
  height: 32px;
}

.admin-comment-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: 20px;
  align-items: start;
  padding: 22px 20px;
  border-bottom: 1px solid var(--admin-list-line);
}

.admin-comment-row:last-child {
  border-bottom: 0;
}

.admin-comment-row--hidden {
  background: var(--admin-surface-subtle);
}

.admin-comment-row__identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.admin-comment-row__avatar {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--admin-chart-5);
  color: var(--admin-text-strong);
  font-size: 12px;
  font-weight: 800;
}

.admin-comment-row__identity strong,
.admin-comment-row__identity div > span {
  display: block;
}

.admin-comment-row__identity strong {
  overflow: hidden;
  color: var(--admin-text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-comment-row__identity div > span {
  margin-top: 3px;
  color: var(--admin-meta);
  font-size: 11px;
}

.admin-comment-row__body {
  min-width: 0;
}

.admin-comment-row__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--admin-meta);
  font-size: 11.5px;
}

.admin-comment-row__meta a {
  overflow: hidden;
  color: var(--admin-text);
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-comment-row__meta a:hover {
  color: var(--admin-accent-dark);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.admin-comment-row__body > p {
  margin-top: 10px;
  color: var(--admin-text-strong);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-comment-row--hidden .admin-comment-row__body > p {
  color: var(--admin-text-muted);
}

.admin-comment-row__reply-mark {
  display: block;
  margin-top: 8px;
  color: var(--admin-meta);
  font-size: 11px;
}

.admin-comment-row__actions {
  display: flex;
  gap: 6px;
}

.admin-comment-reply-form blockquote {
  margin: 0 0 18px;
  padding: 12px 14px;
  border-left: 3px solid var(--admin-accent-line);
  background: var(--admin-surface-subtle);
  color: var(--admin-text);
  font-size: 13px;
  line-height: 1.65;
}

.admin-comment-reply-form label {
  display: block;
  margin-bottom: 7px;
  color: var(--admin-text-strong);
  font-size: 12px;
  font-weight: 700;
}

.admin-comment-reply-form textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--admin-input-line);
  border-radius: 7px;
  padding: 12px;
  background: var(--admin-card);
  color: var(--admin-text-strong);
  font: inherit;
  font-size: 14px;
  line-height: 1.65;
}

.admin-comment-reply-form textarea:focus {
  border-color: var(--admin-accent);
  outline: none;
}

.admin-comment-reply-form__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 7px;
  color: var(--admin-meta);
  font-size: 11.5px;
}

.admin-comment-reply-form__error {
  color: #9b3d2d;
}

@media (max-width: 900px) {
  .admin-comment-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .admin-comment-row__identity {
    grid-column: 1 / -1;
  }
}
</style>
