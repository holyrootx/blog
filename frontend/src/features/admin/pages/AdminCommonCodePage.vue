<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
  createAdminCommonCode,
  createAdminCommonCodeGroup,
  getAdminCommonCodeGroups,
  getAdminCommonCodes,
  updateAdminCommonCode,
  updateAdminCommonCodeGroup,
} from '../api/adminApi';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';

/**
 * 공통 코드 관리. 왼쪽은 그룹 목록, 오른쪽은 고른 그룹의 코드.
 *
 * 그룹 코드와 코드 값은 만든 뒤 바꾸지 않고, 지우는 대신 사용 여부를 끈다(데이터가 그 값을 가리킨다).
 * 서버 코드(enum)가 쓰는 그룹은 코드 목록을 서버가 정해서, 코드 추가와 사용 여부 변경을 막고
 * 이름·설명·순서만 고친다(KAN-26).
 */
const GROUP_COLUMNS = [
  { key: 'group', label: '그룹' },
  { key: 'codeCount', label: '코드', width: '64px', align: 'right' },
  { key: 'enabled', label: '사용', width: '72px', align: 'center' },
];

const CODE_COLUMNS = [
  { key: 'sortOrder', label: '순서', width: '64px', align: 'center' },
  { key: 'code', label: '코드', width: '26%' },
  { key: 'codeName', label: '이름', width: '24%' },
  { key: 'description', label: '설명' },
  { key: 'enabled', label: '사용', width: '72px', align: 'center' },
];

const ENABLED_FILTERS = [
  { value: '', label: '전체' },
  { value: 'true', label: '사용' },
  { value: 'false', label: '사용 안 함' },
];

const ENABLED_OPTIONS = [
  { value: 'true', label: '사용' },
  { value: 'false', label: '사용 안 함' },
];

const MANAGED_NOTE = '서버 코드와 연결된 그룹이라 코드 추가와 사용 여부 변경은 개발로만 합니다.';
const SCREEN_NOTE = '화면에서 관리하는 그룹입니다. 쓰지 않는 코드는 지우지 않고 사용 안 함으로 끕니다.';

// 두 칸이 위아래로 쌓이는 폭. 이때는 그룹을 고르면 아래 코드 목록으로 내려가 준다
const STACKED_QUERY = '(max-width: 1100px)';

const EMPTY_CONDITION = {
  keyword: '',
  enabled: '',
};

// ── 그룹 목록 ──
const condition = reactive({ ...EMPTY_CONDITION });
const applied = ref({ ...EMPTY_CONDITION });
const groups = ref([]);
const page = ref(1);
const pageSize = ref(20);
const totalElements = ref(0);
const totalPages = ref(0);
const groupsLoading = ref(false);
const groupsLoaded = ref(false);
const groupsError = ref('');

// 오른쪽에 보여 줄 그룹. 목록의 다른 페이지로 넘어가도 보던 그룹은 그대로 둔다
const selectedGroup = ref(null);
const codesPane = ref(null);

// ── 코드 목록 ──
const codes = ref([]);
const codesLoading = ref(false);
const codesError = ref('');
let codesRequest = 0;

// ── 팝업 ──
const groupFormOpen = ref(false);
const groupFormMode = ref('create');
const groupForm = reactive({ groupCode: '', groupName: '', description: '', enabled: 'true', managedByEnum: false, updatedAt: null });
const codeFormOpen = ref(false);
const codeFormMode = ref('create');
const codeForm = reactive({ code: '', codeName: '', description: '', sortOrder: '', enabled: 'true', managedByEnum: false, updatedAt: null });
const saving = ref(false);
const formError = ref('');

const groupsPending = computed(() => !groupsLoaded.value && groupsLoading.value);
const canAddCode = computed(() => Boolean(selectedGroup.value) && !selectedGroup.value.managedByEnum);

// 코드 값은 영문 대문자로만 받는다. 입력하면서 바로 바꿔 보여 준다
const groupCodeInput = computed({
  get: () => groupForm.groupCode,
  set: (value) => { groupForm.groupCode = value.toUpperCase(); },
});
const codeInput = computed({
  get: () => codeForm.code,
  set: (value) => { codeForm.code = value.toUpperCase(); },
});

