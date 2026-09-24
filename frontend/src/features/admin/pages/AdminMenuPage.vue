<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
  createAdminMenu,
  deleteAdminMenu,
  getAdminMenus,
  updateAdminMenu,
} from '../api/adminApi';
import { sortAdminMenus } from '../data/adminMenus';
import { reloadAdminSidebarMenus } from '../data/adminSidebarMenuStore';
import { notifySuccess } from '../data/adminToastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminSearchPanel from '../components/AdminSearchPanel.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSelect from '../components/AdminSelect.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';

// 서버의 MenuSearchCondition 과 이름·개수를 그대로 맞춘다.
// 서버가 안 받는 조건은 화면에도 두지 않는다 (걸러지는 것처럼 보이면 더 헷갈린다)
const EMPTY_CONDITION = {
  menuName: '',
  menuDescription: '',
  routePath: '',
};

const menus = ref([]);
const loading = ref(false);
const loadError = ref('');
const collapsedGroupIds = ref([]);

// 입력창의 값. 조회를 눌러 서버에 보낼 때까지 목록에는 영향이 없다
const condition = reactive({ ...EMPTY_CONDITION });
// 저장 뒤 목록을 다시 읽을 때 쓸, 조회로 적용된 조건
const applied = ref({ ...EMPTY_CONDITION });

const modalOpen = ref(false);
const selectedMenu = ref(null);

const EMPTY_FORM = {
  // 팝업을 연 시점의 서버 값. 저장 때 함께 보내 충돌을 판단한다
  updatedAt: null,
  menuType: 'ITEM',
  parentId: '',
  menuName: '',
  menuDescription: '',
  routePath: '',
  sortOrder: '',
  visible: 'true',
};

const MENU_TYPE_OPTIONS = [
  { value: 'GROUP', label: '그룹' },
  { value: 'ITEM', label: '항목' },
];

// 팝업의 입력값. 표의 원본을 직접 고치지 않는다 —
// 저장을 누르지 않고 닫으면 화면이 바뀌어 있으면 안 되기 때문
const form = reactive({ ...EMPTY_FORM });
const formMode = ref('edit');
const saving = ref(false);
const formError = ref('');
const deleteConfirmOpen = ref(false);

// 그룹이면 소속·경로 칸이 없다. 등록할 때는 고른 유형을, 수정할 때는 원래 유형을 따른다
const isGroupForm = computed(() => form.menuType === 'GROUP');

const modalTitle = computed(() => {
  if (formMode.value === 'create') {
    return '메뉴 추가';
  }

  return isGroupForm.value ? '그룹 수정' : '항목 수정';
});

// 서버가 그룹 + 그 그룹에 속한 항목으로 조립해서 내려준다.
// 화면은 조립 결과를 그대로 보여주기만 한다
const groups = computed(() => sortAdminMenus(menus.value));

const groupCount = computed(() => groups.value.length);

const itemCount = computed(() => groups.value
  .reduce((total, group) => total + group.items.length, 0));

const totalCount = computed(() => groupCount.value + itemCount.value);

const state = computed(() => {
  if (loading.value) return 'loading';
  if (loadError.value) return 'error';
  if (groups.value.length === 0) return 'empty';
  return 'groups';
});

const allCollapsed = computed(() => groups.value.length > 0
  && groups.value.every((group) => collapsedGroupIds.value.includes(group.id)));

function isCollapsed(groupId) {
  return collapsedGroupIds.value.includes(groupId);
}

function toggleGroup(groupId) {
  collapsedGroupIds.value = isCollapsed(groupId)
    ? collapsedGroupIds.value.filter((id) => id !== groupId)
    : [...collapsedGroupIds.value, groupId];
}

function toggleAllGroups() {
  collapsedGroupIds.value = allCollapsed.value
    ? []
    : groups.value.map((group) => group.id);
}

