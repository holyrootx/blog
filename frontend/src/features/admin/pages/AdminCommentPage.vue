<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  dismissAdminCommentReports,
  getAdminCommentModeration,
  getAdminComments,
  replyAdminComment,
  updateAdminCommentVisibility,
} from '../api/adminApi';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import { notifySuccess } from '../data/adminToastStore';

const FILTERS = ['ALL', 'UNANSWERED', 'REPORTED', 'HIDDEN'];
const EMPTY_CONDITION = { status: 'ALL', keyword: '' };

const route = useRoute();
const router = useRouter();
const condition = reactive({
  ...EMPTY_CONDITION,
  status: FILTERS.includes(String(route.query.status)) ? String(route.query.status) : 'ALL',
});
const applied = ref({ ...condition });
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

/**
 * 신고 내역과 조치 이력을 펼쳐 볼 댓글.
 *
 * 둘을 함께 본다. 이미 가린 댓글인 줄 모르고 또 가리는 일을 막으려면
 * 신고만 봐서는 안 된다.
 */
const moderationTarget = ref(null);
const moderationDetail = ref({ reports: [], moderations: [] });
const moderationLoading = ref(false);
const moderationError = ref('');
const dismissSubmitting = ref(false);

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
  // 하고, 신고 건수와 사유는 이 창을 열어야 보인다
  const requestedReportId = Number(route.query.report);
  if (Number.isFinite(requestedReportId)) {
    const target = comments.value.find((comment) => comment.id === requestedReportId);
    if (target) {
      await openModeration(target);
    }

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
  } finally {
    visibilitySubmitting.value = false;
  }
}

async function openModeration(comment) {
  moderationTarget.value = comment;
  moderationError.value = '';
  moderationLoading.value = true;
  moderationDetail.value = { reports: [], moderations: [] };

  try {
    moderationDetail.value = await getAdminCommentModeration(comment.id);
  } catch (error) {
    moderationError.value = error?.message ?? '신고 내역을 불러오지 못했습니다.';
  } finally {
    moderationLoading.value = false;
  }
}

function closeModeration() {
  if (dismissSubmitting.value) {
    return;
  }

  moderationTarget.value = null;
  moderationError.value = '';
}

/**
 * 신고를 봤지만 댓글은 그대로 둔다.
 *
 * 이게 없으면 "문제 없음" 이라는 판단을 남길 자리가 없어서, 같은 신고를 볼 때마다
 * 처음부터 다시 읽게 된다.
 */
async function dismissReports() {
  if (!moderationTarget.value || dismissSubmitting.value) {
    return;
  }

  dismissSubmitting.value = true;
  moderationError.value = '';

  try {
    await dismissAdminCommentReports(moderationTarget.value.id, visibilityReason.value || null);

    moderationTarget.value = null;
    visibilityReason.value = '';
    await load();
    notifySuccess('신고를 처리했습니다. 댓글은 그대로 둡니다.');
  } catch (error) {
    moderationError.value = error?.message ?? '신고를 처리하지 못했습니다.';
  } finally {
    dismissSubmitting.value = false;
  }
}

function commentState(comment) {
  if (comment.hidden) return '숨김';
  if (comment.parentId !== null) return '답글';
  if (comment.memberRole === 'ADMIN') return '관리자 댓글';
  return comment.answered ? '답변 완료' : '미답변';
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
        class="admin-comment-row"
        :class="{ 'admin-comment-row--hidden': comment.hidden }"
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
              :class="comment.hidden ? 'admin-badge--off' : 'admin-badge--on'"
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
              @click="openModeration(comment)"
            >
              신고 {{ comment.unhandledReportCount > 0
                ? `${comment.unhandledReportCount}건 대기`
                : `${comment.reportCount}건 처리됨` }}
            </button>
            <span>{{ formatDateTime(comment.createdAt) }}</span>
            <a :href="`/posts/${comment.postId}`" target="_blank" rel="noopener noreferrer">
              {{ comment.postTitle }}
            </a>
          </div>
          <p>{{ comment.content }}</p>
          <span v-if="comment.parentId !== null" class="admin-comment-row__reply-mark">
            답글 · 원댓글 #{{ comment.parentId }}
          </span>
        </div>

        <div class="admin-comment-row__actions">
          <button
            v-if="canReply(comment)"
            class="admin-button admin-button--small"
            type="button"
            @click="openReply(comment)"
          >
            답글
          </button>
          <button
            class="admin-button admin-button--small"
            :class="{ 'admin-button--danger': !comment.hidden }"
            type="button"
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

    <BaseModal variant="admin"
      :open="Boolean(moderationTarget)"
      title="신고 내역과 조치"
      description="신고가 쌓여도 댓글이 저절로 숨겨지지는 않습니다. 읽어 보고 정하세요."
      @close="closeModeration"
    >
      <div v-if="moderationLoading" class="admin-moderation__skeleton" aria-hidden="true">
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
      </div>
      <p v-else-if="moderationError" class="admin-comment-reply-form__error" role="alert">
        {{ moderationError }}
      </p>

      <template v-else>
        <p class="admin-moderation__quote">{{ moderationTarget?.content }}</p>

        <h3 class="admin-moderation__title">신고 {{ moderationDetail.reports.length }}건</h3>
        <ul class="admin-moderation__list">
          <li v-for="report in moderationDetail.reports" :key="report.id">
            <span class="admin-moderation__reason">{{ report.reasonLabel }}</span>
            <span v-if="report.detail" class="admin-moderation__detail">{{ report.detail }}</span>
            <span class="admin-moderation__time">
              {{ formatDateTime(report.reportedAt) }} · {{ report.handled ? '처리됨' : '대기' }}
            </span>
          </li>
        </ul>

        <h3 class="admin-moderation__title">지금까지의 조치</h3>
        <p v-if="moderationDetail.moderations.length === 0" class="admin-moderation__empty">
          아직 없습니다.
        </p>
        <ul v-else class="admin-moderation__list">
          <li v-for="item in moderationDetail.moderations" :key="item.id">
            <span class="admin-moderation__reason">{{ item.actionLabel }}</span>
            <span v-if="item.reason" class="admin-moderation__detail">{{ item.reason }}</span>
            <span class="admin-moderation__time">
              {{ formatDateTime(item.actedAt) }} · {{ item.adminNickname }}
            </span>
          </li>
        </ul>

        <AdminTextInput
          v-model="visibilityReason"
          label="메모 (선택)"
          placeholder="판단한 이유를 남겨 두면 다음에 다시 읽지 않아도 됩니다"
          :maxlength="200"
        />
      </template>

      <template #footer><button
          class="admin-button"
          type="button"
          :disabled="dismissSubmitting || moderationLoading"
          @click="dismissReports"
        >
          {{ dismissSubmitting ? '처리 중' : '문제 없음' }}
        </button>
        <button
          v-if="moderationTarget && !moderationTarget.hidden"
          class="admin-button admin-button--danger"
          type="button"
          :disabled="dismissSubmitting"
          @click="askVisibility(moderationTarget); closeModeration()"
        >
          가리기
        </button>
      
        <button class="admin-button" type="button" :disabled="dismissSubmitting" @click="closeModeration">
          닫기
        </button>
        </template>
    </BaseModal>
  </div>
</template>
