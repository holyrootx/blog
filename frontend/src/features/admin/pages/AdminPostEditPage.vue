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
import {
  clearPostDraft,
  loadPostDraft,
  savePostDraft,
} from '../data/adminPostDraftStore';
import { notifySuccess } from '../data/adminToastStore';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import AdminTextInput from '../components/AdminTextInput.vue';
import AdminSelect from '../components/AdminSelect.vue';
import AdminModal from '../components/AdminModal.vue';
import AdminBlockEditor from '../components/AdminBlockEditor.vue';

const TITLE_MAX = 255;
const EXCERPT_MAX = 500;
const THUMBNAIL_MAX = 500;

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
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const formError = ref('');
const savedMessage = ref('');

const confirmAction = ref('');

// 발행 확인 창에서 고르는 시각. 비우면 지금 발행이다
const scheduledAt = ref('');

/** 예약 발행된 글. 상태가 알려주므로 시각을 따로 비교하지 않는다 */
const isScheduled = computed(() => status.value === 'SCHEDULED');

// datetime-local 이 과거를 못 고르게 막는 하한. 초 단위는 버린다
const earliestPublishAt = computed(() => toLocalInputValue(new Date()));
const draftFound = ref(null);

// 스냅샷을 만든 뒤 서버에서 글이 따로 바뀌었는지
const draftConflict = ref(false);

const isPublished = computed(() => !isNew.value && status.value === 'PUBLISHED');

// 발행된 글에 "임시저장"이라 써 있으면 "저장하면 임시저장으로 내려가나?"로 읽힌다.
// 실제로는 PUT 이 status 를 건드리지 않아 발행 상태 그대로 내용만 바뀐다
const saveLabel = computed(() => (isPublished.value ? '저장' : '임시저장'));

const categoryOptions = computed(() => categories.value.map((category) => ({
  value: String(category.id),
  label: category.name,
})));

// 임시저장의 최소 조건은 타협이 아니라 DB 제약이다.
// title 과 category_id 가 NOT NULL 이라 이 둘 없이는 INSERT 자체가 안 된다
const canSaveDraft = computed(() => form.title.trim().length > 0 && form.categoryId !== '');

const draftBlockReason = computed(() => {
  if (canSaveDraft.value) {
    return '';
  }

  return `제목과 카테고리를 채우면 ${saveLabel.value}할 수 있습니다.`;
});

// 발행은 되돌리기 비용이 비싸서 검증도 엄격하다
const canPublish = computed(() => canSaveDraft.value
  && form.content.trim().length > 0
  && form.excerpt.trim().length > 0);

const publishBlockReason = computed(() => {
  if (canPublish.value) {
    return '';
  }

  if (!canSaveDraft.value) {
    return draftBlockReason.value;
  }

  if (form.content.trim().length === 0) {
    return '본문을 채우면 발행할 수 있습니다.';
  }

  return '요약을 채우면 발행할 수 있습니다.';
});

// 발행된 글은 발행 조건을 계속 만족해야 한다.
// 공개된 글에서 요약을 지우면 공개 화면 카드가 빈다
const canSave = computed(() => (isPublished.value ? canPublish.value : canSaveDraft.value));

const saveBlockReason = computed(() => (isPublished.value ? publishBlockReason.value : draftBlockReason.value));

/**
 * 지금 막혀 있는 이유 한 줄.
 *
 * 비활성 이유를 tooltip 으로만 두면 회색 버튼만 보고 왜 못 누르는지 알 수 없다.
 */
const editorHint = computed(() => {
  if (saveBlockReason.value) {
    return saveBlockReason.value;
  }

  return publishBlockReason.value;
});

const lengthError = computed(() => {
  if (form.title.trim().length > TITLE_MAX) return `제목은 ${TITLE_MAX}자까지 입력할 수 있습니다.`;
  if (form.excerpt.length > EXCERPT_MAX) return `요약은 ${EXCERPT_MAX}자까지 입력할 수 있습니다.`;
  if (form.thumbnailImageUrl.length > THUMBNAIL_MAX) return '썸네일 주소는 500자까지 입력할 수 있습니다.';
  return '';
});

