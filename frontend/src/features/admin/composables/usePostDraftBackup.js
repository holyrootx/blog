import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { clearPostDraft, loadPostDraft, savePostDraft } from '../data/adminPostDraftStore';

/**
 * 작성 중인 글을 브라우저에 잠깐 보관한다.
 *
 * 서버 자동저장이 아니다. 브라우저에만 남긴다.
 * 대상별로 키를 나누는 이유: 한 키에 덮어쓰면 글 A를 두고 B를 열었다 돌아왔을 때
 * A의 스냅샷이 B로 덮여 사라진다.
 *
 * {@code lastSavedForm} 은 마지막으로 서버에 저장한 내용이다. 이것과 같으면 남길 이유가 없다.
 * {@code serverUpdatedAt} 은 보관본이 어느 서버 값을 기준으로 만들어졌는지 비교하는 데 쓴다.
 */
export function usePostDraftBackup({ form, draftKey, serverUpdatedAt, lastSavedForm }) {
  const draftFound = ref(null);

  // 스냅샷을 만든 뒤 서버에서 글이 따로 바뀌었는지
  const draftConflict = ref(false);

  let draftTimer = null;

  // 한 글자라도 들어있는지. 빈 폼까지 남기면 /posts/new 를 열기만 해도 쓰레기가 쌓인다
  const hasAnyInput = computed(() => Object.values(form).some((value) => String(value).trim() !== ''));

  function scheduleDraftSave() {
    clearTimeout(draftTimer);
    // 매 글자마다 쓰면 긴 본문에서 직렬화 비용이 눈에 띄고,
    // 10초로 두면 방금 쓴 문단이 보호 범위 밖에 남는다
    draftTimer = setTimeout(saveDraftNow, 2000);
  }

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

  function closeDraftPrompt() {
    draftFound.value = null;
  }

  /** 서버 저장이 끝났다. 저장한 그대로면 보관본을 지우고, 그사이 더 쓴 게 있으면 그것을 남긴다 */
  function acknowledgeSavedForm(snapshot) {
    lastSavedForm.value = snapshot;
    if (JSON.stringify({ ...form }) === snapshot) {
      clearTimeout(draftTimer);
      clearPostDraft(draftKey.value);
    } else {
      saveDraftNow();
    }
  }

  function resetDraftPrompt() {
    draftFound.value = null;
    draftConflict.value = false;
  }

  /** 글을 불러온 직후에 부른다. 남은 보관본이 있으면 복구 창을 띄운다 */
  function offerSavedDraft() {
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

  onBeforeUnmount(() => {
    clearTimeout(draftTimer);
  });

  return {
    draftFound,
    draftConflict,
    saveDraftNow,
    restoreDraft,
    discardDraft,
    closeDraftPrompt,
    acknowledgeSavedForm,
    resetDraftPrompt,
    offerSavedDraft,
  };
}
