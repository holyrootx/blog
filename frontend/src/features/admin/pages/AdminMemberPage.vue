<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
  getAdminMembers,
  suspendAdminMember,
  unsuspendAdminMember,
} from '../api/adminApi';
import { notifySuccess } from '../data/adminToastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';

const COLUMNS = [
  { key: 'member', label: '회원' },
  { key: 'provider', label: '가입 경로', width: '100px' },
  { key: 'status', label: '상태', width: '90px', align: 'center' },
  { key: 'commentCount', label: '댓글', width: '80px', align: 'right' },
  { key: 'createdAt', label: '가입일', width: '120px' },
  { key: 'actions', label: '', width: '110px', align: 'right' },
];

const EMPTY_CONDITION = {
  status: '',
  keyword: '',
};

const condition = reactive({ ...EMPTY_CONDITION });
const applied = ref({ ...EMPTY_CONDITION });
const members = ref([]);
const statusCounts = ref({ all: 0, active: 0, suspended: 0, withdrawn: 0 });
const page = ref(1);
const pageSize = ref(20);
const totalElements = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const loadError = ref('');
const actionTarget = ref(null);
const action = ref('');
const actionPending = ref(false);
const actionError = ref('');

const statusOptions = computed(() => [
  { value: '', label: `전체 ${formatCount(statusCounts.value.all)}` },
  { value: 'ACTIVE', label: `활동 ${formatCount(statusCounts.value.active)}` },
  { value: 'SUSPENDED', label: `정지 ${formatCount(statusCounts.value.suspended)}` },
  { value: 'WITHDRAWN', label: `탈퇴 ${formatCount(statusCounts.value.withdrawn)}` },
]);

const confirmText = computed(() => {
  if (action.value === 'suspend') {
    return {
      title: '이 회원을 정지할까요?',
      description: '정지된 회원은 다시 로그인하거나 댓글·반응을 남길 수 없습니다.',
      button: '정지',
    };
  }

  return {
    title: '회원 정지를 해제할까요?',
    description: '다시 로그인하고 댓글과 반응을 남길 수 있습니다.',
    button: '정지 해제',
  };
});

async function loadMembers(searchCondition) {
  loading.value = true;
  loadError.value = '';

  try {
    const result = await getAdminMembers({
      ...searchCondition,
      page: page.value - 1,
      size: pageSize.value,
    });

    members.value = result.items;
    statusCounts.value = result.statusCounts;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  } catch (error) {
    console.error(error);
    members.value = [];
    loadError.value = '회원 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  applied.value = { ...condition };
  return loadMembers(applied.value);
}

function reload() {
  return loadMembers(applied.value);
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

function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

function askAction(nextAction, member) {
  action.value = nextAction;
  actionTarget.value = member;
  actionError.value = '';
}

async function runAction() {
  if (!actionTarget.value || actionPending.value) {
    return;
  }

  actionPending.value = true;
  actionError.value = '';

  try {
    if (action.value === 'suspend') {
      await suspendAdminMember(actionTarget.value.id);
      notifySuccess('회원을 정지했습니다.');
    } else {
      await unsuspendAdminMember(actionTarget.value.id);
      notifySuccess('회원 정지를 해제했습니다.');
    }

    actionTarget.value = null;
    await reload();
  } catch (error) {
    actionError.value = error?.message ?? '회원 상태를 변경하지 못했습니다.';
  } finally {
    actionPending.value = false;
  }
}

function formatCount(value) {
  return Number(value ?? 0).toLocaleString('ko-KR');
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
  ACTIVE: '활동',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
};

const PROVIDER_LABELS = {
  GOOGLE: 'Google',
  KAKAO: 'Kakao',
  NAVER: 'Naver',
  LOCAL: 'Local',
};

onMounted(search);
</script>

<template>
  <div class="admin-member-page">
    <AdminPageHeader>
      <template #meta>일반 회원의 활동 상태를 관리합니다.</template>
    </AdminPageHeader>

    <AdminSearchPanel @search="search">
      <AdminTextInput
        v-model="condition.keyword"
        label="회원"
        placeholder="닉네임 또는 이메일"
      />
      <AdminSegmented v-model="condition.status" label="상태" :options="statusOptions" />
    </AdminSearchPanel>

    <AdminGridToolbar :total-count="totalElements">
      <template #left>
        <AdminPageSize :model-value="pageSize" @update:model-value="changePageSize" />
      </template>

      <template #right>
        <button class="admin-button admin-button--ghost" type="button" @click="reset">초기화</button>
        <button class="admin-button admin-button--solid" type="button" @click="search">조회</button>
      </template>
    </AdminGridToolbar>

    <AdminDataGrid
      :columns="COLUMNS"
      :rows="members"
      :loading="loading"
      :error-text="loadError"
      empty-text="조회조건에 맞는 회원이 없습니다."
      @retry="reload"
    >
      <template #cell-member="{ row }">
        <span class="admin-member__identity">
          <strong>{{ row.nickname }}</strong>
          <small>{{ row.email || '이메일 미제공' }}</small>
        </span>
      </template>

      <template #cell-provider="{ value }">
        {{ PROVIDER_LABELS[value] ?? value }}
      </template>

      <template #cell-status="{ value }">
        <span
          class="admin-badge"
          :class="value === 'ACTIVE' ? 'admin-badge--on'
            : value === 'SUSPENDED' ? 'admin-badge--danger' : 'admin-badge--off'"
        >
          {{ STATUS_LABELS[value] ?? value }}
        </span>
      </template>

      <template #cell-commentCount="{ value }">
        {{ formatCount(value) }}개
      </template>

      <template #cell-createdAt="{ value }">
        {{ formatDate(value) }}
      </template>

      <template #cell-actions="{ row }">
        <span class="admin-member__actions">
          <button
            v-if="row.status === 'ACTIVE'"
            class="admin-button admin-button--danger admin-button--small"
            type="button"
            @click="askAction('suspend', row)"
          >
            정지
          </button>
          <button
            v-else-if="row.status === 'SUSPENDED'"
            class="admin-button admin-button--ghost admin-button--small"
            type="button"
            @click="askAction('unsuspend', row)"
          >
            해제
          </button>
        </span>
      </template>
    </AdminDataGrid>

    <AdminPagination :page="page" :total-pages="totalPages" @update:page="changePage" />

    <BaseModal
      variant="admin"
      :open="actionTarget !== null"
      :title="confirmText.title"
      :description="confirmText.description"
      size="small"
      @close="actionTarget = null"
    >
      <p class="admin-member__confirm">
        <strong>{{ actionTarget?.nickname }}</strong>
        <span>{{ actionTarget?.email || '이메일 미제공' }}</span>
      </p>

      <p v-if="actionError" class="admin-form-error">{{ actionError }}</p>

      <template #footer>
        <button
          class="admin-button"
          :class="action === 'suspend' ? 'admin-button--danger' : 'admin-button--solid'"
          type="button"
          :disabled="actionPending"
          @click="runAction"
        >
          {{ actionPending ? '처리 중…' : confirmText.button }}
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="actionTarget = null">
          취소
        </button>
      </template>
    </BaseModal>
  </div>
</template>
