<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createPostComment,
  getPostComments,
  removeCommentReaction,
  setCommentReaction,
} from '../api/postApi';
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
const replyDraft = ref('');
const formError = ref('');
const replyError = ref('');
const reactionError = ref('');
const showReportAction = false;
const showBottomAd = false;

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
}

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
    hiddenReplyCount: 0,
    replies: [],
  };
}

function isReactionPending(commentId) {
  return reactionPendingIds.value.has(commentId);
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
  <section class="comments" aria-labelledby="comments-title">
    <div class="comments__header">
      <h2 id="comments-title" class="comments__title">댓글</h2>
      <span class="comments__count">{{ total }}</span>
      <span class="comments__note">이 글에 남겨진 이야기</span>
    </div>

    <!-- 로그인하지 않았으면 쓰는 칸 대신 로그인으로 보낸다.
         빈 칸을 보여 주고 누른 뒤에 막으면 쓴 글이 날아간다 -->
    <div v-if="!isSignedIn" class="comment-signin">
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

    <p v-if="reactionError" class="comment-reaction__error" role="alert">
      {{ reactionError }}
    </p>

    <ul class="comment-list">
      <li v-for="comment in items" :key="comment.id" class="comment">
        <div class="comment__main">
          <span
            class="comment-avatar"
            :class="{ 'comment-avatar--deleted': comment.deleted }"
          >{{ comment.deleted ? '' : avatarInitial(comment.author) }}</span>

          <div v-if="comment.deleted" class="comment__content">
            <p class="comment__text comment__text--deleted">삭제된 댓글입니다.</p>
          </div>

          <div v-else class="comment__content">
            <div class="comment__meta">
              <span class="comment__author">{{ comment.author }}</span>
              <span v-if="comment.isAuthor" class="comment__badge">작성자</span>
              <time class="comment__time">{{ comment.createdAt }}</time>
            </div>
            <p class="comment__text">{{ comment.content }}</p>

            <div class="comment__actions">
              <button
                class="comment__action"
                :class="{ 'comment__action--active': comment.likedByMe }"
                type="button"
                :disabled="isReactionPending(comment.id)"
                :aria-pressed="comment.likedByMe"
                @click="reactToComment(comment, 'LIKE')"
              >
                좋아요 {{ comment.likeCount }}
              </button>
              <button
                class="comment__action"
                :class="{ 'comment__action--active': comment.dislikedByMe }"
                type="button"
                :disabled="isReactionPending(comment.id)"
                :aria-pressed="comment.dislikedByMe"
                @click="reactToComment(comment, 'DISLIKE')"
              >
                싫어요 {{ comment.dislikeCount }}
              </button>
              <button
                class="comment__action comment__action--strong"
                type="button"
                @click="toggleReply(comment.id)"
              >
                답글
              </button>
              <button v-if="showReportAction" class="comment__action" type="button">신고</button>
            </div>

            <button v-if="comment.hiddenReplyCount" class="comment__more" type="button">
              답글 {{ comment.hiddenReplyCount }}개 더 보기 ⌄
            </button>
          </div>
        </div>

        <div v-if="comment.replies.length || replyTargetId === comment.id" class="comment__replies">
          <div v-for="reply in comment.replies" :key="reply.id" class="reply">
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
              </div>
              <p class="comment__text">{{ reply.content }}</p>
              <div class="comment__actions">
                <button
                  class="comment__action"
                  :class="{ 'comment__action--active': reply.likedByMe }"
                  type="button"
                  :disabled="isReactionPending(reply.id)"
                  :aria-pressed="reply.likedByMe"
                  @click="reactToComment(reply, 'LIKE')"
                >
                  좋아요 {{ reply.likeCount }}
                </button>
                <button
                  class="comment__action"
                  :class="{ 'comment__action--active': reply.dislikedByMe }"
                  type="button"
                  :disabled="isReactionPending(reply.id)"
                  :aria-pressed="reply.dislikedByMe"
                  @click="reactToComment(reply, 'DISLIKE')"
                >
                  싫어요 {{ reply.dislikeCount }}
                </button>
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
    <div ref="sentinel" class="comments__sentinel">
      <span v-if="loading">댓글을 불러오는 중…</span>
      <button v-else-if="hasNext" class="comments__more" type="button" @click="loadMore">
        댓글 더 보기
      </button>
      <span v-else>{{ items.length === 0 ? '아직 댓글이 없습니다' : '마지막 댓글입니다' }}</span>
    </div>

    <div v-if="showBottomAd" class="post-ad">
      <span class="post-ad__label">광고 · AD</span>
      <div class="post-ad__slot">{{ comments.bottomAd.label }}</div>
    </div>
  </section>
</template>