async function loadGroups() {
  groupsLoading.value = true;
  groupsError.value = '';

  try {
    const result = await getAdminCommonCodeGroups({
      ...applied.value,
      page: page.value - 1,
      size: pageSize.value,
    });

    groups.value = result.items;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;

    // 보던 그룹이 이 목록에 있으면 최신 값으로 바꾸고, 아무것도 안 보고 있으면 첫 그룹을 연다
    const current = selectedGroup.value && result.items.find((group) => group.groupCode === selectedGroup.value.groupCode);
    if (current) {
      selectedGroup.value = current;
    } else if (!selectedGroup.value && result.items.length) {
      selectGroup(result.items[0]);
    }
  } catch (error) {
    console.error(error);
    groups.value = [];
    groupsError.value = '공통 코드 그룹을 불러오지 못했습니다.';
  } finally {
    groupsLoading.value = false;
    groupsLoaded.value = true;
  }
}

function search() {
  page.value = 1;
  applied.value = { ...condition };
  // 새로 검색하면 결과의 첫 그룹을 연다
  selectedGroup.value = null;
  codes.value = [];
  return loadGroups();
}

function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

function changePage(nextPage) {
  page.value = nextPage;
  return loadGroups();
}

function changePageSize(nextSize) {
  pageSize.value = nextSize;
  page.value = 1;
  return loadGroups();
}

function selectGroup(group) {
  const changed = selectedGroup.value?.groupCode !== group.groupCode;
  selectedGroup.value = group;
  if (changed) {
    loadCodes();
  }
}

/** 목록에서 직접 고른 경우. 쌓인 화면에서는 코드 목록이 그룹 목록 아래라 그쪽으로 내려간다 */
function pickGroup(group) {
  selectGroup(group);
  if (window.matchMedia(STACKED_QUERY).matches) {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    codesPane.value?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
  }
}

async function loadCodes() {
  const group = selectedGroup.value;
  if (!group) {
    return;
  }

  // 그룹을 빠르게 바꿔 누르면 늦게 온 이전 응답이 화면을 덮지 않게 한다
  const request = ++codesRequest;
  codesLoading.value = true;
  codesError.value = '';

  try {
    const result = await getAdminCommonCodes(group.groupCode);
    if (request === codesRequest) {
      codes.value = result;
    }
  } catch (error) {
    console.error(error);
    if (request === codesRequest) {
      codes.value = [];
      codesError.value = '코드를 불러오지 못했습니다.';
    }
  } finally {
    if (request === codesRequest) {
      codesLoading.value = false;
    }
  }
}

// ── 그룹 추가·수정 ──

function openGroupCreate() {
  formError.value = '';
  groupFormMode.value = 'create';
  Object.assign(groupForm, { groupCode: '', groupName: '', description: '', enabled: 'true', managedByEnum: false, updatedAt: null });
  groupFormOpen.value = true;
}

function openGroupEdit() {
  const group = selectedGroup.value;
  formError.value = '';
  groupFormMode.value = 'edit';
  Object.assign(groupForm, {
    groupCode: group.groupCode,
    groupName: group.groupName,
    description: group.description ?? '',
    enabled: String(group.enabled),
    managedByEnum: group.managedByEnum,
    updatedAt: group.updatedAt,
  });
  groupFormOpen.value = true;
}

async function saveGroup() {
  if (saving.value) {
    return;
  }
  saving.value = true;
  formError.value = '';

  try {
    const saved = groupFormMode.value === 'create'
      ? await createAdminCommonCodeGroup({
        groupCode: groupForm.groupCode.trim(),
        groupName: groupForm.groupName.trim(),
        description: groupForm.description.trim() || null,
      })
      : await updateAdminCommonCodeGroup(groupForm.groupCode, {
        groupName: groupForm.groupName.trim(),
        description: groupForm.description.trim() || null,
        enabled: groupForm.enabled === 'true',
        updatedAt: groupForm.updatedAt,
      });

    groupFormOpen.value = false;
    notifySuccess(groupFormMode.value === 'create' ? '그룹을 추가했습니다.' : '그룹을 수정했습니다.');
    // 새 그룹은 목록의 어느 페이지에 있든 바로 오른쪽에 연다
    selectGroup(saved);
    await loadGroups();
  } catch (error) {
    failWith(error.message);
  } finally {
    saving.value = false;
  }
}

// ── 코드 추가·수정 ──

function openCodeCreate() {
  formError.value = '';
  codeFormMode.value = 'create';
  Object.assign(codeForm, { code: '', codeName: '', description: '', sortOrder: '', enabled: 'true', managedByEnum: false, updatedAt: null });
  codeFormOpen.value = true;
}

