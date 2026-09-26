<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createPostComment,
  deletePostComment,
  getPostComments,
  removeCommentReaction,
  reportComment,
  setCommentReaction,
  updatePostComment,
} from '../api/postApi';
import CommentActionIcon from './CommentActionIcon.vue';
import CommentReportDialog from './CommentReportDialog.vue';
import { signOutMember, useMemberAuth } from '../../member/data/memberAuthStore';
import { rememberReturnPath } from '../../member/data/memberReturnPath';

const props = defineProps({
  postId: {
    type: [Number, String],
    required: true,
  },
  comments: {
    type: Object,
    required: true,
  },
  initialLoading: {
    type: Boolean,
    default: false,
  },
});

const route = useRoute();
const router = useRouter();

// 읽는 것은 로그인이 필요 없다. 쓰는 자리만 갈린다 —
// 검색으로 들어온 사람에게 댓글이 안 보이면 글의 내용이 그만큼 깎인다
const { member, isSignedIn } = useMemberAuth();

const total = ref(0);
const items = ref([]);
const nextCursor = ref(null);
const hasNext = ref(false);
const loading = ref(false);
const submitting = ref(false);
const replySubmitting = ref(false);
const reactionPendingIds = ref(new Set());
const draft = ref('');
const replyTargetId = ref(null);

// 칸을 열어 놓고 커서를 옮겨 주지 않으면 쓰려던 사람이 한 번 더 눌러야 한다
const commentInput = ref(null);
const replyInput = ref(null);
const signinBox = ref(null);
const replyDraft = ref('');
const formError = ref('');
const replyError = ref('');
const reactionError = ref('');
const showBottomAd = false;

/**
 * 신고 창.
 *
 * 대상 댓글을 들고 있다가 보낼 때 쓴다. 신고는 잘못 누르면 남을 가리키는 일이라
 * 바로 보내지 않고 한 번 더 묻는다.
 */
const reportTarget = ref(null);
const reportPending = ref(false);
const reportError = ref('');
const reportDone = ref('');

/**
 * 고치는 중인 댓글과 지우려고 묻는 중인 댓글.
 *
 * 지우기는 한 번 더 묻는다. 되돌릴 수 없는데 좋아요 옆에 붙어 있어서, 누르자마자
 * 지워지면 잘못 누른 사람이 손쓸 틈이 없다.
 */
const editingId = ref(null);
const editDraft = ref('');
const editPending = ref(false);
const editError = ref('');
const deletingId = ref(null);
const deletePending = ref(false);

const sentinel = ref(null);
let observer = null;

const commentPlaceholder = computed(() => props.comments.placeholder ?? '');
const commentMaxLength = computed(() => props.comments.maxLength ?? 1000);

/**
 * 누구 이름으로 쓰이는지 알려 주는 문구.
 *
 * 새로고침하면 로그인 상태만 남고 닉네임이 비는 구간이 있다 — 회원 정보를 다시 물어볼
 * 경로가 아직 없어서다. 그때는 이름 없이 로그인했다는 것만 말한다.
 */
const writingAs = computed(() => {
  const nickname = member.value?.nickname ?? '';

  return nickname === '' ? '로그인한 계정으로 작성됩니다.' : `${nickname} 님으로 작성됩니다.`;
});

/**
 * 프사를 실제로 그릴 수 있는가.
 *
 * <p>주소가 와도 그림이 안 뜨는 경우가 있다. 구글 프사({@code lh3.googleusercontent.com})는
 * 리퍼러를 보고 거부하기도 하고, 사용자가 사진을 바꾸면 옛 주소가 죽는다.
 * {@code alt} 가 비어 있어서 실패해도 깨진 아이콘조차 안 뜨고 빈 원만 남는다 —
 * 그래서 실패를 잡아 첫 글자로 되돌린다.</p>
 */
const avatarImageFailed = ref(false);

const showAvatarImage = computed(
  () => Boolean(member.value?.profileImageUrl) && !avatarImageFailed.value,
);

// 로그인한 사람이 바뀌면 실패 기록도 지운다. 안 그러면 다음 사람 프사가 안 뜬다
watch(() => member.value?.profileImageUrl, () => {
  avatarImageFailed.value = false;
});

