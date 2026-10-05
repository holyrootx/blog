import { computed } from 'vue';

export const TITLE_MAX = 255;
export const EXCERPT_MAX = 500;
export const THUMBNAIL_MAX = 500;

/**
 * 글 편집 화면에서 무엇을 누를 수 있는지, 왜 못 누르는지.
 *
 * {@code keepsPublicRules} 는 발행·예약된 글인지다. 그런 글은 저장할 때도 발행 조건을 지켜야 한다.
 */
export function usePostEditRules({ form, keepsPublicRules, saveLabel }) {
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
  const canSave = computed(() => (keepsPublicRules.value ? canPublish.value : canSaveDraft.value));

  const saveBlockReason = computed(() => (keepsPublicRules.value ? publishBlockReason.value : draftBlockReason.value));

  /**
   * 지금 막혀 있는 이유 한 줄.
   *
   * 비활성 이유를 tooltip 으로만 두면 회색 버튼만 보고 왜 못 누르는지 알 수 없다.
   */
  const editorHint = computed(() => saveBlockReason.value || publishBlockReason.value);

  const lengthError = computed(() => {
    if (form.title.trim().length > TITLE_MAX) return `제목은 ${TITLE_MAX}자까지 입력할 수 있습니다.`;
    if (form.excerpt.length > EXCERPT_MAX) return `요약은 ${EXCERPT_MAX}자까지 입력할 수 있습니다.`;
    if (form.thumbnailImageUrl.length > THUMBNAIL_MAX) return '썸네일 주소는 500자까지 입력할 수 있습니다.';
    return '';
  });

  return {
    canSaveDraft,
    canPublish,
    publishBlockReason,
    canSave,
    saveBlockReason,
    editorHint,
    lengthError,
  };
}