// 항목 수정 팝업에서 고를 소속 그룹
const groupOptions = computed(() => groups.value
  .map((group) => ({ value: String(group.id), label: group.menuName })));

async function loadMenus(searchCondition) {
  loading.value = true;
  loadError.value = '';

  try {
    menus.value = await getAdminMenus(searchCondition);
    // 조건이 바뀌면 접힘을 푼다.
    // 접힌 그룹 안의 항목이 걸리면 아무것도 안 보이기 때문
    collapsedGroupIds.value = [];
  } catch (error) {
    console.error(error);
    menus.value = [];
    loadError.value = '메뉴를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

// 조회 — 목록이 바뀌는 것은 이 함수를 거칠 때뿐이다
function search() {
  applied.value = { ...condition };
  return loadMenus(applied.value);
}

// 초기화 — 입력창만 비운다. 목록은 다음 조회 때 바뀐다
function reset() {
  Object.assign(condition, EMPTY_CONDITION);
}

function openCreateForm() {
  formMode.value = 'create';
  selectedMenu.value = null;
  formError.value = '';
  deleteConfirmOpen.value = false;

  Object.assign(form, EMPTY_FORM, {
    // 첫 그룹을 미리 골라둔다. 그룹이 하나도 없으면 항목을 만들 수 없다
    parentId: groups.value[0] ? String(groups.value[0].id) : '',
  });

  modalOpen.value = true;
}

function openMenu(menu) {
  formMode.value = 'edit';
  selectedMenu.value = menu;
  formError.value = '';
  deleteConfirmOpen.value = false;

  Object.assign(form, {
    updatedAt: menu.updatedAt,
    menuType: menu.menuType,
    parentId: menu.parentId ? String(menu.parentId) : '',
    menuName: menu.menuName,
    menuDescription: menu.menuDescription ?? '',
    routePath: menu.routePath ?? '',
    sortOrder: String(menu.sortOrder),
    visible: String(menu.visible),
  });

  modalOpen.value = true;
}

async function saveMenu() {
  if (saving.value) {
    return;
  }

  saving.value = true;
  formError.value = '';

  // 그룹은 소속과 경로를 가질 수 없다 (서버에서도 무시하지만 보내지도 않는다)
  const request = {
    updatedAt: form.updatedAt,
    parentId: isGroupForm.value || form.parentId === '' ? null : Number(form.parentId),
    menuName: form.menuName.trim(),
    menuDescription: form.menuDescription.trim(),
    routePath: isGroupForm.value ? null : form.routePath.trim(),
    // 비우면 서버가 같은 자리의 마지막 다음 번호를 매긴다
    sortOrder: form.sortOrder === '' ? null : Number(form.sortOrder),
    visible: form.visible === 'true',
  };

  try {
    if (formMode.value === 'create') {
      // 등록에는 비교할 이전 값이 없다
      await createAdminMenu({ ...request, updatedAt: null, menuType: form.menuType });
    } else {
      await updateAdminMenu(selectedMenu.value.id, request);
    }

    const done = formMode.value === 'create' ? '메뉴를 등록했습니다.' : '메뉴를 수정했습니다.';

    modalOpen.value = false;
    await afterChange();

    notifySuccess(done);
  } catch (error) {
    // 서버가 준 메시지를 그대로 보여준다
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}

async function removeMenu() {
  if (saving.value) {
    return;
  }

  saving.value = true;
  formError.value = '';

  try {
    await deleteAdminMenu(selectedMenu.value.id);
    deleteConfirmOpen.value = false;
    modalOpen.value = false;
    await afterChange();

    notifySuccess('메뉴를 삭제했습니다.');
  } catch (error) {
    // 확인창을 닫아 팝업의 오류 자리에서 이유를 보여준다
    deleteConfirmOpen.value = false;
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}

// 메뉴를 고치면 사이드바도 달라지므로 같이 다시 읽는다
async function afterChange() {
  await Promise.all([loadMenus(applied.value), reloadAdminSidebarMenus()]);
}

onMounted(search);
</script>

<template>
  <div class="admin-menu-page">
    <AdminPageHeader>
      <template #actions>
        <button class="admin-button admin-button--solid" type="button" @click="openCreateForm">
          메뉴 추가
        </button>
      </template>
    </AdminPageHeader>

    <AdminSearchPanel @search="search">
      <AdminTextInput v-model="condition.menuName" label="메뉴명" placeholder="메뉴명 검색" />
      <AdminTextInput v-model="condition.menuDescription" label="설명" placeholder="설명 검색" />
      <AdminTextInput v-model="condition.routePath" label="경로" placeholder="/admin/..." />
    </AdminSearchPanel>

    <AdminGridToolbar :total-count="totalCount">
      <template #left>
        <span class="admin-toolbar__total">
          그룹 <strong>{{ groupCount }}</strong> · 항목 <strong>{{ itemCount }}</strong>
        </span>
      </template>

      <template #right>
        <button
          class="admin-button admin-button--ghost"
          type="button"
          :disabled="groups.length === 0"
          @click="toggleAllGroups"
        >
          {{ allCollapsed ? '모두 펼치기' : '모두 접기' }}
        </button>
        <button class="admin-button admin-button--ghost" type="button" @click="reset">
          초기화
        </button>
        <button class="admin-button admin-button--solid" type="button" @click="search">
          조회
        </button>
      </template>
    </AdminGridToolbar>

    <!-- 로딩 · 실패 · 빈 목록은 표와 같은 모양으로 보여준다 -->
    <div v-if="state === 'loading'" class="admin-tree__state">
      <div class="admin-tree__skeleton"><span></span><span></span><span></span></div>
    </div>

    <div v-else-if="state === 'error'" class="admin-tree__state">
      <div class="admin-grid__error">
        <span class="admin-grid__error-icon" aria-hidden="true">!</span>
        <span>{{ loadError }}</span>
        <button class="admin-grid__retry" type="button" @click="search">다시 시도</button>
      </div>
    </div>

    <div v-else-if="state === 'empty'" class="admin-tree__state">
      <p class="admin-tree__message">조회조건에 맞는 메뉴가 없습니다.</p>
    </div>

    <div v-else class="admin-tree">
      <section v-for="group in groups" :key="group.id" class="admin-tree__group">
        <div class="admin-tree__head">
          <button
            class="admin-tree__toggle"
            :class="{ 'admin-tree__toggle--collapsed': isCollapsed(group.id) }"
            type="button"
            :disabled="group.items.length === 0"
            :aria-expanded="!isCollapsed(group.id)"
            :aria-label="`${group.menuName} 항목 ${isCollapsed(group.id) ? '펼치기' : '접기'}`"
            @click="toggleGroup(group.id)"
          >
            <span class="admin-tree__chevron" aria-hidden="true"></span>
          </button>

          <button class="admin-tree__head-main" type="button" @click="openMenu(group)">
            <span class="admin-tree__title-row">
              <span class="admin-tree__order">{{ group.sortOrder }}</span>
              <span class="admin-tree__name">{{ group.menuName }}</span>
              <span
                class="admin-badge"
                :class="group.visible ? 'admin-badge--on' : 'admin-badge--off'"
              >
                {{ group.visible ? '노출' : '숨김' }}
              </span>
            </span>
            <span class="admin-tree__desc">{{ group.menuDescription || '설명 없음' }}</span>
          </button>

          <span class="admin-tree__meta">
            항목 {{ group.items.length }}개 ·
            노출 {{ group.items.filter((item) => item.visible).length }}개
          </span>
        </div>

        <div v-if="!isCollapsed(group.id)" class="admin-tree__items">
          <button
            v-for="item in group.items"
            :key="item.id"
            class="admin-tree__item"
            type="button"
            @click="openMenu(item)"
          >
            <span class="admin-tree__order">{{ item.sortOrder }}</span>

            <span class="admin-tree__item-name">
              <span>{{ item.menuName }}</span>
              <span class="admin-tree__desc">{{ item.menuDescription || '설명 없음' }}</span>
            </span>

            <span
              class="admin-tree__path"
              :class="{ 'admin-tree__path--none': !item.routePath }"
            >
              {{ item.routePath || '경로 없음' }}
            </span>

            <span
              class="admin-badge admin-tree__item-badge"
              :class="item.visible ? 'admin-badge--on' : 'admin-badge--off'"
            >
              {{ item.visible ? '노출' : '숨김' }}
            </span>
          </button>
        </div>
      </section>
    </div>

    <!-- 수정은 이 팝업에서 한다. 유형(그룹·항목)은 바꿀 수 없다 -->
    <BaseModal variant="admin"
      :open="modalOpen"
      :title="modalTitle"
      :description="formMode === 'create'
        ? '그룹은 최상위에, 항목은 그룹 안에 만들어집니다.'
        : `${selectedMenu?.menuName ?? ''} 메뉴의 설정입니다.`"
      :close-on-backdrop="false"
      @close="modalOpen = false"
    >
      <div class="admin-modal__form">
        <!-- 유형은 만들 때만 정할 수 있다. 나중에 바꾸면 트리가 깨진다 -->
        <AdminSegmented
          v-if="formMode === 'create'"
          v-model="form.menuType"
          label="유형"
          :options="MENU_TYPE_OPTIONS"
        />

        <AdminTextInput v-model="form.menuName" label="메뉴명" />
        <AdminTextInput v-model="form.menuDescription" label="설명" />

        <AdminSelect
          v-if="!isGroupForm"
          v-model="form.parentId"
          label="소속 그룹"
          :options="groupOptions"
        />
        <AdminTextInput
          v-if="!isGroupForm"
          v-model="form.routePath"
          label="경로"
          placeholder="/admin/..."
        />

        <AdminTextInput
          v-model="form.sortOrder"
          label="순서"
          :placeholder="formMode === 'create' ? '비우면 맨 뒤' : ''"
        />
        <AdminSegmented
          v-model="form.visible"
          label="노출 여부"
          :options="[
            { value: 'true', label: '노출' },
            { value: 'false', label: '숨김' },
          ]"
        />
      </div>

      <p v-if="formError" class="admin-form-error">{{ formError }}</p>

      <template #footer>
        <button
          v-if="formMode === 'edit'"
          class="admin-button admin-button--danger"
          type="button"
          :disabled="saving"
          @click="deleteConfirmOpen = true"
        >
          삭제
        </button>
        <button
          class="admin-button admin-button--solid"
          type="button"
          :disabled="saving"
          @click="saveMenu"
        >
          {{ saving ? '저장 중…' : '저장' }}
        </button>
      <button class="admin-button admin-button--ghost" type="button" @click="modalOpen = false">
          닫기
        </button>
        </template>
    </BaseModal>

    <!-- 삭제 확인. 사이드바에서 사라지는 일이라 한 번 더 묻는다 -->
    <BaseModal variant="admin"
      :open="deleteConfirmOpen"
      :title="isGroupForm ? '이 그룹을 삭제할까요?' : '이 항목을 삭제할까요?'"
      :description="isGroupForm
        ? '속한 항목이 남아 있으면 삭제할 수 없습니다.'
        : '사이드바에서 사라집니다. 되돌릴 수 없습니다.'"
      size="small"
      @close="deleteConfirmOpen = false"
    >
      <p class="admin-category__confirm">{{ selectedMenu?.menuName }}</p>

      <template #footer><button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="saving"
          @click="removeMenu"
        >
          {{ saving ? '삭제 중…' : '삭제' }}
        </button>
      
        <button
          class="admin-button admin-button--ghost"
          type="button"
          @click="deleteConfirmOpen = false"
        >
          취소
        </button>
        </template>
    </BaseModal>
  </div>
</template>