/**
 * 아바타에 넣을 첫 글자.
 *
 * 프로필 이미지를 아직 못 받아서(서버 응답에 주소가 없다) 빈 원만 떠 있었다.
 * 글자 하나라도 있으면 누구 자리인지 읽히고, 이미지가 생기면 그때 이 자리를 바꾸면 된다.
 *
 * 이모지 닉네임이 반쪽으로 잘리지 않게 코드 포인트 단위로 자른다.
 */
function avatarInitial(name) {
  return Array.from(name ?? '')[0] ?? '';
}

async function signOut() {
  // 쓰던 내용은 지운다. 로그아웃한 사람의 이름으로 올라갈 글이 칸에 남아 있으면 안 된다
  draft.value = '';
  replyDraft.value = '';
  replyTargetId.value = null;

  await signOutMember();
  items.value.forEach((comment) => {
    comment.likedByMe = false;
    comment.dislikedByMe = false;
    comment.replies.forEach((reply) => {
      reply.likedByMe = false;
      reply.dislikedByMe = false;
    });
  });
}

function goToLogin() {
  // 로그인은 제공자 화면까지 다녀오는 길이라 라우터 상태가 남지 않는다.
  // 보던 글을 적어 두지 않으면 돌아왔을 때 홈으로 떨어진다
  rememberReturnPath(route.fullPath);
  router.push({ name: 'member-login' });
}

function toggleReply(commentId) {
  replyTargetId.value = replyTargetId.value === commentId ? null : commentId;
  replyDraft.value = '';
  replyError.value = '';

  if (replyTargetId.value === null) {
    return;
  }

  // 답글 칸은 지금 막 만들어져서 아직 화면에 없다. 그려진 뒤에 커서를 옮긴다.
  // v-for 안이라 ref 는 배열로 들어오는데, 열려 있는 답글 폼은 언제나 하나뿐이다
  nextTick(() => {
    const input = Array.isArray(replyInput.value) ? replyInput.value[0] : replyInput.value;

    input?.focus();
  });
}

/**
 * 본문의 "댓글 N" 에서 부른다. 댓글 칸으로 데려오고 커서까지 옮긴다.
 *
 * 로그인하지 않았으면 쓰는 칸이 없다. 그때는 로그인 안내까지만 데려간다 —
 * 아무 일도 안 일어나면 버튼이 고장 난 것처럼 보인다.
 *
 * 스크롤을 먼저 하고 포커스는 preventScroll 로 준다. 반대로 하면 focus 가 즉시
 * 튕겨 올린 뒤 부드러운 스크롤이 덮어써서 화면이 두 번 움직인다.
 */
function focusCommentInput() {
  const target = commentInput.value ?? signinBox.value;

  target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  commentInput.value?.focus({ preventScroll: true });
}

defineExpose({ focusCommentInput });

async function submitComment() {
  const content = draft.value.trim();

  if (!content || submitting.value) {
    formError.value = content ? '' : '댓글 내용을 입력해 주세요.';
    return;
  }

  submitting.value = true;
  formError.value = '';

  try {
    const created = await createPostComment(props.postId, { content });
    items.value = [toCreatedComment(created?.id, content), ...items.value];
    total.value += 1;
    draft.value = '';
  } catch (error) {
    formError.value = error?.message ?? '댓글을 등록하지 못했습니다.';
  } finally {
    submitting.value = false;
  }
}

async function submitReply(parentId) {
  const content = replyDraft.value.trim();

  if (!content || replySubmitting.value) {
    replyError.value = content ? '' : '답글 내용을 입력해 주세요.';
    return;
  }

  replySubmitting.value = true;
  replyError.value = '';

  try {
    const created = await createPostComment(props.postId, { content, parentId });
    const parent = items.value.find((comment) => comment.id === parentId);

    if (parent) {
      parent.replies = [...parent.replies, toCreatedComment(created?.id, content)];
    }

    total.value += 1;
    replyDraft.value = '';
    replyTargetId.value = null;
  } catch (error) {
    replyError.value = error?.message ?? '답글을 등록하지 못했습니다.';
  } finally {
    replySubmitting.value = false;
  }
}

function toCreatedComment(id, content) {
  return {
    id,
    author: member.value?.nickname ?? '',
    createdAt: '방금 전',
    content,
    isAuthor: member.value?.role === 'ADMIN',
    deleted: false,
    likeCount: 0,
    dislikeCount: 0,
    likedByMe: false,
    dislikedByMe: false,
    // 방금 내가 쓴 것이다. 안 넣으면 새로고침 전까지 내 댓글에 신고 단추가 보인다
    mine: true,
    hiddenReplyCount: 0,
    replies: [],
  };
}

