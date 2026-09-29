<script setup>
import { computed, onMounted, ref } from 'vue';

import { cleanupAdminImages, getAdminImages } from '../api/adminApi';
import { collectDraftImageUrls } from '../data/postDraftImages';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminGridToolbar from '../components/AdminGridToolbar.vue';
import AdminDataGrid from '../components/AdminDataGrid.vue';
import AdminPageSize from '../components/AdminPageSize.vue';
import AdminPagination from '../components/AdminPagination.vue';
import AdminSegmented from '../components/AdminSegmented.vue';
import BaseModal from '../../../shared/components/BaseModal.vue';

/**
 * 이미지 정리.
 *
 * 글을 지우거나 고칠 때 서버가 알아서 치우지만, 그 전에 쌓인 것과 올려놓고 글에 안 넣은
 * 것이 남는다. 어디에 쓰이는지 보면서 지울 것을 고른다.
 *
 * 올린 지 일주일 안 된 것은 서버가 목록에서 뺀다. 사진만 올리고 아직 저장 안 한 글이
 * 있으면 여기 잡히는데, 임시저장이 브라우저에만 있어서 서버가 알 방법이 없다.
 * 그 부분은 이 화면이 localStorage 를 직접 읽어서 메운다.
 */
const COLUMNS = [
  { key: 'preview', label: '', width: '78px', align: 'center' },
  { key: 'originalName', label: '파일' },
  { key: 'usage', label: '쓰이는 곳' },
  { key: 'byteSize', label: '크기', width: '96px', align: 'right' },
  { key: 'uploadedAt', label: '올린 날', width: '120px' },
];

const FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'UNUSED', label: '안 쓰는 것만' },
];

const WHERE_LABELS = {
  POST_CONTENT: '본문',
  POST_THUMBNAIL: '대표 이미지',
  HOME_HERO: '대문 그림',
  PROFILE: '프로필 사진',
};

const images = ref([]);
const unusedBytes = ref(0);
const totalElements = ref(0);
const totalPages = ref(0);
const page = ref(1);
const pageSize = ref(20);
const filter = ref('UNUSED');

/** 주소 → 그 주소를 쓰는 임시저장 글 제목들 */
const draftUsers = ref(new Map());

const loading = ref(false);
const loadError = ref('');
const selectedIds = ref([]);
const deleting = ref(false);
const confirmOpen = ref(false);

const selected = computed(() => images.value.filter((i) => selectedIds.value.includes(i.id)));
const selectedBytes = computed(() => selected.value.reduce((sum, i) => sum + i.byteSize, 0));

/** 고른 것 중 작성 중인 글에서 쓰는 것. 지우기 직전에 한 번 더 알린다 */
const selectedInDrafts = computed(() => selected.value
  .filter((image) => draftUsers.value.has(image.url))
  .map((image) => ({ name: image.originalName, titles: draftUsers.value.get(image.url) })));

onMounted(load);

async function load() {
  loading.value = true;
  loadError.value = '';
  draftUsers.value = collectDraftImageUrls();

  try {
    const result = await getAdminImages({
      onlyUnused: filter.value === 'UNUSED',
      page: page.value - 1,
      size: pageSize.value,
    });

    images.value = result.items;
    unusedBytes.value = result.unusedBytes;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
    selectedIds.value = [];
  } catch (error) {
    loadError.value = error?.message ?? '이미지를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function changeFilter(next) {
  filter.value = next;
  page.value = 1;
  load();
}

function changePage(next) {
  page.value = next;
  load();
}

function changePageSize(next) {
  pageSize.value = next;
  page.value = 1;
  load();
}

async function remove() {
  if (deleting.value || selectedIds.value.length === 0) {
    return;
  }

  deleting.value = true;

  try {
    const result = await cleanupAdminImages([...selectedIds.value]);
    confirmOpen.value = false;
    await load();

    const message = cleanupMessage(result);

    if (result.failedCount > 0) {
      notifyError(message);
    } else {
      notifySuccess(message);
    }
  } catch (error) {
    const message = error?.message ?? '이미지를 지우지 못했습니다.';

    loadError.value = message;
    notifyError(message);
  } finally {
    deleting.value = false;
  }
}

function cleanupMessage(result) {
  const parts = [];

  if (result.deletedCount > 0) {
    parts.push(`이미지 ${result.deletedCount}장을 지웠습니다.`);
  }

  if (result.skippedUsedCount > 0) {
    parts.push(`${result.skippedUsedCount}장은 사용 중이라 건너뛰었습니다.`);
  }

  if (result.notFoundCount > 0) {
    parts.push(`${result.notFoundCount}장은 이미 없어졌습니다.`);
  }

  if (result.failedCount > 0) {
    parts.push(`${result.failedCount}장은 지우지 못했습니다.`);
  }

  return parts.join(' ') || '처리할 이미지가 없습니다.';
}

function draftTitlesOf(url) {
  return draftUsers.value.get(url) ?? [];
}

function formatSize(bytes) {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  }

  return `${Math.max(1, Math.round(bytes / 1024)).toLocaleString('ko-KR')}KB`;
}

