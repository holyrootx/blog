<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  deleteAdminPost,
  getAdminCategories,
  getAdminPosts,
  publishAdminPost,
  purgeAdminPost,
  restoreAdminPost,
  unpublishAdminPost,
} from '../api/adminApi';
import { notifySuccess } from '../../../shared/toast/toastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSelect from '../components/AdminSelect.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';

// 댓글 수·좋아요·썸네일은 열에 두지 않는다.
// 값이 늘 0이거나 비어 있어서 정보가 아니라 잡음이 된다
const COLUMNS = [
  { key: 'title', label: '제목' },
  { key: 'categoryName', label: '카테고리', width: '110px' },
  { key: 'status', label: '상태', width: '90px', align: 'center' },
  { key: 'date', label: '날짜', width: '150px' },
  { key: 'views', label: '조회', width: '90px', align: 'right' },
  // 액션이 셋뿐이라 더보기(⋯) 드롭다운을 두지 않는다. 한 번 더 여는 것보다 버튼 셋이 짧다
  { key: 'actions', label: '관리', width: '190px', align: 'right' },
];

const EMPTY_CONDITION = {
  status: '',
  categoryId: '',
  keyword: '',
};

// condition — 입력창의 값. applied — 조회로 적용된 값
const condition = reactive({ ...EMPTY_CONDITION });
const applied = ref({ ...EMPTY_CONDITION });

const posts = ref([]);
const statusCounts = ref({ all: 0, published: 0, scheduled: 0, private: 0, draft: 0, trash: 0 });
const isTrash = computed(() => applied.value.status === 'TRASH');
const columns = computed(() => isTrash.value
  ? COLUMNS.map((column) => column.key === 'views'
    ? { key: 'restoreUntil', label: '복구 기한', width: '185px' }
    : column)
  : COLUMNS);
const totalElements = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const loadError = ref('');

const page = ref(1);
const pageSize = ref(20);

const router = useRouter();
const categories = ref([]);

// 확인창. 어떤 동작을 무슨 글에 할지 담아둔다
const confirmTarget = ref(null);
const confirmAction = ref('');
const working = ref(false);
const actionError = ref('');
const purgeTitle = ref('');
const canConfirm = computed(() => !working.value && confirmTarget.value !== null
  && (confirmAction.value !== 'purge' || purgeTitle.value === confirmTarget.value.title));

const categoryOptions = computed(() => [
  { value: '', label: '전체' },
  ...categories.value.map((category) => ({
    value: String(category.id),
    label: category.name,
  })),
]);

// 상태 필터에 건수를 붙인다. 건수는 status 와 무관하게 전체 기준이라
// 발행만 보고 있어도 임시저장이 몇 개인지 보인다
const statusOptions = computed(() => [
  { value: '', label: `전체 ${formatCount(statusCounts.value.all)}` },
  { value: 'PUBLISHED', label: `발행 ${formatCount(statusCounts.value.published)}` },
  { value: 'SCHEDULED', label: `예약 ${formatCount(statusCounts.value.scheduled)}` },
  { value: 'PRIVATE', label: `비공개 ${formatCount(statusCounts.value.private)}` },
  { value: 'DRAFT', label: `임시저장 ${formatCount(statusCounts.value.draft)}` },
  { value: 'TRASH', label: `휴지통 ${formatCount(statusCounts.value.trash)}` },
]);

function formatCount(count) {
  return (count ?? 0).toLocaleString('ko-KR');
}

const isEmptyBeforeFirstPost = computed(() => statusCounts.value.all === 0
  && applied.value.keyword === ''
  && applied.value.categoryId === ''
  && applied.value.status === '');

