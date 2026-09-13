<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
  createAdminCategory,
  deleteAdminCategory,
  getAdminCategories,
  updateAdminCategory,
} from '../api/adminApi';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminModal from '../components/AdminModal.vue';

// 카테고리는 계층이 없어서 표로 보여준다 (메뉴와 다른 점)
const COLUMNS = [
  { key: 'sortOrder', label: '순서', width: '72px', align: 'center' },
  { key: 'name', label: '이름' },
  { key: 'postCount', label: '글 수', width: '110px', align: 'right' },
];

const EMPTY_CONDITION = {
  name: '',
};

const EMPTY_FORM = {
  name: '',
  sortOrder: '',
  // 팝업을 연 시점의 서버 값. 저장 때 함께 보내 충돌을 판단한다
  updatedAt: null,
};

// condition — 입력창의 값. applied — 조회로 적용된 값.
// 저장 뒤 목록을 다시 읽을 때도 적용된 조건을 그대로 써야 화면이 튀지 않는다
const condition = reactive({ ...EMPTY_CONDITION });
const applied = ref({ ...EMPTY_CONDITION });
const categories = ref([]);
const loading = ref(false);
const loadError = ref('');

const page = ref(1);
const pageSize = ref(10);
const selectedIds = ref([]);

const formOpen = ref(false);
const formMode = ref('create');
const form = reactive({ ...EMPTY_FORM });
const editingId = ref(null);

// 여러 건(체크박스)과 한 건(수정 팝업)은 확인창이 다르다
const deleteConfirmOpen = ref(false);
const editDeleteConfirmOpen = ref(false);

// 저장·삭제가 진행 중인지. 두 번 눌러 두 번 보내는 것을 막는다
const saving = ref(false);
const formError = ref('');
const deleteError = ref('');

const totalPages = computed(() => Math.ceil(categories.value.length / pageSize.value));

const pagedCategories = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return categories.value.slice(start, start + pageSize.value);
});

// 선택은 페이지를 넘겨도 유지되므로, 지금 화면에 없는 것도 들어있다
const selectedNames = computed(() => selectedIds.value
  .map((id) => categories.value.find((category) => category.id === id)?.name)
  .filter(Boolean));