function openCodeEdit(code) {
  formError.value = '';
  codeFormMode.value = 'edit';
  Object.assign(codeForm, {
    code: code.code,
    codeName: code.codeName,
    description: code.description ?? '',
    sortOrder: String(code.sortOrder),
    enabled: String(code.enabled),
    managedByEnum: code.managedByEnum,
    updatedAt: code.updatedAt,
  });
  codeFormOpen.value = true;
}

async function saveCode() {
  if (saving.value || !selectedGroup.value) {
    return;
  }
  // 숫자가 아닌 순서를 그냥 보내면 비운 것으로 처리돼 맨 뒤로 가 버린다
  const sortOrderText = codeForm.sortOrder.trim();
  if (sortOrderText !== '' && !/^\d+$/.test(sortOrderText)) {
    failWith('순서는 0부터 9999까지 숫자로 입력해 주세요.');
    return;
  }
  saving.value = true;
  formError.value = '';
  const groupCode = selectedGroup.value.groupCode;
  const sortOrder = sortOrderText === '' ? null : Number(sortOrderText);

  try {
    if (codeFormMode.value === 'create') {
      await createAdminCommonCode(groupCode, {
        code: codeForm.code.trim(),
        codeName: codeForm.codeName.trim(),
        description: codeForm.description.trim() || null,
        sortOrder,
      });
    } else {
      await updateAdminCommonCode(groupCode, codeForm.code, {
        codeName: codeForm.codeName.trim(),
        description: codeForm.description.trim() || null,
        sortOrder,
        enabled: codeForm.enabled === 'true',
        updatedAt: codeForm.updatedAt,
      });
    }

    codeFormOpen.value = false;
    notifySuccess(codeFormMode.value === 'create' ? '코드를 추가했습니다.' : '코드를 수정했습니다.');
    // 코드 수가 왼쪽 목록에도 보이므로 둘 다 다시 읽는다
    await Promise.all([loadCodes(), loadGroups()]);
  } catch (error) {
    failWith(error.message);
  } finally {
    saving.value = false;
  }
}

/** 실패도 성공만큼 눈에 띄어야 한다. 인라인 문구는 폼 아래라 화면 밖일 때가 있다 */
function failWith(message) {
  formError.value = message;
  notifyError(message);
}

onMounted(search);
</script>