function isReactionPending(commentId) {
  return reactionPendingIds.value.has(commentId);
}

/**
 * 신고 단추를 보일 것인가.
 *
 * 로그인하지 않았으면 안 보인다. 눌러 봐야 로그인 화면으로 튕기는데, 신고는
 * 그렇게까지 해서 하라고 떠밀 일이 아니다. 삭제된 댓글과 내 댓글도 뺀다.
 */
function canReport(comment) {
  return isSignedIn.value && !comment.deleted && !comment.mine;
}

/**
 * 이미 신고한 댓글인가.
 *
 * 단추를 감추지 않고 "신고됨" 으로 바꾼다. 감추면 내가 신고했었는지를 알 수 없어서,
 * 같은 댓글을 볼 때마다 다시 신고해야 하나 망설이게 된다.
 */
function alreadyReported(comment) {
  return Boolean(comment.reportedByMe);
}

/** 고치거나 지울 수 있는가. 막는 일은 서버가 한다 — 여기는 헛걸음을 줄이는 표시다 */
function canManage(comment) {
  return isSignedIn.value && !comment.deleted && comment.mine;
}

function startEdit(comment) {
  editError.value = '';
  deletingId.value = null;
  editingId.value = comment.id;
  editDraft.value = comment.content;
}

function cancelEdit() {
  editingId.value = null;
  editDraft.value = '';
  editError.value = '';
}

async function saveEdit(comment) {
  const content = editDraft.value.trim();

  if (!content || editPending.value) {
    return;
  }

  editPending.value = true;
  editError.value = '';

  try {
    await updatePostComment(comment.id, content);

    // 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
    comment.content = content;
    comment.edited = true;
    cancelEdit();
  } catch (error) {
    editError.value = error.message ?? '고치지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
  } finally {
    editPending.value = false;
  }
}

function askDelete(comment) {
  cancelEdit();
  deletingId.value = comment.id;
}

function cancelDelete() {
  deletingId.value = null;
}

/**
 * 지운 뒤 화면을 서버와 같은 모습으로 맞춘다.
 *
 * 서버는 지운 댓글을 무조건 감추지 않는다. 살아 있는 답글이 달린 댓글만 "삭제된
 * 댓글입니다" 로 자리를 남기고, 그 외에는 목록에서 빠진다. 답글은 언제나 빠진다.
 *
 * 여기서 서버와 다르게 그리면, 새로고침하는 순간 화면이 달라져서 지운 것이
 * 되살아난 것처럼 보인다.
 *
 * @param parent 답글이면 그 답글이 달린 댓글. 최상위 댓글이면 null
 */
async function confirmDelete(target, parent = null) {
  if (deletePending.value) {
    return;
  }

  deletePending.value = true;

  try {
    await deletePostComment(target.id);

    if (parent) {
      parent.replies = parent.replies.filter((reply) => reply.id !== target.id);
    } else if (target.replies.length > 0) {
      target.deleted = true;
      target.content = '';
      target.author = '';
      target.mine = false;
      // 내가 지운 것이다. 운영자가 가린 것으로 보이면 안 된다
      target.hiddenByAdmin = false;
    } else {
      items.value = items.value.filter((comment) => comment.id !== target.id);
    }

    total.value = Math.max(0, total.value - 1);
    deletingId.value = null;
  } catch (error) {
    reactionError.value = error.message ?? '지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
    deletingId.value = null;
  } finally {
    deletePending.value = false;
  }
}

function openReport(comment) {
  reportError.value = '';
  reportDone.value = '';
  reportTarget.value = comment;
}

function closeReport() {
  reportTarget.value = null;
  reportError.value = '';
}

async function submitReport({ reason, detail }) {
  if (reportPending.value || reportTarget.value === null) {
    return;
  }

  reportPending.value = true;
  reportError.value = '';

  try {
    await reportComment(reportTarget.value.id, { reason, detail });

    // 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
    reportTarget.value.reportedByMe = true;
    reportTarget.value = null;
    // 신고 수는 화면에 안 보인다. 보이면 그 자체로 낙인이 되고,
    // 몰려서 신고하면 숫자가 오르는 것이 보여 재미가 붙는다
    reportDone.value = '신고를 접수했습니다. 확인 뒤 처리하겠습니다.';
  } catch (error) {
    // 이미 신고했거나 내 댓글인 경우 서버가 이유를 준다. 그대로 보여 준다
    reportError.value = error.message ?? '신고하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
  } finally {
    reportPending.value = false;
  }
}