const draftKey = computed(() => (isNew.value ? 'new' : String(postId.value)));

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

/* ── 로컬 임시 보관 ───────────────────────── */
// 서버 자동저장이 아니다. 브라우저에만 남긴다.
// 대상별로 키를 나누는 이유: 한 키에 덮어쓰면 글 A를 두고 B를 열었다 돌아왔을 때
// A의 스냅샷이 B로 덮여 사라진다

let draftTimer = null;

function scheduleDraftSave() {
  clearTimeout(draftTimer);
  // 매 글자마다 쓰면 긴 본문에서 직렬화 비용이 눈에 띄고,
  // 10초로 두면 방금 쓴 문단이 보호 범위 밖에 남는다
  draftTimer = setTimeout(saveDraftNow, 2000);
}

// 한 글자라도 들어있는지. 빈 폼까지 남기면 /posts/new 를 열기만 해도 쓰레기가 쌓인다
const hasAnyInput = computed(() => Object.values(form).some((value) => String(value).trim() !== ''));

function saveDraftNow() {
  clearTimeout(draftTimer);

  // 임시저장 버튼이 비활성인 동안에도 로컬 보관은 계속 돌아간다.
  // 제목을 안 붙였다는 이유로 본문 30분치를 버리면 보관의 존재 이유가 없다
  if (!hasAnyInput.value) {
    return;
  }

  // 서버에 저장한 내용 그대로면 새로 남길 이유가 없다.
  // 여기서 지우지는 않는다 — 글을 열면 loadPost 가 폼을 바꾸고, 그 watcher 가 건 2초 타이머가
  // 복구 창이 떠 있는 동안 여기에 도달한다. 지우면 사용자가 고르기도 전에 보관본이 사라진다.
  // 지우는 일은 명시적인 경로(버리기·저장 성공·글 삭제)만 한다
  if (lastSavedForm.value && JSON.stringify({ ...form }) === lastSavedForm.value) {
    return;
  }

  savePostDraft(draftKey.value, { ...form }, serverUpdatedAt.value);
}

function restoreDraft() {
  Object.assign(form, draftFound.value.form);
  draftFound.value = null;
  draftConflict.value = false;
}

function discardDraft() {
  clearPostDraft(draftKey.value);
  draftFound.value = null;
  draftConflict.value = false;
}

/* ── 저장 ─────────────────────────────────── */

async function save() {
  if (saving.value || !canSave.value) {
    return;
  }

  if (lengthError.value) {
    formError.value = lengthError.value;
    return;
  }

  saving.value = true;
  formError.value = '';
  savedMessage.value = '';

  try {
    if (isNew.value) {
      clearTimeout(draftTimer);
      lastSavedForm.value = JSON.stringify({ ...form });

      const newId = await createAdminPost(buildRequest());
      clearPostDraft('new');
      // 저장했으니 이제 수정 화면이다. 주소도 그 글을 가리켜야 한다
      await router.replace({ name: 'admin-post-edit', params: { postId: newId } });

      notifySuccess(`${saveLabel.value}했습니다.`);
    } else {
      await persistForm();

      // 서버가 저장하면서 updatedAt 을 바꾼다.
      // 안 받아오면 두 번째 저장이 옛 값을 보내 충돌로 막힌다
      await refreshServerState();

      savedMessage.value = '저장했습니다.';
    }
  } catch (error) {
    formError.value = error.message;
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
    thumbnailImageUrl: form.thumbnailImageUrl,
  };
}

/**
 * 지금 화면에 있는 내용을 서버에 저장한다 (수정 전용).
 *
 * 저장 버튼과 발행·내리기가 같이 쓴다. 발행이 이걸 먼저 부르지 않으면
 * 서버에 있던 예전 본문이 공개된다.
 */