<template>
  <div class="admin-common-code-page">
    <AdminPageHeader>
      <template #meta>공통으로 쓰는 코드와 이름을 그룹별로 관리합니다.</template>
    </AdminPageHeader>

    <AdminSearchPanel @search="search">
      <AdminTextInput v-model="condition.keyword" label="검색어" placeholder="그룹·코드의 값이나 이름" />
      <AdminSegmented v-model="condition.enabled" label="그룹 사용 여부" :options="ENABLED_FILTERS" />
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

    <div class="admin-common-code__panes">
      <!-- 왼쪽: 그룹 -->
      <section class="admin-common-code__pane" aria-labelledby="common-code-groups-title">
        <div class="admin-common-code__pane-head">
          <h2 id="common-code-groups-title" class="admin-common-code__pane-title">그룹</h2>
          <button class="admin-button admin-button--ghost admin-button--small" type="button" @click="openGroupCreate">
            + 그룹 추가
          </button>
        </div>

        <AdminDataGrid
          :columns="GROUP_COLUMNS"
          :rows="groups"
          row-key="groupCode"
          :active-key="selectedGroup?.groupCode ?? null"
          min-width="300px"
          :loading="groupsLoading"
          :error-text="groupsError"
          empty-text="조회조건에 맞는 그룹이 없습니다."
          row-clickable
          @row-click="pickGroup"
          @retry="loadGroups"
        >
          <template #cell-group="{ row }">
            <!-- 키보드로도 고를 수 있게 버튼으로 둔다. 누르면 행 클릭으로 이어진다 -->
            <button class="admin-common-code__pick" type="button">
              <span class="admin-common-code__pick-name">
                {{ row.groupName }}
                <span v-if="row.managedByEnum" class="admin-badge admin-badge--on">서버 코드</span>
              </span>
              <code class="admin-common-code__value">{{ row.groupCode }}</code>
            </button>
          </template>
          <template #cell-enabled="{ value }">
            <span class="admin-badge" :class="value ? 'admin-badge--on' : 'admin-badge--off'">
              {{ value ? '사용' : '안 함' }}
            </span>
          </template>
        </AdminDataGrid>

        <AdminPagination
          v-if="totalPages > 1"
          :page="page"
          :total-pages="totalPages"
          @update:page="changePage"
        />
      </section>

      <!-- 오른쪽: 고른 그룹의 코드 -->
      <section
        ref="codesPane"
        class="admin-common-code__pane admin-common-code__pane--codes"
        :aria-labelledby="groupsPending ? undefined : 'common-code-codes-title'"
        :aria-busy="groupsPending"
      >
        <div class="admin-common-code__pane-head admin-common-code__pane-head--group">
          <!-- 그룹 정보는 그룹 목록이 와야 정해진다. 그동안 같은 높이의 자리를 잡는다 -->
          <div v-if="groupsPending" class="admin-common-code__group-info" aria-hidden="true">
            <span class="ui-skeleton admin-common-code__title-skeleton"></span>
            <span class="ui-skeleton admin-common-code__meta-skeleton"></span>
          </div>
          <div v-else-if="selectedGroup" class="admin-common-code__group-info">
            <h2 id="common-code-codes-title" class="admin-common-code__pane-title">
              {{ selectedGroup.groupName }}
              <span v-if="selectedGroup.managedByEnum" class="admin-badge admin-badge--on">서버 코드</span>
              <span v-if="!selectedGroup.enabled" class="admin-badge admin-badge--off">사용 안 함</span>
            </h2>
            <p class="admin-common-code__group-meta">
              <code class="admin-common-code__value">{{ selectedGroup.groupCode }}</code>
              <span v-if="selectedGroup.description"> · {{ selectedGroup.description }}</span>
            </p>
          </div>
          <div v-else class="admin-common-code__group-info">
            <h2 id="common-code-codes-title" class="admin-common-code__pane-title">코드</h2>
            <p class="admin-common-code__group-meta">왼쪽에서 그룹을 고르면 코드가 나옵니다.</p>
          </div>

          <div class="admin-common-code__actions">
            <button
              class="admin-button admin-button--ghost admin-button--small"
              type="button"
              :disabled="!selectedGroup"
              @click="openGroupEdit"
            >
              그룹 수정
            </button>
            <button
              class="admin-button admin-button--solid admin-button--small"
              type="button"
              :disabled="!canAddCode"
              :title="selectedGroup?.managedByEnum ? MANAGED_NOTE : undefined"
              @click="openCodeCreate"
            >
              + 코드 추가
            </button>
          </div>
        </div>

        <!-- 그룹 종류에 따라 문구만 바꾸고 줄은 늘 둔다. 그룹을 바꿀 때 아래 표가 오르내리지 않게 -->
        <p class="admin-field__hint admin-common-code__note">
          <span v-if="groupsPending" class="ui-skeleton admin-common-code__note-skeleton" aria-hidden="true">&nbsp;</span>
          <template v-else-if="selectedGroup">{{ selectedGroup.managedByEnum ? MANAGED_NOTE : SCREEN_NOTE }}</template>
          <template v-else>&nbsp;</template>
        </p>

        <AdminDataGrid
          :columns="CODE_COLUMNS"
          :rows="selectedGroup ? codes : []"
          row-key="code"
          min-width="520px"
          :loading="codesLoading || groupsPending"
          :error-text="codesError"
          :empty-text="selectedGroup ? '이 그룹에 코드가 없습니다.' : '고른 그룹이 없습니다.'"
          row-clickable
          @row-click="openCodeEdit"
          @retry="loadCodes"
        >
          <template #cell-code="{ value }">
            <button class="admin-common-code__pick" type="button">
              <code class="admin-common-code__value">{{ value }}</code>
            </button>
          </template>
          <template #cell-description="{ value }">
            <span v-if="value">{{ value }}</span>
            <span v-else class="admin-common-code__empty">—</span>
          </template>
          <template #cell-enabled="{ value }">
            <span class="admin-badge" :class="value ? 'admin-badge--on' : 'admin-badge--off'">
              {{ value ? '사용' : '안 함' }}
            </span>
          </template>
        </AdminDataGrid>
      </section>
    </div>

    <!-- 그룹 추가·수정 -->
    <BaseModal variant="admin"
      :open="groupFormOpen"
      :title="groupFormMode === 'create' ? '그룹 추가' : '그룹 수정'"
      :description="groupFormMode === 'create'
        ? '그룹 코드는 만든 뒤 바꿀 수 없습니다.'
        : `${groupForm.groupCode} — 그룹 코드는 바꿀 수 없습니다.`"
      :close-on-backdrop="false"
      size="small"
      @close="groupFormOpen = false"
    >
      <div class="admin-modal__form admin-modal__form--full">
        <AdminTextInput
          v-if="groupFormMode === 'create'"
          v-model="groupCodeInput"
          label="그룹 코드"
          placeholder="예: BANK (영문 대문자·숫자·_)"
        />
        <AdminTextInput v-model="groupForm.groupName" label="이름" placeholder="화면에 보일 이름" />
        <AdminTextInput v-model="groupForm.description" label="설명" placeholder="비워도 됩니다" />
        <template v-if="groupFormMode === 'edit'">
          <AdminSegmented v-if="!groupForm.managedByEnum" v-model="groupForm.enabled" label="사용 여부" :options="ENABLED_OPTIONS" />
          <p v-else class="admin-field__hint">{{ MANAGED_NOTE }}</p>
        </template>
      </div>

      <p v-if="formError" class="admin-form-error">{{ formError }}</p>

      <template #footer>
        <button class="admin-button admin-button--solid" type="button" :disabled="saving" @click="saveGroup">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="groupFormOpen = false">닫기</button>
      </template>
    </BaseModal>

    <!-- 코드 추가·수정 -->
    <BaseModal variant="admin"
      :open="codeFormOpen"
      :title="codeFormMode === 'create' ? '코드 추가' : '코드 수정'"
      :description="codeFormMode === 'create'
        ? `${selectedGroup?.groupName ?? ''} 그룹에 넣습니다. 코드 값은 만든 뒤 바꿀 수 없습니다.`
        : `${selectedGroup?.groupCode ?? ''} / ${codeForm.code} — 코드 값은 바꿀 수 없습니다.`"
      :close-on-backdrop="false"
      size="small"
      @close="codeFormOpen = false"
    >
      <div class="admin-modal__form admin-modal__form--full">
        <AdminTextInput
          v-if="codeFormMode === 'create'"
          v-model="codeInput"
          label="코드"
          placeholder="예: KB (영문 대문자·숫자·_)"
        />
        <AdminTextInput v-model="codeForm.codeName" label="이름" placeholder="화면에 보일 이름" />
        <AdminTextInput v-model="codeForm.description" label="설명" placeholder="비워도 됩니다" />
        <AdminTextInput
          v-model="codeForm.sortOrder"
          label="순서"
          :placeholder="codeFormMode === 'create' ? '비우면 맨 뒤 (0~9999)' : '0~9999'"
        />
        <template v-if="codeFormMode === 'edit'">
          <AdminSegmented v-if="!codeForm.managedByEnum" v-model="codeForm.enabled" label="사용 여부" :options="ENABLED_OPTIONS" />
          <p v-else class="admin-field__hint">서버 코드와 연결된 코드라 사용 여부는 개발로만 바꿉니다.</p>
        </template>
      </div>

      <p v-if="formError" class="admin-form-error">{{ formError }}</p>

      <template #footer>
        <button class="admin-button admin-button--solid" type="button" :disabled="saving" @click="saveCode">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="codeFormOpen = false">닫기</button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.admin-common-code__panes {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 20px;
  align-items: start;
}