async function reactToComment(comment, type) {
  if (!isSignedIn.value) {
    goToLogin();
    return;
  }

  if (isReactionPending(comment.id)) {
    return;
  }

  reactionPendingIds.value = new Set([...reactionPendingIds.value, comment.id]);
  reactionError.value = '';

  try {
    const active = type === 'LIKE' ? comment.likedByMe : comment.dislikedByMe;
    const reaction = active
      ? await removeCommentReaction(comment.id, type)
      : await setCommentReaction(comment.id, type);

    comment.likeCount = Number(reaction?.likeCount ?? 0);
    comment.dislikeCount = Number(reaction?.dislikeCount ?? 0);
    comment.likedByMe = Boolean(reaction?.likedByMe);
    comment.dislikedByMe = Boolean(reaction?.dislikedByMe);
  } catch (error) {
    reactionError.value = error?.message ?? '댓글 반응을 저장하지 못했습니다.';
  } finally {
    const pendingIds = new Set(reactionPendingIds.value);
    pendingIds.delete(comment.id);
    reactionPendingIds.value = pendingIds;
  }
}

async function loadMore() {
  if (loading.value || !hasNext.value || nextCursor.value === null) {
    return;
  }

  loading.value = true;
  const requestedPostId = String(props.postId);
  const requestedCursor = nextCursor.value;

  try {
    const nextPage = await getPostComments(props.postId, {
      cursor: requestedCursor,
    });

    if (String(props.postId) !== requestedPostId || nextCursor.value !== requestedCursor) {
      return;
    }

    items.value = [...items.value, ...nextPage.items];
    total.value = nextPage.total;
    nextCursor.value = nextPage.nextCursor;
    hasNext.value = nextPage.hasNext;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function resetComments(comments) {
  total.value = comments.total ?? 0;
  items.value = [...(comments.items ?? [])];
  nextCursor.value = comments.nextCursor ?? null;
  hasNext.value = Boolean(comments.hasNext);
  loading.value = false;
  replyTargetId.value = null;
}

function observeSentinel() {
  observer?.disconnect();

  if (sentinel.value) {
    observer?.observe(sentinel.value);
  }
}

onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      loadMore();
    }
  });

  observeSentinel();
});

onBeforeUnmount(() => {
  observer?.disconnect();
});

watch(
  () => props.comments,
  (comments) => {
    resetComments(comments);
    window.requestAnimationFrame(observeSentinel);
  },
  { immediate: true },
);
</script>

