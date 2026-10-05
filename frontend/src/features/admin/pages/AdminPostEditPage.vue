<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';

import {
  createAdminPost,
  deleteAdminPost,
  getAdminCategories,
  getAdminPost,
  publishAdminPost,
  unpublishAdminPost,
  updateAdminPost,
} from '../api/adminApi';
import { cleanUpPostDrafts, clearPostDraft, savePostDraft } from '../data/adminPostDraftStore';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';
import { firstImageUrlOf } from '../data/postEditorBlocks';
import { formatEditorDateTime, toLocalInputValue } from '../data/postEditorDates';
import { EXCERPT_MAX, TITLE_MAX, usePostEditRules } from '../composables/usePostEditRules';
import { usePostDraftBackup } from '../composables/usePostDraftBackup';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSelect from '../components/AdminSelect.vue';
import AdminBlockEditor from '../components/AdminBlockEditor.vue';
import AdminPostThumbnailField from '../components/AdminPostThumbnailField.vue';
import AdminPostDraftDialog from '../components/AdminPostDraftDialog.vue';
import AdminPostActionDialog from '../components/AdminPostActionDialog.vue';
import { getFallbackPostImageUrl } from '../../../shared/post/postCardMapper';

const STATUS_LABELS = {
  PUBLISHED: '발행',
  SCHEDULED: '예약',
  PRIVATE: '비공개',
  DRAFT: '임시저장',
};

const route = useRoute();
const router = useRouter();

// 신규와 수정은 같은 화면, 같은 폼이다. 진입점만 다르다
const postId = computed(() => (route.params.postId ? Number(route.params.postId) : null));
const isNew = computed(() => postId.value === null);

const EMPTY_FORM = {
  title: '',
  categoryId: '',
  excerpt: '',
  content: '',
  thumbnailImageUrl: '',
};

const form = reactive({ ...EMPTY_FORM });
const status = ref('DRAFT');
const publishedAt = ref(null);
// 스냅샷이 어느 서버 값을 기준으로 만들어졌는지 비교하는 데 쓴다
const serverUpdatedAt = ref(null);
// 마지막으로 서버에 저장한 내용. 이것과 같으면 스냅샷을 남길 이유가 없다
const lastSavedForm = ref(null);

const categories = ref([]);
const loading = ref(!isNew.value);
const loadError = ref('');
const saving = ref(false);
const formError = ref('');
const savedMessage = ref('');

const confirmAction = ref('');

const firstBodyImageUrl = computed(() => firstImageUrlOf(form.content));
const representativeImageUrl = computed(() => form.thumbnailImageUrl
  || firstBodyImageUrl.value
  || getFallbackPostImageUrl());

// 발행 확인 창에서 고르는 시각. 비우면 지금 발행이다
const scheduledAt = ref('');

/** 예약 발행된 글. 상태가 알려주므로 시각을 따로 비교하지 않는다 */
const isScheduled = computed(() => status.value === 'SCHEDULED');

// datetime-local 이 과거를 못 고르게 막는 하한. 초 단위는 버린다
const earliestPublishAt = computed(() => toLocalInputValue(new Date()));

const isPublished = computed(() => !isNew.value && status.value === 'PUBLISHED');

// 발행된 글에 "임시저장"이라 써 있으면 "저장하면 임시저장으로 내려가나?"로 읽힌다.
// 실제로는 PUT 이 status 를 건드리지 않아 발행 상태 그대로 내용만 바뀐다
const saveLabel = computed(() => (isPublished.value || isScheduled.value ? '저장' : '임시저장'));

const categoryOptions = computed(() => categories.value.map((category) => ({
  value: String(category.id),
  label: category.name,
})));

const {
  canPublish,
  publishBlockReason,
  canSave,
  saveBlockReason,
  editorHint,
  lengthError,
} = usePostEditRules({
  form,
  keepsPublicRules: computed(() => isPublished.value || isScheduled.value),
  saveLabel,
});

const draftKey = computed(() => (isNew.value ? 'new' : String(postId.value)));

const {
  draftFound,
  draftConflict,
  saveDraftNow,
  restoreDraft,
  discardDraft,
  closeDraftPrompt,
  acknowledgeSavedForm,
  resetDraftPrompt,
  offerSavedDraft,
} = usePostDraftBackup({ form, draftKey, serverUpdatedAt, lastSavedForm });

/* ── 불러오기 ─────────────────────────────── */