async function persistForm() {
  // 저장 성공 뒤에 디바운스 타이머가 돌아 옛 내용을 다시 남기는 것을 막는다
  clearTimeout(draftTimer);
  lastSavedForm.value = JSON.stringify({ ...form });

  try {
    await updateAdminPost(postId.value, buildRequest());
    clearPostDraft(draftKey.value);

    return postId.value;
  } catch (error) {
    // 저장에 실패했으면 스냅샷은 계속 남겨야 한다
    lastSavedForm.value = null;
    throw error;
  }
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

  if (action === 'delete') {
    await runDelete();
    return;
  }

  // 발행·내리기는 저장 → 상태 변경 두 단계다. 저장을 건너뛰면 지금 쓴 내용이 아니라
  // 서버에 있던 예전 내용이 공개되고, 이어지는 loadPost 가 화면의 내용까지 덮는다
  const blockReason = action === 'publish' ? publishBlockReason.value : saveBlockReason.value;

  if (lengthError.value || blockReason) {
    confirmAction.value = '';
    formError.value = lengthError.value || blockReason;
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
    await loadPost();
    lastSavedForm.value = JSON.stringify({ ...form });

    notifySuccess(publishedMessage(action));
  } catch (error) {
    confirmAction.value = '';

    // 어디까지 됐는지가 사용자의 다음 행동을 정한다.
    // "발행 실패"라고만 하면 방금 쓴 내용도 날아갔다고 생각하고 다시 쓰게 된다
    if (saved) {
      retryAction.value = action;
      formError.value = `내용은 저장되었지만 ${ACTION_LABELS[action]}하지 못했습니다. ${error.message}`;
    } else {
      formError.value = error.message;
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
    ? `발행했습니다. ${formatDateTime(publishedAt.value)}에 공개됩니다.`
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
async function createFromForm() {
  clearTimeout(draftTimer);
  lastSavedForm.value = JSON.stringify({ ...form });

  try {
    const newId = await createAdminPost(buildRequest());
    clearPostDraft('new');

    await router.replace({ name: 'admin-post-edit', params: { postId: newId } });

    return newId;
  } catch (error) {
    lastSavedForm.value = null;
    throw error;
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

    await loadPost();
    lastSavedForm.value = JSON.stringify({ ...form });

    notifySuccess(publishedMessage(action));
  } catch (error) {
    formError.value = `${ACTION_LABELS[retryAction.value]}하지 못했습니다. ${error.message}`;
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
    notifySuccess('글을 삭제했습니다.');
    await router.push({ name: 'admin-posts' });
  } catch (error) {
    confirmAction.value = '';
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}

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
    description: '글과 달린 댓글이 함께 지워집니다. 되돌릴 수 없습니다.',
  },
};

const confirmText = computed(() => CONFIRM_TEXTS[confirmAction.value] ?? { title: '', description: '' });

/** datetime-local 입력이 쓰는 형식(YYYY-MM-DDTHH:mm)으로. UTC 로 바꾸면 시간이 밀린다 */
function toLocalInputValue(date) {
  const pad = (number) => String(number).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatDateTime(value) {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const pad = (number) => String(number).padStart(2, '0');

  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/* ── 생명주기 ─────────────────────────────── */

async function enter() {
  draftFound.value = null;
  draftConflict.value = false;

  // 앞 글에서 남은 안내와 다시 시도 버튼을 들고 오면 엉뚱한 글을 발행하게 된다
  formError.value = '';
  savedMessage.value = '';
  retryAction.value = '';

  await loadPost();

  // 불러온 그대로면 스냅샷을 남길 이유가 없다.
  // 손대기 전까지는 브라우저에 아무것도 쓰지 않는다
  lastSavedForm.value = JSON.stringify({ ...form });

  const draft = loadPostDraft(draftKey.value);

  if (!draft) {
    return;
  }

  // 내용이 같으면 물어볼 이유가 없다
  if (JSON.stringify(draft.form) === JSON.stringify({ ...form })) {
    clearPostDraft(draftKey.value);
    return;
  }

  // 스냅샷이 기준으로 삼은 서버 값과 지금 서버 값이 다르면, 그 사이 다른 곳에서 글이 바뀐 것이다.
  // 그래도 말없이 버리지 않는다 — 사용자가 쓰던 내용을 묻지도 않고 지우는 것이
  // 이 기능이 막으려는 사고 그 자체다. 어느 쪽이 최신인지 알려주고 고르게 한다
  draftConflict.value = Boolean(draft.baseUpdatedAt)
    && Boolean(serverUpdatedAt.value)
    && draft.baseUpdatedAt !== serverUpdatedAt.value;

  draftFound.value = draft;
}

// 폼이 바뀌면 2초 뒤에 스냅샷을 남긴다
watch(form, scheduleDraftSave, { deep: true });

// 새로고침·창 닫기 직전에는 동기적으로 남긴다
function onBeforeUnload() {
  saveDraftNow();
}

onMounted(async () => {
  try {
    categories.value = await getAdminCategories();
  } catch (error) {
    console.warn(error);
    categories.value = [];
  }

  await enter();
  window.addEventListener('beforeunload', onBeforeUnload);
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload);
  clearTimeout(draftTimer);
});

// 사이드바로 다른 화면에 가기 직전에도 남긴다
onBeforeRouteLeave(() => {
  saveDraftNow();
});

// 같은 화면에서 대상이 바뀌는 경우 (새 글 저장 후 수정 화면이 되는 등)
watch(postId, enter);
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

    <div v-if="loadError" class="admin-tree__state">
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
            {{ formatDateTime(publishedAt) }} {{ isScheduled ? '공개 예정' : '발행' }}
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

        <AdminTextInput
          v-model="form.thumbnailImageUrl"
          label="썸네일 주소"
          placeholder="https://..."
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
    <AdminModal
      :open="draftFound !== null"
      title="작성 중이던 내용이 있습니다"
      description="브라우저에 남아 있던 내용입니다. 불러올까요?"
      size="small"
      :close-on-backdrop="false"
      @close="draftFound = null"
    >
      <p class="admin-post__confirm">
        {{ formatDateTime(draftFound?.savedAt) }}에 보관됨
      </p>

      <p v-if="draftConflict" class="admin-category__confirm-note">
        보관한 뒤 서버에서 이 글이 따로 바뀌었습니다.
        불러오면 화면의 내용이 보관본으로 덮이고, 저장할 때 서버 내용을 덮어씁니다.
      </p>

      <template #footer>
        <button class="admin-button admin-button--ghost" type="button" @click="discardDraft">
          버리기
        </button>
        <button class="admin-button admin-button--solid" type="button" @click="restoreDraft">
          불러오기
        </button>
      </template>
    </AdminModal>

    <!-- 발행·내리기·삭제 확인 -->
    <AdminModal
      :open="confirmAction !== ''"
      :title="confirmText.title"
      :description="confirmText.description"
      size="small"
      @close="confirmAction = ''"
    >
      <p class="admin-post__confirm">{{ form.title }}</p>

      <!-- 발행 시각은 여기서만 정한다. 글의 내용이 아니라 발행이라는 행동에 딸린 값이다.
           과거는 고를 수 없다 — 지나간 시각에 발행할 일이 없다 -->
      <label v-if="confirmAction === 'publish'" class="admin-field admin-post__schedule">
        <span class="admin-field__label">발행 시각</span>
        <input
          v-model="scheduledAt"
          class="admin-field__input"
          type="datetime-local"
          :min="earliestPublishAt"
        />
        <small class="admin-post__schedule-hint">
          {{ scheduledAt ? '그때까지 공개 화면에 나오지 않습니다.' : '비워 두면 지금 발행합니다.' }}
        </small>
      </label>

      <template #footer>
        <button class="admin-button admin-button--ghost" type="button" @click="confirmAction = ''">
          취소
        </button>
        <button
          class="admin-button"
          :class="confirmAction === 'delete' ? 'admin-button--danger' : 'admin-button--solid'"
          type="button"
          :disabled="saving"
          @click="runConfirmedAction"
        >
          {{ saving ? '처리 중…' : confirmAction === 'delete' ? '삭제' : '확인' }}
        </button>
      </template>
    </AdminModal>
  </div>
</template>