function formatDate(value) {
  if (!value) {
    return '';
  }

  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(value))
    .replace(/\.$/, '');
}
</script>

<template>
  <div class="admin-image-page">
    <AdminPageHeader>
      <template #meta>
        올린 지 일주일이 지난 이미지입니다. 어디에 쓰이는지 확인하고 지울 것을 고르세요.
      </template>
    </AdminPageHeader>

    <AdminGridToolbar :total-count="totalElements">
      <template #left>
        <AdminPageSize :model-value="pageSize" @update:model-value="changePageSize" />
        <AdminSegmented
          label="사용 여부"
          :model-value="filter"
          :options="FILTERS"
          @update:model-value="changeFilter"
        />
        <span v-if="unusedBytes > 0" class="admin-toolbar__selected">
          안 쓰는 것 <strong>{{ formatSize(unusedBytes) }}</strong>
        </span>
        <span v-if="selectedIds.length" class="admin-toolbar__selected">
          선택 <strong>{{ selectedIds.length }}</strong>건 · {{ formatSize(selectedBytes) }}
        </span>
      </template>

      <template #right>
        <button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="selectedIds.length === 0"
          @click="confirmOpen = true"
        >선택 삭제</button>
        <button class="admin-button admin-button--solid" type="button" @click="load">조회</button>
      </template>
    </AdminGridToolbar>

    <AdminDataGrid
      v-model:selected-keys="selectedIds"
      :columns="COLUMNS"
      :rows="images"
      :loading="loading"
      :error-text="loadError"
      empty-text="조회조건에 맞는 이미지가 없습니다."
      selectable
      @retry="load"
    >
      <!-- 파일 이름만으로는 뭔지 모른다. 지우기 전에 눈으로 봐야 한다 -->
      <template #cell-preview="{ row }">
        <a :href="row.url" target="_blank" rel="noopener">
          <img class="admin-image-thumb" :src="row.url" :alt="row.originalName" loading="lazy" />
        </a>
      </template>

      <template #cell-originalName="{ row }">
        <span class="admin-image-name">
          <strong>{{ row.originalName }}</strong>
          <small>{{ row.contentType }}</small>
        </span>
      </template>

      <template #cell-usage="{ row }">
        <span class="admin-image-usage">
          <span
            v-for="(usage, index) in row.usages"
            :key="index"
            class="admin-badge admin-badge--on"
          >{{ WHERE_LABELS[usage.where] }}{{ usage.title ? ` · ${usage.title}` : '' }}</span>

          <!-- 서버는 임시저장을 모른다. 이 브라우저에 남은 글과 대조한 결과다 -->
          <span
            v-for="title in draftTitlesOf(row.url)"
            :key="`draft-${title}`"
            class="admin-badge admin-badge--danger"
          >작성 중 · {{ title }}</span>

          <span
            v-if="row.usages.length === 0 && draftTitlesOf(row.url).length === 0"
            class="admin-image-usage__none"
          >없음</span>
        </span>
      </template>

      <template #cell-byteSize="{ value }">{{ formatSize(value) }}</template>
      <template #cell-uploadedAt="{ value }">{{ formatDate(value) }}</template>
    </AdminDataGrid>

    <AdminPagination
      v-if="totalPages > 1"
      :page="page"
      :total-pages="totalPages"
      @change="changePage"
    />

    <BaseModal
      :open="confirmOpen"
      title="이미지 삭제"
      :description="`${selectedIds.length}장 · ${formatSize(selectedBytes)}`"
      size="small"
      variant="admin"
      @close="confirmOpen = false"
    >
      <!-- 고를 때는 막지 않고 여기서 한 번 더 말한다 -->
      <div v-if="selectedInDrafts.length" class="admin-image-warning" role="alert">
        <strong>작성 중인 글에서 쓰고 있습니다</strong>
        <ul>
          <li v-for="item in selectedInDrafts" :key="item.name">
            {{ item.name }} — {{ item.titles.join(', ') }}
          </li>
        </ul>
        <span>지우면 그 글의 사진이 깨집니다. 붙여넣은 스크린샷이면 다시 만들 수 없습니다.</span>
      </div>

      <p class="admin-confirm__text">R2 저장소에서 지웁니다. 되돌릴 수 없습니다.</p>
      <p class="admin-confirm__text">
        목록을 띄운 사이에 글에 넣은 이미지가 있으면 그것은 지우지 않고 넘어갑니다.
      </p>

      <template #footer>
        <button
          class="admin-button admin-button--danger"
          type="button"
          :disabled="deleting"
          @click="remove"
        >{{ deleting ? '지우는 중…' : '삭제' }}</button>
        <button
          class="admin-button admin-button--ghost"
          type="button"
          :disabled="deleting"
          @click="confirmOpen = false"
        >닫기</button>
      </template>
    </BaseModal>
  </div>
</template>
