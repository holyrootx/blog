<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps({
  comments: {
    type: Object,
    required: true,
  },
  nextPage: {
    type: Object,
    default: null,
  },
});

const items = ref([...props.comments.items]);
const hasNext = ref(props.comments.hasNext);
const loading = ref(false);
const nickname = ref('');
const draft = ref('');
const replyTargetId = ref(null);
const replyNickname = ref('');
const replyDraft = ref('');
const showReportAction = false;
const showBottomAd = false;

const sentinel = ref(null);
let observer = null;

function toggleReply(commentId) {
  replyTargetId.value = replyTargetId.value === commentId ? null : commentId;
  replyNickname.value = '';
  replyDraft.value = '';
}

// 커서 기반 무한 스크롤. 실제로는 nextCursor를 API에 넘겨 다음 묶음을 받아온다.
function loadMore() {
  if (loading.value || !hasNext.value || !props.nextPage) {
    return;
  }

  loading.value = true;

  window.setTimeout(() => {
    items.value = [...items.value, ...props.nextPage.items];
    hasNext.value = props.nextPage.hasNext;
    loading.value = false;
  }, 400);
}

onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      loadMore();
    }
  });

  if (sentinel.value) {
    observer.observe(sentinel.value);
  }
});

onBeforeUnmount(() => {
  observer?.disconnect();
});
</script>

<template>
  <section class="comments" aria-labelledby="comments-title">
    <div class="comments__header">
      <h2 id="comments-title" class="comments__title">댓글</h2>
      <span class="comments__count">{{ comments.total }}</span>
      <span class="comments__note">이 글에 남겨진 이야기</span>
    </div>

    <form class="comment-form" @submit.prevent>
      <div class="comment-form__body">
        <span class="comment-avatar"></span>
        <div class="comment-form__content">
          <input
            v-model.trim="nickname"
            class="comment-form__nickname"
            type="text"
            placeholder="닉네임"
            maxlength="20"
            autocomplete="nickname"
          />
          <textarea
            v-model="draft"
            class="comment-form__input"
            :placeholder="comments.placeholder"
            :maxlength="comments.maxLength"
            rows="2"
          ></textarea>
        </div>
      </div>
      <div class="comment-form__footer">
        <span class="comment-form__note">닉네임만 입력하면 댓글을 남길 수 있습니다.</span>
        <div class="comment-form__actions">
          <span class="comment-form__counter">{{ draft.length }} / {{ comments.maxLength }}</span>
          <button class="post-button post-button--accent" type="submit">등록</button>
        </div>
      </div>
    </form>

    <ul class="comment-list">
      <li v-for="comment in items" :key="comment.id" class="comment">
        <div class="comment__main">
          <span
            class="comment-avatar"
            :class="{
              'comment-avatar--secret': comment.isSecret,
              'comment-avatar--deleted': comment.deleted,
            }"
          ></span>

          <div v-if="comment.deleted" class="comment__content">
            <p class="comment__text comment__text--deleted">삭제된 댓글입니다.</p>
          </div>

          <div v-else class="comment__content">
            <div class="comment__meta">
              <span class="comment__author">{{ comment.author }}</span>
              <time class="comment__time">{{ comment.createdAt }}</time>
            </div>
            <p v-if="comment.isSecret" class="comment__text comment__text--secret">
              작성자와 본인만 볼 수 있는 댓글입니다.
            </p>
            <p v-else class="comment__text">{{ comment.content }}</p>

            <div v-if="!comment.isSecret" class="comment__actions">
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
            <span class="comment-avatar comment-avatar--author"></span>
            <div class="comment__content">
              <div class="comment__meta">
                <span class="comment__author">{{ reply.author }}</span>
                <span v-if="reply.isAuthor" class="comment__badge">작성자</span>
                <time class="comment__time">{{ reply.createdAt }}</time>
              </div>
              <p class="comment__text">{{ reply.content }}</p>
              <div class="comment__actions">
                <button class="comment__action comment__action--strong" type="button">답글</button>
              </div>
            </div>
          </div>

          <form v-if="replyTargetId === comment.id" class="reply-form" @submit.prevent>
            <p class="reply-form__target">{{ comment.author }} 님에게 답글 쓰는 중</p>
            <input
              v-model.trim="replyNickname"
              class="comment-form__nickname"
              type="text"
              placeholder="닉네임"
              maxlength="20"
              autocomplete="nickname"
            />
            <textarea
              v-model="replyDraft"
              class="reply-form__input"
              :maxlength="comments.maxLength"
              rows="2"
            ></textarea>
            <div class="reply-form__footer">
              <span class="comment-form__counter">
                {{ replyDraft.length }} / {{ comments.maxLength }}
              </span>
              <div class="comment-form__actions">
                <button class="post-button" type="button" @click="toggleReply(comment.id)">
                  취소
                </button>
                <button class="post-button post-button--accent" type="submit">답글 등록</button>
              </div>
            </div>
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
      <span v-else>마지막 댓글입니다</span>
    </div>

    <div v-if="showBottomAd" class="post-ad">
      <span class="post-ad__label">광고 · AD</span>
      <div class="post-ad__slot">{{ comments.bottomAd.label }}</div>
    </div>
  </section>
</template>