async function loadPosts(searchCondition) {
  loading.value = true;
  loadError.value = '';

  try {
    const result = await getAdminPosts({
      ...searchCondition,
      status: searchCondition.status === 'TRASH' ? '' : searchCondition.status,
      trash: searchCondition.status === 'TRASH',
      // 화면은 1쪽부터, 서버는 0쪽부터 센다
      page: page.value - 1,
      size: pageSize.value,
    });

    posts.value = result.items;
    statusCounts.value = result.statusCounts;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  } catch (error) {
    console.error(error);
    posts.value = [];
    loadError.value = '글 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

// 조회 — 목록이 바뀌는 것은 이 함수를 거칠 때뿐이다
function search() {
  page.value = 1;
  applied.value = { ...condition };
  return loadPosts(applied.value);
}

// 쪽 이동·개수 변경은 조건을 그대로 두고 다시 읽는다
function reload() {
  return loadPosts(applied.value);
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

// 초기화 — 입력창만 비운다. 목록은 다음 조회 때 바뀐다
function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

/** 발행된 글은 발행일, 그 밖에는 작성일. 어느 쪽인지 셀 안에 적어준다 */
function toDateCell(post) {
  if (post.deletedAt) {
    return { label: '삭제', value: formatDate(post.deletedAt) };
  }
  const isPublished = post.status === 'PUBLISHED' && post.publishedAt;

  return {
    label: isPublished ? '발행' : '작성',
    value: formatDate(isPublished ? post.publishedAt : post.createdAt),
  };
}

function formatDate(value) {
  if (!value) {
    return '-';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

const STATUS_LABELS = {
  PUBLISHED: '발행',
  PRIVATE: '비공개',
  DRAFT: '임시저장',
  SCHEDULED: '예약',
};

const CONFIRM_TEXTS = {
  publish: {
    title: '이 글을 발행할까요?',
    description: '공개 화면과 검색엔진이 이 글을 보게 됩니다.',
  },
  unpublish: {
    title: '이 글을 내릴까요?',
    description: '공개 화면에서 사라집니다. 발행일은 그대로 남습니다.',
  },
  delete: {
    title: '이 글을 삭제할까요?',
    description: '휴지통으로 이동합니다. 댓글과 이미지는 보관하며 30일 이내에 복구할 수 있습니다.',
  },
  restore: {
    title: '이 글을 복구할까요?',
    description: '임시저장 상태로 복구합니다. 내용을 확인한 뒤 직접 발행해 주세요.',
  },
  purge: {
    title: '이 글을 영구 삭제할까요?',
    description: '제목·요약·본문과 남아 있는 글 변경 기록을 삭제합니다. 다른 곳에서 쓰지 않는 첨부 이미지도 정리하며, 삭제한 글은 복구할 수 없습니다.',
  },
};

const confirmText = computed(() => CONFIRM_TEXTS[confirmAction.value] ?? { title: '', description: '' });

function openPost(post) {
  if (post.deletedAt) return;
  router.push({ name: 'admin-post-edit', params: { postId: post.id } });
}

function askAction(action, post) {
  confirmAction.value = action;
  confirmTarget.value = post;
  actionError.value = '';
  purgeTitle.value = '';
}

async function runAction() {
  if (!canConfirm.value) {
    return;
  }

  working.value = true;
  actionError.value = '';

  const postId = confirmTarget.value.id;

  try {
    let done = '';

    if (confirmAction.value === 'publish') {
      await publishAdminPost(postId);
      done = '발행했습니다.';
    } else if (confirmAction.value === 'unpublish') {
      await unpublishAdminPost(postId);
      done = '글을 내렸습니다.';
    } else if (confirmAction.value === 'restore') {
      await restoreAdminPost(postId);
      done = '임시저장으로 복구했습니다.';
    } else if (confirmAction.value === 'purge') {
      await purgeAdminPost(postId, {
        title: purgeTitle.value,
        deletedAt: confirmTarget.value.deletedAt,
      });
      done = '글을 영구 삭제했습니다.';
    } else {
      await deleteAdminPost(postId);
      done = '휴지통으로 이동했습니다.';
    }

    confirmTarget.value = null;
    await reload();
    if (!loadError.value && page.value > Math.max(1, totalPages.value)) {
      page.value = Math.max(1, totalPages.value);
      await reload();
    }

    // 목록이 새로 그려지는 것만으로는 무엇이 바뀌었는지 알기 어렵다
    notifySuccess(done);
  } catch (error) {
    // 서버가 준 메시지를 그대로 보여준다 (요약 없음·본문 없음 등)
    actionError.value = error.message;
  } finally {
    working.value = false;
  }
}

async function loadCategories() {
  try {
    categories.value = await getAdminCategories();
  } catch (error) {
    // 카테고리를 못 불러와도 목록은 봐야 하므로 조건만 비운다
    console.warn(error);
    categories.value = [];
  }
}

onMounted(() => {
  loadCategories();
  search();
});
</script>

<template>
  <div class="admin-post-page">
    <AdminPageHeader>
      <template #actions>
        <RouterLink class="admin-button admin-button--solid" :to="{ name: 'admin-post-new' }">
          새 글 쓰기
        </RouterLink>
      </template>
    </AdminPageHeader>

    <AdminSearchPanel @search="search">
      <AdminTextInput v-model="condition.keyword" label="제목" placeholder="제목 검색" />
      <AdminSelect v-model="condition.categoryId" label="카테고리" :options="categoryOptions" />
      <AdminSegmented v-model="condition.status" label="상태" :options="statusOptions" />
    </AdminSearchPanel>

    <AdminGridToolbar :total-count="totalElements">
      <template #left>
        <AdminPageSize :model-value="pageSize" @update:model-value="changePageSize" />
      </template>

      <template #right>
        <button class="admin-button admin-button--ghost" type="button" @click="reset">
          초기화
        </button>
        <button class="admin-button admin-button--solid" type="button" @click="search">
          조회
        </button>
      </template>
    </AdminGridToolbar>

    <AdminDataGrid
      :columns="columns"
      :rows="posts"
      :loading="loading"
      :error-text="loadError"
      :empty-text="isEmptyBeforeFirstPost
        ? '아직 쓴 글이 없습니다.'
        : '조회조건에 맞는 글이 없습니다.'"
      :row-clickable="!isTrash"
      @row-click="openPost"
      @retry="reload"
    >
      <template #cell-status="{ value, row }">
        <span
          class="admin-badge"
          :class="!row.deletedAt && value === 'PUBLISHED' ? 'admin-badge--on' : 'admin-badge--off'"
        >
          {{ row.deletedAt ? '삭제됨' : (STATUS_LABELS[value] ?? value) }}
        </span>
      </template>

      <!-- 임시저장 글은 발행일이 없어서 날짜를 한 칸에 모으고 어느 날짜인지 적는다 -->
      <template #cell-date="{ row }">
        <span class="admin-post__date">
          {{ toDateCell(row).value }}
          <em>{{ toDateCell(row).label }}</em>
        </span>
      </template>

      <template #cell-views="{ value }">
        {{ value.toLocaleString('ko-KR') }}
      </template>

      <template #cell-restoreUntil="{ row }">
        <span :title="row.restoreUntil">
          {{ row.restorable
            ? new Date(row.restoreUntil).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
            : '복구 기한 만료' }}
        </span>
      </template>

      <template #cell-actions="{ row }">
        <span class="admin-post__actions" @click.stop>
          <button
            v-if="row.deletedAt"
            class="admin-button admin-button--ghost admin-button--small"
            type="button"
            :disabled="!row.restorable || working"
            @click="askAction('restore', row)"
          >
            복구
          </button>
          <button
            v-else-if="row.status !== 'PUBLISHED'"
            class="admin-button admin-button--ghost admin-button--small"
            type="button"
            @click="askAction('publish', row)"
          >
            발행
          </button>
          <button
            v-else
            class="admin-button admin-button--ghost admin-button--small"
            type="button"
            @click="askAction('unpublish', row)"
          >
            내리기
          </button>
          <button
            v-if="!row.deletedAt"
            class="admin-button admin-button--danger admin-button--small"
            type="button"
            @click="askAction('delete', row)"
          >
            삭제
          </button>
          <button
            v-else
            class="admin-button admin-button--danger admin-button--small"
            type="button"
            :disabled="!row.purgeable || working"
            :title="row.purgeable ? undefined : '삭제 후 30일이 지나면 영구 삭제할 수 있습니다'"
            @click="askAction('purge', row)"
          >
            영구 삭제
          </button>
        </span>
      </template>
    </AdminDataGrid>

    <AdminPagination :page="page" :total-pages="totalPages" @update:page="changePage" />

    <!-- 발행·내리기·삭제 확인. 셋 다 공개 화면에 바로 영향을 준다 -->
    <BaseModal variant="admin"
      :open="confirmTarget !== null"
      :title="confirmText.title"
      :description="confirmText.description"
      size="small"
      @close="!working && (confirmTarget = null)"
    >
      <p class="admin-post__confirm">{{ confirmTarget?.title }}</p>

      <template v-if="confirmAction === 'purge'">
        <p>
          연결된 댓글 원문은 신고 확인을 위해 6개월 보관한 뒤 정리합니다.
          이미 삭제된 댓글의 파기 기한은 늦추지 않습니다. 신고·조치 기록은 남습니다.
        </p>
        <label class="admin-field">
          <span class="admin-field__label">삭제할 글 제목을 그대로 입력해 주세요</span>
          <input v-model="purgeTitle" class="admin-field__input" type="text" autocomplete="off" :disabled="working" />
        </label>
      </template>

      <p v-if="actionError" class="admin-form-error">{{ actionError }}</p>

      <template #footer><button
          class="admin-button"
          :class="['delete', 'purge'].includes(confirmAction) ? 'admin-button--danger' : 'admin-button--solid'"
          type="button"
          :disabled="!canConfirm"
          @click="runAction"
        >
          {{ working ? '처리 중…' : confirmAction === 'purge' ? '영구 삭제' : confirmAction === 'delete' ? '삭제' : '확인' }}
        </button>
      
        <button class="admin-button admin-button--ghost" type="button" :disabled="working" @click="confirmTarget = null">
          취소
        </button>
        </template>
    </BaseModal>
  </div>
</template>