async function loadCategories(searchCondition) {
  loading.value = true;
  loadError.value = '';

  try {
    categories.value = await getAdminCategories(searchCondition);
    selectedIds.value = [];

    if (page.value > totalPages.value) {
      page.value = Math.max(1, totalPages.value);
    }
  } catch (error) {
    console.error(error);
    categories.value = [];
    loadError.value = '카테고리를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

// 조회 — 목록이 바뀌는 것은 이 함수를 거칠 때뿐이다
function search() {
  page.value = 1;
  applied.value = { ...condition };
  return loadCategories(applied.value);
}

// 초기화 — 입력창만 비운다. 목록은 다음 조회 때 바뀐다
function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

function openCreateForm() {
  formMode.value = 'create';
  editingId.value = null;
  formError.value = '';
  editDeleteConfirmOpen.value = false;
  Object.assign(form, EMPTY_FORM, {
    // 다음 순서를 미리 채워둔다
    sortOrder: String(nextSortOrder()),
  });
  formOpen.value = true;
}

function openEditForm(category) {
  formMode.value = 'edit';
  editingId.value = category.id;
  formError.value = '';
  editDeleteConfirmOpen.value = false;
  Object.assign(form, {
    name: category.name,
    sortOrder: String(category.sortOrder),
    updatedAt: category.updatedAt,
  });
  formOpen.value = true;
}

async function saveCategory() {
  if (saving.value) {
    return;
  }

  saving.value = true;
  formError.value = '';

  // 순서를 비워두면 서버가 마지막 다음 번호를 매긴다
  const request = {
    name: form.name.trim(),
    sortOrder: form.sortOrder === '' ? null : Number(form.sortOrder),
    // 등록에는 비교할 이전 값이 없다
    updatedAt: formMode.value === 'create' ? null : form.updatedAt,
  };

  try {
    if (formMode.value === 'create') {
      await createAdminCategory(request);
    } else {
      await updateAdminCategory(editingId.value, request);
    }

    formOpen.value = false;
    await loadCategories({ ...applied.value });
  } catch (error) {
    // 서버가 준 메시지를 그대로 보여준다 (이름 중복·필수값 누락 등)
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}

async function deleteSelected() {
  if (saving.value) {
    return;
  }

  saving.value = true;
  deleteError.value = '';

  // 삭제 API 가 한 건씩만 받으므로 순서대로 보낸다.
  // 하나가 실패해도 멈추지 않는다 — 글이 달린 카테고리 하나 때문에
  // 나머지를 못 지우면 사용자가 이유도 모른 채 다시 골라야 한다
  const targets = [...selectedIds.value];
  const failures = [];
  let deleted = 0;

  for (const categoryId of targets) {
    try {
      // eslint-disable-next-line no-await-in-loop
      await deleteAdminCategory(categoryId);
      deleted += 1;
    } catch (error) {
      const name = categories.value.find((category) => category.id === categoryId)?.name ?? categoryId;
      failures.push(`${name} — ${error.message}`);
    }
  }

  // 몇 건이 지워졌는지 모르면 사용자는 같은 동작을 다시 시도하게 된다
  if (failures.length > 0) {
    deleteError.value = `${targets.length}건 중 ${deleted}건을 삭제했습니다.\n`
      + `실패: ${failures.join(' / ')}`;
  } else {
    deleteConfirmOpen.value = false;
  }

  await loadCategories({ ...applied.value });
  saving.value = false;
}

async function deleteEditing() {
  if (saving.value) {
    return;
  }

  saving.value = true;
  formError.value = '';

  try {
    await deleteAdminCategory(editingId.value);
    editDeleteConfirmOpen.value = false;
    formOpen.value = false;
    await loadCategories({ ...applied.value });
  } catch (error) {
    // 확인창을 닫아 수정 팝업의 오류 자리에서 이유를 보여준다
    editDeleteConfirmOpen.value = false;
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}

function nextSortOrder() {
  const orders = categories.value.map((category) => category.sortOrder);
  return orders.length === 0 ? 1 : Math.max(...orders) + 1;
}

onMounted(search);
</script>

<template>
  <div class="admin-category-page">
    <AdminPageHeader>
      <template #actions>
        <button class="admin-button admin-button--solid" type="button" @click="openCreateForm">
          카테고리 추가
        </button>
      </template>
    </AdminPageHeader>

    <AdminSearchPanel @search="search">
      <AdminTextInput v-model="condition.name" label="이름" placeholder="카테고리 이름" />
    </AdminSearchPanel>

    <AdminGridToolbar :total-count="categories.length">
      <template #left>
        <AdminPageSize v-model="pageSize" />
        <span v-if="selectedIds.length" class="admin-toolbar__selected">
          선택 <strong>{{ selectedIds.length }}</strong>건
        </span>
      </template>

      <template #right>
        <button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="selectedIds.length === 0"
          @click="deleteConfirmOpen = true"
        >
          선택 삭제
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="reset">
          초기화
        </button>
        <button class="admin-button admin-button--solid" type="button" @click="search">
          조회
        </button>
      </template>
    </AdminGridToolbar>

    <AdminDataGrid
      v-model:selected-keys="selectedIds"
      :columns="COLUMNS"
      :rows="pagedCategories"
      :loading="loading"
      :error-text="loadError"
      empty-text="조회조건에 맞는 카테고리가 없습니다."
      selectable
      row-clickable
      @row-click="openEditForm"
      @retry="search"
    >
      <!-- 글 수는 관리자 전용 API 에서 내려올 값 -->
      <template #cell-postCount="{ value }">
        <span v-if="value === null" class="admin-category__pending">연결 전</span>
        <template v-else>{{ value.toLocaleString('ko-KR') }}편</template>
      </template>
    </AdminDataGrid>

    <AdminPagination v-model:page="page" :total-pages="totalPages" />

    <!-- 등록·수정 팝업 -->
    <AdminModal
      :open="formOpen"
      :title="formMode === 'create' ? '카테고리 추가' : '카테고리 수정'"
      :description="formMode === 'create'
        ? '이름과 순서를 정해 새 카테고리를 만듭니다.'
        : '이름과 순서를 바꿉니다.'"
      :close-on-backdrop="false"
      size="small"
      @close="formOpen = false"
    >
      <div class="admin-modal__form admin-modal__form--full">
        <AdminTextInput v-model="form.name" label="이름" placeholder="예: 개발" />
        <AdminTextInput v-model="form.sortOrder" label="순서" placeholder="비우면 맨 뒤" />
      </div>

      <p v-if="formError" class="admin-form-error">{{ formError }}</p>

      <template #footer>
        <button
          v-if="formMode === 'edit'"
          class="admin-button admin-button--danger"
          type="button"
          :disabled="saving"
          @click="editDeleteConfirmOpen = true"
        >
          삭제
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="formOpen = false">
          닫기
        </button>
        <button
          class="admin-button admin-button--solid"
          type="button"
          :disabled="saving"
          @click="saveCategory"
        >
          {{ saving ? '저장 중…' : '저장' }}
        </button>
      </template>
    </AdminModal>

    <!-- 한 건 삭제 확인. 되돌릴 수 없어서 한 번 더 묻는다 -->
    <AdminModal
      :open="editDeleteConfirmOpen"
      title="이 카테고리를 삭제할까요?"
      description="되돌릴 수 없습니다."
      size="small"
      @close="editDeleteConfirmOpen = false"
    >
      <p class="admin-category__confirm">{{ form.name }}</p>
      <p class="admin-category__confirm-note">
        글이 달린 카테고리는 삭제할 수 없습니다.
      </p>

      <template #footer>
        <button
          class="admin-button admin-button--ghost"
          type="button"
          @click="editDeleteConfirmOpen = false"
        >
          취소
        </button>
        <button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="saving"
          @click="deleteEditing"
        >
          {{ saving ? '삭제 중…' : '삭제' }}
        </button>
      </template>
    </AdminModal>

    <!-- 선택 삭제 확인 -->
    <AdminModal
      :open="deleteConfirmOpen"
      title="선택한 카테고리를 삭제할까요?"
      :description="`${selectedIds.length}건을 삭제합니다. 되돌릴 수 없습니다.`"
      size="small"
      @close="deleteConfirmOpen = false"
    >
      <p class="admin-category__confirm">{{ selectedNames.join(', ') }}</p>
      <p class="admin-category__confirm-note">
        글이 달린 카테고리는 삭제할 수 없습니다. 글 수를 먼저 확인하세요.
      </p>

      <p v-if="deleteError" class="admin-form-error">{{ deleteError }}</p>

      <template #footer>
        <button
          class="admin-button admin-button--ghost"
          type="button"
          @click="deleteConfirmOpen = false"
        >
          취소
        </button>
        <button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="saving"
          @click="deleteSelected"
        >
          {{ saving ? '삭제 중…' : '삭제' }}
        </button>
      </template>
    </AdminModal>
  </div>
</template>
