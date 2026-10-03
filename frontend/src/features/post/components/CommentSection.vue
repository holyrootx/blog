<script setup>
import { computed, ref, toRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CommentComposer from './CommentComposer.vue';
import CommentActions from './CommentActions.vue';
import CommentEditForm from './CommentEditForm.vue';
import CommentReportDialog from './CommentReportDialog.vue';
import { useCommentFeed } from '../composables/useCommentFeed';
import { useCommentComposer } from '../composables/useCommentComposer';
import { useCommentManagement } from '../composables/useCommentManagement';
import { useCommentFeedback } from '../composables/useCommentFeedback';
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
const { member, isSignedIn } = useMemberAuth();
const composer = ref(null);
const replyInput = ref(null);
const signinBox = ref(null);
const showBottomAd = false;
const commentPlaceholder = computed(() => props.comments.placeholder ?? '');
const commentMaxLength = computed(() => props.comments.maxLength ?? 1000);
const { total, items, hasNext, loading, sentinel, loadMore } = useCommentFeed({
  postId: toRef(props, 'postId'), comments: toRef(props, 'comments'),
});
const {
  submitting,
  replySubmitting,
  draft,
  replyTargetId,
  replyDraft,
  formError,
  replyError,
  toggleReply,
  submitComment,
  submitReply,
} = useCommentComposer({ postId: toRef(props, 'postId'), items, total, member, replyInput });
const {
  reactionError,
  reportTarget,
  reportPending,
  reportError,
  reportDone,
  isReactionPending,
  openReport,
  closeReport,
  submitReport,
  reactToComment,
} = useCommentFeedback({ isSignedIn, goToLogin });
const {
  editingId,
  editDraft,
  editPending,
  editError,
  deletingId,
  deletePending,
  deleteError,
  startEdit,
  cancelEdit,
  saveEdit,
  askDelete,
  cancelDelete,
  confirmDelete,
} = useCommentManagement({ items, total });

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

function focusCommentInput() {
  const target = composer.value?.$el ?? signinBox.value;
  target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  composer.value?.focus({ preventScroll: true });
}
defineExpose({ focusCommentInput });
watch(() => props.comments, () => { replyTargetId.value = null; });
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
      <button class="ui-button ui-button--accent" type="button" @click="goToLogin">
        로그인하고 댓글 쓰기
      </button>
    </div>

    <CommentComposer
      v-else
      ref="composer"
      v-model="draft"
      :member="member"
      :placeholder="commentPlaceholder"
      :max-length="commentMaxLength"
      :pending="submitting"
      :error="formError"
      @submit="submitComment"
      @signout="signOut"
    />

    <p v-if="!initialLoading && reactionError" class="comment-reaction__error" role="alert">
      {{ reactionError }}
    </p>
    <p v-if="!initialLoading && deleteError" class="comment-reaction__error" role="alert">
      {{ deleteError }}
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
            <CommentEditForm
              v-if="editingId === comment.id"
              v-model="editDraft"
              :max-length="commentMaxLength"
              :rows="3"
              label="댓글 고치기"
              :pending="editPending"
              :error="editError"
              @save="saveEdit(comment)"
              @cancel="cancelEdit"
            />

            <p v-else class="comment__text">{{ comment.content }}</p>

            <CommentActions
              :comment="comment"
              :signed-in="isSignedIn"
              :editing="editingId === comment.id"
              :deleting="deletingId === comment.id"
              :reaction-pending="isReactionPending(comment.id)"
              :delete-pending="deletePending"
              @react="reactToComment(comment, $event)"
              @reply="toggleReply(comment.id)"
              @report="openReport(comment)"
              @edit="startEdit(comment)"
              @ask-delete="askDelete(comment)"
              @confirm-delete="confirmDelete(comment)"
              @cancel-delete="cancelDelete"
            />

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
              <CommentEditForm
                v-if="editingId === reply.id"
                v-model="editDraft"
                :max-length="commentMaxLength"
                :rows="2"
                label="답글 고치기"
                :pending="editPending"
                :error="editError"
                @save="saveEdit(reply)"
                @cancel="cancelEdit"
              />

              <p v-else class="comment__text">{{ reply.content }}</p>
              <CommentActions
                reply
                :comment="reply"
                :signed-in="isSignedIn"
                :editing="editingId === reply.id"
                :deleting="deletingId === reply.id"
                :reaction-pending="isReactionPending(reply.id)"
                :delete-pending="deletePending"
                @react="reactToComment(reply, $event)"
                @report="openReport(reply)"
                @edit="startEdit(reply)"
                @ask-delete="askDelete(reply)"
                @confirm-delete="confirmDelete(reply, comment)"
                @cancel-delete="cancelDelete"
              />
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
                <button class="ui-button" type="button" @click="toggleReply(comment.id)">
                  취소
                </button>
                <button
                  class="ui-button ui-button--accent"
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