<template>
  <section class="comments" aria-labelledby="comments-title" :aria-busy="initialLoading || loading">
    <div class="comments__header">
      <h2 id="comments-title" class="comments__title">댓글</h2>
      <span v-if="initialLoading" class="ui-skeleton comments__count-skeleton" aria-hidden="true"></span>
      <span v-else class="comments__count">{{ total }}</span>
      <span class="comments__note">이 글에 남겨진 이야기</span>
    </div>

    <div v-if="initialLoading" class="comments__initial-skeleton" aria-hidden="true">
      <div class="comments__form-skeleton">
        <span class="ui-skeleton ui-skeleton--circle"></span>
        <span class="ui-skeleton"></span>
      </div>
      <div v-for="index in 2" :key="index" class="comments__row-skeleton">
        <span class="ui-skeleton ui-skeleton--circle"></span>
        <div>
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
        </div>
      </div>
    </div>

    <!-- 로그인하지 않았으면 쓰는 칸 대신 로그인으로 보낸다.
         빈 칸을 보여 주고 누른 뒤에 막으면 쓴 글이 날아간다 -->
    <div v-else-if="!isSignedIn" ref="signinBox" class="comment-signin">
      <p class="comment-signin__text">댓글을 남기려면 로그인이 필요합니다.</p>
      <button class="post-button post-button--accent" type="button" @click="goToLogin">
        로그인하고 댓글 쓰기
      </button>
    </div>

    <form v-else class="comment-form" @submit.prevent="submitComment">
      <div class="comment-form__body">
        <!-- 프사가 있으면 그림, 없거나 못 받아오면 첫 글자.
             카카오·네이버는 프사가 선택 동의라 주소가 안 오는 경우도 정상이다 -->
        <img
          v-if="showAvatarImage"
          class="comment-avatar comment-avatar--photo"
          :src="member.profileImageUrl"
          alt=""
          referrerpolicy="no-referrer"
          @error="avatarImageFailed = true"
        />
        <span v-else class="comment-avatar">{{ avatarInitial(member?.nickname) }}</span>
        <div class="comment-form__content">
          <textarea
            ref="commentInput"
            v-model="draft"
            class="comment-form__input"
            :placeholder="commentPlaceholder"
            :maxlength="commentMaxLength"
            rows="2"
          ></textarea>
        </div>
      </div>
      <div class="comment-form__footer">
        <span class="comment-form__note">
          {{ writingAs }}
          <button class="comment-form__signout" type="button" @click="signOut">로그아웃</button>
        </span>
        <div class="comment-form__actions">
          <span class="comment-form__counter">{{ draft.length }} / {{ commentMaxLength }}</span>
          <button
            class="post-button post-button--accent"
            type="submit"
            :disabled="submitting"
          >
            {{ submitting ? '등록 중' : '등록' }}
          </button>
        </div>
      </div>
      <p v-if="formError" class="comment-form__error" role="alert">{{ formError }}</p>
    </form>

    <p v-if="!initialLoading && reactionError" class="comment-reaction__error" role="alert">
      {{ reactionError }}
    </p>

    <ul v-if="!initialLoading" class="comment-list">
      <li
        v-for="comment in items"
        :id="`comment-${comment.id}`"
        :key="comment.id"
        class="comment"
      >
        <div class="comment__main">
          <span
            class="comment-avatar"
            :class="{ 'comment-avatar--deleted': comment.deleted }"
          >{{ comment.deleted ? '' : avatarInitial(comment.author) }}</span>

          <div v-if="comment.deleted" class="comment__content">
            <!--
              가린 주체에 따라 말이 달라야 한다. 운영자가 가린 것을 "삭제된 댓글" 이라고
              하면 글쓴이가 자기가 지운 줄 알고, 반대면 없는 일을 만든다
            -->
            <p class="comment__text comment__text--deleted">
              {{ comment.hiddenByAdmin ? '운영자가 가린 댓글입니다.' : '삭제된 댓글입니다.' }}
            </p>
          </div>

          <div v-else class="comment__content">
            <div class="comment__meta">
              <span class="comment__author">{{ comment.author }}</span>
              <span v-if="comment.isAuthor" class="comment__badge">작성자</span>
              <time class="comment__time">{{ comment.createdAt }}</time>
              <!--
                말이 오간 뒤에 조용히 바뀌면 뒤에 달린 답글이 엉뚱한 말에 답한 것처럼
                보인다. 고쳤다는 사실만 남기고 무엇을 고쳤는지는 남기지 않는다
              -->
              <span v-if="comment.edited" class="comment__edited">수정됨</span>
            </div>
            <form
              v-if="editingId === comment.id"
              class="comment-edit"
              @submit.prevent="saveEdit(comment)"
            >
              <textarea
                v-model="editDraft"
                class="comment-edit__input"
                rows="3"
                :maxlength="commentMaxLength"
                aria-label="댓글 고치기"
              ></textarea>
              <p v-if="editError" class="comment-edit__error" role="alert">{{ editError }}</p>
              <div class="comment-edit__actions">
                <button class="comment__action" type="button" @click="cancelEdit">취소</button>
                <button
                  class="comment__action comment__action--strong"
                  type="submit"
                  :disabled="editPending || editDraft.trim() === ''"
                >{{ editPending ? '저장 중…' : '저장' }}</button>
              </div>
            </form>

            <p v-else class="comment__text">{{ comment.content }}</p>

            <div class="comment__actions">
              <button
                class="comment__action comment__action--icon"
                :class="{ 'comment__action--active': comment.likedByMe }"
                type="button"
                :disabled="isReactionPending(comment.id)"
                :aria-pressed="comment.likedByMe"
                aria-label="좋아요"
                title="좋아요"
                @click="reactToComment(comment, 'LIKE')"
              >
                <CommentActionIcon name="like" />
                {{ comment.likeCount }}
              </button>
              <button
                class="comment__action comment__action--icon"
                :class="{ 'comment__action--active': comment.dislikedByMe }"
                type="button"
                :disabled="isReactionPending(comment.id)"
                :aria-pressed="comment.dislikedByMe"
                aria-label="싫어요"
                title="싫어요"
                @click="reactToComment(comment, 'DISLIKE')"
              >
                <CommentActionIcon name="dislike" />
                {{ comment.dislikeCount }}
              </button>
              <button
                class="comment__action comment__action--strong"
                type="button"
                @click="toggleReply(comment.id)"
              >
                답글
              </button>
              <button
                v-if="canReport(comment)"
                class="comment__action comment__action--icon comment__action--report"
                :class="{ 'comment__action--reported': alreadyReported(comment) }"
                type="button"
                :disabled="alreadyReported(comment)"
                :aria-label="alreadyReported(comment) ? '이미 신고한 댓글' : '신고'"
                :title="alreadyReported(comment) ? '이미 신고한 댓글입니다' : '신고'"
                @click="openReport(comment)"
              >
                <CommentActionIcon name="report" />
                {{ alreadyReported(comment) ? '신고됨' : '신고' }}
              </button>

              <template v-if="canManage(comment)">
                <button
                  v-if="editingId !== comment.id"
                  class="comment__action"
                  type="button"
                  @click="startEdit(comment)"
                >수정</button>

                <!-- 지우기는 되돌릴 수 없다. 좋아요 옆에 붙어 있어서 한 번 더 묻는다 -->
                <template v-if="deletingId === comment.id">
                  <span class="comment__confirm">정말 지울까요?</span>
                  <button
                    class="comment__action comment__action--danger"
                    type="button"
                    :disabled="deletePending"
                    @click="confirmDelete(comment)"
                  >{{ deletePending ? '지우는 중…' : '지우기' }}</button>
                  <button class="comment__action" type="button" @click="cancelDelete">취소</button>
                </template>
                <button v-else class="comment__action" type="button" @click="askDelete(comment)">
                  삭제
                </button>
              </template>
            </div>

            <button v-if="comment.hiddenReplyCount" class="comment__more" type="button">
              답글 {{ comment.hiddenReplyCount }}개 더 보기 ⌄
            </button>
          </div>
        </div>

        <div v-if="comment.replies.length || replyTargetId === comment.id" class="comment__replies">
          <div
            v-for="reply in comment.replies"
            :id="`comment-${reply.id}`"
            :key="reply.id"
            class="reply"
          >
            <span
              class="comment-avatar"
              :class="{ 'comment-avatar--author': reply.isAuthor }"
            >
              {{ avatarInitial(reply.author) }}
            </span>
            <div class="comment__content">
              <div class="comment__meta">
                <span class="comment__author">{{ reply.author }}</span>
                <span v-if="reply.isAuthor" class="comment__badge">작성자</span>
                <time class="comment__time">{{ reply.createdAt }}</time>
                <span v-if="reply.edited" class="comment__edited">수정됨</span>
              </div>
              <form
                v-if="editingId === reply.id"
                class="comment-edit"
                @submit.prevent="saveEdit(reply)"
              >
                <textarea
                  v-model="editDraft"
                  class="comment-edit__input"
                  rows="2"
                  :maxlength="commentMaxLength"
                  aria-label="답글 고치기"
                ></textarea>
                <p v-if="editError" class="comment-edit__error" role="alert">{{ editError }}</p>
                <div class="comment-edit__actions">
                  <button class="comment__action" type="button" @click="cancelEdit">취소</button>
                  <button
                    class="comment__action comment__action--strong"
                    type="submit"
                    :disabled="editPending || editDraft.trim() === ''"
                  >{{ editPending ? '저장 중…' : '저장' }}</button>
                </div>
              </form>

              <p v-else class="comment__text">{{ reply.content }}</p>
              <div class="comment__actions">
                <button
                  class="comment__action comment__action--icon"
                  :class="{ 'comment__action--active': reply.likedByMe }"
                  type="button"
                  :disabled="isReactionPending(reply.id)"
                  :aria-pressed="reply.likedByMe"
                  aria-label="좋아요"
                  title="좋아요"
                  @click="reactToComment(reply, 'LIKE')"
                >
                  <CommentActionIcon name="like" />
                  {{ reply.likeCount }}
                </button>
                <button
                  class="comment__action comment__action--icon"
                  :class="{ 'comment__action--active': reply.dislikedByMe }"
                  type="button"
                  :disabled="isReactionPending(reply.id)"
                  :aria-pressed="reply.dislikedByMe"
                  aria-label="싫어요"
                  title="싫어요"
                  @click="reactToComment(reply, 'DISLIKE')"
                >
                  <CommentActionIcon name="dislike" />
                  {{ reply.dislikeCount }}
                </button>
                <button
                  v-if="canReport(reply)"
                  class="comment__action comment__action--icon comment__action--report"
                  :class="{ 'comment__action--reported': alreadyReported(reply) }"
                  type="button"
                  :disabled="alreadyReported(reply)"
                  :aria-label="alreadyReported(reply) ? '이미 신고한 답글' : '신고'"
                  :title="alreadyReported(reply) ? '이미 신고한 답글입니다' : '신고'"
                  @click="openReport(reply)"
                >
                  <CommentActionIcon name="report" />
                  {{ alreadyReported(reply) ? '신고됨' : '신고' }}
                </button>

                <template v-if="canManage(reply)">
                  <button
                    v-if="editingId !== reply.id"
                    class="comment__action"
                    type="button"
                    @click="startEdit(reply)"
                  >수정</button>

                  <template v-if="deletingId === reply.id">
                    <span class="comment__confirm">정말 지울까요?</span>
                    <button
                      class="comment__action comment__action--danger"
                      type="button"
                      :disabled="deletePending"
                      @click="confirmDelete(reply, comment)"
                    >{{ deletePending ? '지우는 중…' : '지우기' }}</button>
                    <button class="comment__action" type="button" @click="cancelDelete">취소</button>
                  </template>
                  <button v-else class="comment__action" type="button" @click="askDelete(reply)">
                    삭제
                  </button>
                </template>
              </div>
            </div>
          </div>

          <form
            v-if="replyTargetId === comment.id"
            class="reply-form"
            @submit.prevent="submitReply(comment.id)"
          >
            <p class="reply-form__target">{{ comment.author }} 님에게 답글 쓰는 중</p>
            <textarea
              ref="replyInput"
              v-model="replyDraft"
              class="reply-form__input"
              :maxlength="commentMaxLength"
              rows="2"
            ></textarea>
            <div class="reply-form__footer">
              <span class="comment-form__counter">
                {{ replyDraft.length }} / {{ commentMaxLength }}
              </span>
              <div class="comment-form__actions">
                <button class="post-button" type="button" @click="toggleReply(comment.id)">
                  취소
                </button>
                <button
                  class="post-button post-button--accent"
                  type="submit"
                  :disabled="replySubmitting"
                >
                  {{ replySubmitting ? '등록 중' : '답글 등록' }}
                </button>
              </div>
            </div>
            <p v-if="replyError" class="comment-form__error" role="alert">{{ replyError }}</p>
          </form>
        </div>
      </li>
    </ul>

    <!-- 스크롤이 닿으면 자동으로 불러오고, 버튼은 키보드 사용자를 위한 대체 수단 -->
    <div v-if="!initialLoading" ref="sentinel" class="comments__sentinel">
      <div v-if="loading" class="comments__more-skeleton" aria-hidden="true">
        <span class="ui-skeleton ui-skeleton--circle"></span>
        <span class="ui-skeleton"></span>
      </div>
      <button v-else-if="hasNext" class="comments__more" type="button" @click="loadMore">
        댓글 더 보기
      </button>
      <span v-else>{{ items.length === 0 ? '아직 댓글이 없습니다' : '마지막 댓글입니다' }}</span>
    </div>

    <div v-if="showBottomAd" class="post-ad">
      <span class="post-ad__label">광고 · AD</span>
      <div class="post-ad__slot">{{ comments.bottomAd.label }}</div>
    </div>

    <!-- 접수 결과. 창은 닫히므로 남는 자리가 있어야 무엇이 됐는지 알 수 있다 -->
    <p v-if="reportDone" class="comments__report-done" role="status">{{ reportDone }}</p>

    <CommentReportDialog
      :open="reportTarget !== null"
      :target="reportTarget?.author ?? ''"
      :pending="reportPending"
      :error="reportError"
      @submit="submitReport"
      @close="closeReport"
    />
  </section>
</template>