async function loadPost() {
  if (isNew.value) {
    Object.assign(form, EMPTY_FORM);
    status.value = 'DRAFT';
    publishedAt.value = null;
    return;
  }

  loading.value = true;
  loadError.value = '';

  try {
    const post = await getAdminPost(postId.value);

    Object.assign(form, {
      title: post.title,
      categoryId: post.categoryId ? String(post.categoryId) : '',
      excerpt: post.excerpt,
      content: post.content,
      thumbnailImageUrl: post.thumbnailImageUrl,
    });

    status.value = post.status;
    publishedAt.value = post.publishedAt;
    serverUpdatedAt.value = post.updatedAt;
  } catch (error) {
    console.error(error);
    loadError.value = '글을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

/* ── 대표 이미지 ───────────────────────────── */

/** 대표 이미지가 비어 있으면 저장 시점의 본문 첫 이미지를 한 번 적용한다. */
function applyAutomaticThumbnail() {
  if (!form.thumbnailImageUrl) {
    form.thumbnailImageUrl = firstBodyImageUrl.value;
  }
}

/* ── 저장 ─────────────────────────────────── */

/**
 * 저장에 실패했다고 알린다.
 *
 * <p>인라인 문구만으로는 부족하다. 그 줄은 폼 맨 아래에 있어서, 긴 글을 쓰다가 저장을
 * 누르면 화면 밖이다. 성공은 토스트로 크게 알리면서 실패는 조용한 것은 방향이 거꾸로다.</p>
 *
 * <p>인라인도 같이 남긴다. 다시 시도 단추가 거기 붙어 있고, 토스트는 사라지기 때문이다.</p>
 */
function failWith(message) {
  formError.value = message;
  notifyError(message);
}

async function save() {
  if (saving.value || !canSave.value) {
    return;
  }

  applyAutomaticThumbnail();

  if (lengthError.value) {
    failWith(lengthError.value);
    return;
  }

  saving.value = true;
  formError.value = '';
  savedMessage.value = '';

  try {
    if (isNew.value) {
      await createFromForm();

      notifySuccess(`${saveLabel.value}했습니다.`);
    } else {
      await persistForm();

      // 서버가 저장하면서 updatedAt 을 바꾼다.
      // 안 받아오면 두 번째 저장이 옛 값을 보내 충돌로 막힌다
      await refreshServerState();

      savedMessage.value = '저장했습니다.';
    }
  } catch (error) {
    saveDraftNow();
    failWith(error.message);
  } finally {
    saving.value = false;
  }
}

function buildRequest() {
  return {
    // 등록에는 비교할 이전 값이 없다
    updatedAt: isNew.value ? null : serverUpdatedAt.value,
    title: form.title.trim(),
    categoryId: Number(form.categoryId),
    excerpt: form.excerpt,
    content: form.content,
    thumbnailImageUrl: form.thumbnailImageUrl || null,
  };
}

/**
 * 지금 화면에 있는 내용을 서버에 저장한다 (수정 전용).
 *
 * 저장 버튼과 발행·내리기가 같이 쓴다. 발행이 이걸 먼저 부르지 않으면
 * 서버에 있던 예전 본문이 공개된다.
 */
async function persistForm() {
  const snapshot = JSON.stringify({ ...form });
  saveDraftNow();
  await updateAdminPost(postId.value, buildRequest());
  acknowledgeSavedForm(snapshot);
  return postId.value;
}

/** 본문은 그대로 두고 서버 상태(수정 시각·발행 상태)만 다시 읽는다 */
async function refreshServerState() {
  try {
    const post = await getAdminPost(postId.value);

    serverUpdatedAt.value = post.updatedAt;
    status.value = post.status;
    publishedAt.value = post.publishedAt;
  } catch (error) {
    // 상태를 못 읽어도 저장 자체는 끝났다. 다음 저장에서 충돌이 뜨면 그때 알린다
    console.warn(error);
  }
}

const ACTION_LABELS = { publish: '발행', unpublish: '내리기' };

/**
 * 저장은 됐는데 상태 변경만 실패한 경우 남는다.
 * 내용은 이미 서버에 있으므로 다시 시도할 때는 상태 변경만 한다 —
 * 저장을 또 보내면 updatedAt 이 어긋나 충돌로 막힌다.
 */
const retryAction = ref('');

function openPublishConfirm() {
  // 기본은 지금 발행. 예약하려면 사용자가 미래 시각으로 바꾼다
  scheduledAt.value = '';
  confirmAction.value = 'publish';
}

async function runConfirmedAction() {
  if (saving.value) {
    return;
  }

  const action = confirmAction.value;

  if (action === 'cancelSchedule') {
    await cancelSchedule();
    return;
  }

  if (action === 'delete') {
    await runDelete();
    return;
  }

  // 발행·내리기는 저장 → 상태 변경 두 단계다. 저장을 건너뛰면 지금 쓴 내용이 아니라
  // 서버에 있던 예전 내용이 공개된다. 성공 뒤에는 상태만 다시 읽어 추가 입력을 보존한다
  applyAutomaticThumbnail();
  const blockReason = action === 'publish' ? publishBlockReason.value : saveBlockReason.value;

  if (lengthError.value || blockReason) {
    confirmAction.value = '';
    failWith(lengthError.value || blockReason);
    return;
  }

  saving.value = true;
  formError.value = '';
  savedMessage.value = '';
  retryAction.value = '';

  let saved = false;

  try {
    // 새 글은 서버에 아직 없다. 만들어야 발행할 대상이 생긴다 —
    // 사용자에게는 발행 한 번이고, 저장을 먼저 시키지 않는다
    const targetId = isNew.value ? await createFromForm() : await persistForm();
    saved = true;

    await changeStatus(action, targetId);

    confirmAction.value = '';
    await refreshServerState();

    notifySuccess(publishedMessage(action));
  } catch (error) {
    confirmAction.value = '';

    // 어디까지 됐는지가 사용자의 다음 행동을 정한다.
    // "발행 실패"라고만 하면 방금 쓴 내용도 날아갔다고 생각하고 다시 쓰게 된다
    if (saved) {
      retryAction.value = action;
      failWith(`내용은 저장되었지만 ${ACTION_LABELS[action]}하지 못했습니다. ${error.message}`);
    } else {
      saveDraftNow();
      failWith(error.message);
    }
  } finally {
    saving.value = false;
  }
}

/**
 * 결과 문구.
 *
 * 앞 문장은 언제나 같고 뒤 문장만 바뀐다 — "발행했습니다"와 "예약했습니다"로 갈라 두면
 * 발행이 된 건지 안 된 건지부터 헷갈린다. 된 건 하나뿐이고, 다른 건 언제 공개되느냐다.
 */
function publishedMessage(action) {
  if (action === 'unpublish') {
    return '글을 내렸습니다.';
  }

  return isScheduled.value
    ? `발행했습니다. ${formatEditorDateTime(publishedAt.value)}에 공개됩니다.`
    : '발행했습니다. 지금 공개됩니다.';
}

function changeStatus(action, targetId = postId.value) {
  if (action === 'unpublish') {
    return unpublishAdminPost(targetId);
  }

  // 빈 값이면 서버가 지금으로 잡는다
  return publishAdminPost(targetId, scheduledAt.value || null);
}

/** 새 글을 만들고 그 id 를 돌려준다. 주소도 그 글을 가리키게 바꾼다 */
let createdPostId = null;
async function createFromForm() {
  const snapshot = JSON.stringify({ ...form });
  saveDraftNow();
  const newId = await createAdminPost(buildRequest());
  acknowledgeSavedForm(snapshot);
  // 요청 중 추가한 입력도 수정 화면에서 복구할 수 있게 새 글의 키로 옮긴다.
  if (JSON.stringify({ ...form }) !== snapshot) {
    savePostDraft(String(newId), { ...form }, null);
  }
  clearPostDraft('new');
  createdPostId = Number(newId);
  await router.replace({ name: 'admin-post-edit', params: { postId: newId } });
  // 이동 직전 이탈 가드가 new 보관본을 다시 남길 수 있다. 해당 글로 이동한 뒤에
  // 정리해야 다음 새 글에 섞이지 않고, 이동이 취소됐을 때는 복구본을 유지한다.
  if (postId.value === Number(newId)) clearPostDraft('new');
  await refreshServerState();
  return newId;
}

async function cancelSchedule() {
  saving.value = true;
  formError.value = '';
  saveDraftNow();
  try {
    await unpublishAdminPost(postId.value);
    status.value = 'DRAFT';
    publishedAt.value = null;
    await refreshServerState();
    notifySuccess('예약을 취소했습니다. 작성 중인 내용은 유지됩니다.');
  } catch (error) {
    failWith(error.message);
  } finally {
    confirmAction.value = '';
    saving.value = false;
  }
}

async function retryStatusChange() {
  if (saving.value || !retryAction.value) {
    return;
  }

  saving.value = true;
  formError.value = '';

  try {
    const action = retryAction.value;

    await changeStatus(action);
    retryAction.value = '';

    await refreshServerState();

    notifySuccess(publishedMessage(action));
  } catch (error) {
    failWith(`${ACTION_LABELS[retryAction.value]}하지 못했습니다. ${error.message}`);
  } finally {
    saving.value = false;
  }
}

async function runDelete() {
  saving.value = true;
  formError.value = '';

  try {
    await deleteAdminPost(postId.value);
    clearPostDraft(draftKey.value);
    confirmAction.value = '';

    // 목록으로 옮겨 가므로 알림이 그 화면에서 보인다
    notifySuccess('휴지통으로 이동했습니다.');
    await router.push({ name: 'admin-posts' });
  } catch (error) {
    confirmAction.value = '';
    failWith(error.message);
  } finally {
    saving.value = false;
  }
}

/* ── 생명주기 ─────────────────────────────── */

async function enter() {
  resetDraftPrompt();

  // 앞 글에서 남은 안내와 다시 시도 버튼을 들고 오면 엉뚱한 글을 발행하게 된다
  formError.value = '';
  savedMessage.value = '';
  retryAction.value = '';

  await loadPost();

  // 불러온 그대로면 스냅샷을 남길 이유가 없다.
  // 손대기 전까지는 브라우저에 아무것도 쓰지 않는다
  lastSavedForm.value = JSON.stringify({ ...form });

  offerSavedDraft();
}

// 새로고침·창 닫기 직전에는 동기적으로 남긴다
function onBeforeUnload() {
  saveDraftNow();
}

async function loadCategories() {
  try {
    categories.value = await getAdminCategories();
  } catch (error) {
    console.warn(error);
    categories.value = [];
  }
}

onMounted(() => {
  cleanUpPostDrafts();
  window.addEventListener('beforeunload', onBeforeUnload);

  // 분류 응답을 기다렸다가 폼을 초기화하면 그동안 작성한 입력을 지우게 된다.
  enter();
  loadCategories();
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload);
});

// 사이드바로 다른 화면에 가기 직전에도 남긴다
onBeforeRouteLeave(() => {
  saveDraftNow();
});

// 같은 화면에서 대상이 바뀌는 경우 (새 글 저장 후 수정 화면이 되는 등)
watch(postId, (id) => {
  if (id === createdPostId) {
    createdPostId = null;
    return;
  }
  enter();
});
</script>

<template>
  <div class="admin-post-edit">
    <AdminPageHeader :title="isNew ? '새 글 쓰기' : '글 편집'">
      <template #actions>
        <RouterLink class="admin-button admin-button--ghost" :to="{ name: 'admin-posts' }">
          목록
        </RouterLink>
      </template>
    </AdminPageHeader>

    <div v-if="loading" class="admin-editor-skeleton" aria-busy="true">
      <div class="admin-editor__bar" aria-hidden="true">
        <span class="ui-skeleton"></span>
        <div class="admin-editor__actions">
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
        </div>
      </div>
      <div class="admin-editor__form" aria-hidden="true">
        <span class="ui-skeleton admin-editor-skeleton__title"></span>
        <span class="ui-skeleton admin-editor-skeleton__select"></span>
        <span class="ui-skeleton admin-editor-skeleton__excerpt"></span>
        <span class="ui-skeleton admin-editor-skeleton__input"></span>
        <span class="ui-skeleton admin-editor-skeleton__body"></span>
      </div>
    </div>

    <div v-else-if="loadError" class="admin-tree__state">
      <div class="admin-grid__error">
        <span class="admin-grid__error-icon" aria-hidden="true">!</span>
        <span>{{ loadError }}</span>
        <button class="admin-grid__retry" type="button" @click="enter">다시 시도</button>
      </div>
    </div>

    <template v-else>
      <!-- 작업 바. 상태와 발행일은 읽기 전용이고, 상태는 액션의 결과다 -->
      <div class="admin-editor__bar">
        <div class="admin-editor__meta">
          <span class="admin-badge" :class="status === 'PUBLISHED' ? 'admin-badge--on' : 'admin-badge--off'">
            {{ STATUS_LABELS[status] }}
          </span>
          <span v-if="publishedAt" class="admin-editor__published">
            {{ formatEditorDateTime(publishedAt) }} {{ isScheduled ? '공개 예정' : '발행' }}
          </span>
          <span v-if="savedMessage" class="admin-editor__saved">{{ savedMessage }}</span>
        </div>

        <div class="admin-editor__actions">
          <button
            v-if="!isNew"
            class="admin-button admin-button--danger"
            type="button"
            :disabled="saving"
            @click="confirmAction = 'delete'"
          >
            삭제
          </button>

          <button
            class="admin-button admin-button--ghost"
            type="button"
            :disabled="saving || !canSave"
            :title="saveBlockReason"
            @click="save"
          >
            {{ saving ? '저장 중…' : saveLabel }}
          </button>

          <button
            v-if="isScheduled"
            class="admin-button admin-button--ghost"
            type="button"
            :disabled="saving"
            @click="confirmAction = 'cancelSchedule'"
          >예약 취소</button>
          <button
            v-if="!isNew && status === 'PUBLISHED'"
            class="admin-button admin-button--solid"
            type="button"
            :disabled="saving"
            @click="confirmAction = 'unpublish'"
          >
            내리기
          </button>
          <button
            v-else
            class="admin-button admin-button--solid"
            type="button"
            :disabled="saving || !canPublish"
            :title="publishBlockReason"
            @click="openPublishConfirm"
          >
            발행
          </button>
        </div>
      </div>

      <!-- 발행된 글의 저장은 즉시 공개 반영이다. 확인 다이얼로그 대신 상시 문구를 둔다 —
           오타 하나마다 다이얼로그가 뜨면 반사적으로 확인을 누르게 되어 아무것도 막지 못한다 -->
      <p v-if="isPublished" class="admin-editor__hint admin-editor__hint--live">
        저장하면 공개 글에 바로 반영됩니다.
      </p>

      <!-- 비활성인 이유를 버튼 옆이 아니라 줄로 적는다. title 속성만으로는 보이지 않는다 -->
      <p v-if="editorHint" class="admin-editor__hint">{{ editorHint }}</p>

      <div class="admin-editor__form">
        <div class="admin-editor__row">
          <AdminTextInput v-model="form.title" label="제목" placeholder="글 제목" />
          <span class="admin-editor__counter" :class="{ 'admin-editor__counter--over': form.title.length > TITLE_MAX }">
            {{ form.title.length }} / {{ TITLE_MAX }}
          </span>
        </div>

        <AdminSelect v-model="form.categoryId" label="카테고리" :options="categoryOptions" />

        <div class="admin-editor__row">
          <label class="admin-field">
            <span class="admin-field__label">요약</span>
            <textarea
              v-model="form.excerpt"
              class="admin-field__input admin-editor__textarea"
              rows="3"
              placeholder="목록 카드와 검색 결과에 쓰입니다."
            ></textarea>
          </label>
          <span class="admin-editor__counter" :class="{ 'admin-editor__counter--over': form.excerpt.length > EXCERPT_MAX }">
            {{ form.excerpt.length }} / {{ EXCERPT_MAX }}
          </span>
        </div>

        <AdminPostThumbnailField
          v-model="form.thumbnailImageUrl"
          :preview-url="representativeImageUrl"
          :post-title="form.title"
          :disabled="saving"
        />

        <div class="admin-field">
          <span class="admin-field__label">
            본문
            <em class="admin-editor__tip">/ 를 눌러 블록을 고르거나 마크다운을 그대로 쳐도 됩니다</em>
          </span>

          <!-- 쓰는 자리가 곧 결과다. 미리보기를 따로 두지 않는다 -->
          <AdminBlockEditor v-model="form.content" />
        </div>
      </div>

      <!-- 저장까지는 됐는데 발행만 실패한 경우, 저장을 다시 보내지 않고 발행만 다시 시도한다 -->
      <p v-if="formError" class="admin-form-error">
        {{ formError }}
        <button
          v-if="retryAction"
          class="admin-button admin-button--ghost admin-button--small"
          type="button"
          :disabled="saving"
          @click="retryStatusChange"
        >
          {{ ACTION_LABELS[retryAction] }} 다시 시도
        </button>
      </p>
    </template>


    <!-- 로컬 스냅샷 복구 -->
    <AdminPostDraftDialog
      :draft="draftFound"
      :conflict="draftConflict"
      @restore="restoreDraft"
      @discard="discardDraft"
      @close="closeDraftPrompt"
    />

    <!-- 발행·내리기·삭제 확인 -->
    <AdminPostActionDialog
      v-model:scheduled-at="scheduledAt"
      :action="confirmAction"
      :post-title="form.title"
      :saving="saving"
      :earliest-publish-at="earliestPublishAt"
      @confirm="runConfirmedAction"
      @cancel="confirmAction = ''"
    />
  </div>
</template>