.admin-common-code__pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.admin-common-code__pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
}

/* 그룹 이름·코드 두 줄 + 버튼. 그룹이 오기 전 스켈레톤과 높이를 맞춘다 */
.admin-common-code__pane-head--group {
  min-height: 48px;
}

.admin-common-code__pane-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--admin-text-strong);
  font-size: 15px;
  font-weight: 700;
}

.admin-common-code__group-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.admin-common-code__group-meta {
  margin: 0;
  overflow: hidden;
  color: var(--admin-text);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-common-code__title-skeleton {
  width: 120px;
  height: 20px;
}

.admin-common-code__meta-skeleton {
  width: 200px;
  max-width: 100%;
  height: 16px;
}

.admin-common-code__actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

.admin-common-code__note {
  margin: -4px 0 0;
}

.admin-common-code__note-skeleton {
  display: inline-block;
  width: 280px;
  max-width: 100%;
}

/* 쌓인 화면에서 코드 목록으로 내려갈 때 위쪽에 숨 쉴 틈을 둔다 */
.admin-common-code__pane--codes {
  scroll-margin-top: 16px;
}

/* 행 전체가 눌리는 표라 버튼은 모양 없이 글자만 둔다 */
.admin-common-code__pick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.admin-common-code__pick-name {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--admin-text-strong);
  font-weight: 600;
}

.admin-common-code__value {
  color: var(--admin-meta);
  font-size: 12px;
}

.admin-common-code__empty {
  color: var(--admin-text-muted);
}

@media (max-width: 1100px) {
  .admin-common-code__panes {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
