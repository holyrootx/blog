<script setup>
import { onBeforeUnmount, ref } from 'vue';

// 마크다운 :::tip / :::warning / :::note 에 대응하는 고정 종류
import PostBody from './PostBody.vue';

const showInlineAds = false;
const showTags = false;
const showSecondaryActions = false;

// 댓글 칸은 이 컴포넌트 밖에 있다. 직접 건드리지 않고 부모에게 넘긴다
const emit = defineEmits(['focus-comments']);

defineProps({
  post: {
    type: Object,
    required: true,
  },
  author: {
    type: Object,
    required: true,
  },
  body: {
    type: Array,
    required: true,
  },
  adjacentPosts: {
    type: Object,
    required: true,
  },
});

/**
 * 공유 — 지금 글의 주소를 클립보드에 넣는다.
 *
 * <p>눌러도 화면이 바뀌지 않는 일이라, 됐는지 안 됐는지 말해 주지 않으면 사람은
 * 안 된 줄 알고 또 누른다. 그래서 단추의 말이 잠깐 바뀐다.</p>
 */
const SHARE_NOTICE_MS = 2000;

const shareNotice = ref('');

let noticeTimer = null;

async function share() {
  clearTimeout(noticeTimer);

  try {
    // 클립보드는 https 와 localhost 에서만 열린다. 막히면 예외가 난다
    await navigator.clipboard.writeText(window.location.href);
    shareNotice.value = '복사했어요';
  } catch {
    // 주소창의 주소를 직접 복사하면 된다. 실패를 조용히 삼키면 눌러도 아무 일이 없다.
    // 글자 수를 성공 문구와 맞춘다 — 길이가 다르면 단추 폭이 그때만 튄다
    shareNotice.value = '복사 안 됨';
  }

  noticeTimer = setTimeout(() => {
    shareNotice.value = '';
  }, SHARE_NOTICE_MS);
}

onBeforeUnmount(() => clearTimeout(noticeTimer));
</script>

<template>
  <article class="post-article">
    <header class="post-article__header">
      <div class="post-article__meta">
        <span class="post-article__category">{{ post.category }}</span>
        <time>{{ post.publishedAt }}</time>
        <span class="post-article__dot">·</span>
        <span>조회 {{ post.views }}</span>
      </div>

      <h1 class="post-article__title">{{ post.title }}</h1>
      <p class="post-article__excerpt">{{ post.excerpt }}</p>

      <div class="post-author">
        <img
          v-if="author.avatarImageUrl"
          class="post-author__avatar"
          :src="author.avatarImageUrl"
          :alt="author.name"
        />
        <div class="post-author__info">
          <div class="post-author__name">{{ author.name }}</div>
          <div class="post-author__desc">{{ author.job }}</div>
        </div>
      </div>
    </header>

    <figure v-if="post.coverImageUrl" class="post-article__cover">
      <img :src="post.coverImageUrl" :alt="post.coverImageAlt" />
    </figure>

    <PostBody :body="body" :show-inline-ads="showInlineAds" />

    <div v-if="showTags && post.tags?.length" class="post-tags">
      <span v-for="tag in post.tags" :key="tag" class="post-tags__item">#{{ tag }}</span>
    </div>

    <div class="post-reactions">
      <button
        class="post-reactions__button"
        type="button"
        @click="emit('focus-comments')"
      >댓글 {{ post.commentCount }}</button>
      <div class="post-reactions__spacer"></div>
      <!--
        공유는 글 맨 위가 아니라 여기다. 남에게 보내고 싶어지는 것은 읽기 전이 아니라
        다 읽은 뒤라, 스크롤을 거슬러 올라가게 만들 이유가 없다.

        화면 낭독기도 알아야 하므로 aria-live 로 알린다. 색과 글자만 바뀌면 안 들린다
      -->
      <button class="post-button" type="button" @click="share">
        <span aria-live="polite">{{ shareNotice || '공유' }}</span>
      </button>
      <span v-if="showSecondaryActions" class="post-reactions__links">저장 · 링크 복사</span>
    </div>

    <nav v-if="adjacentPosts.prev || adjacentPosts.next" class="post-nav" aria-label="이전 글, 다음 글">
      <RouterLink
        v-if="adjacentPosts.prev"
        class="post-nav__item"
        :to="{ name: 'post-detail', params: { id: adjacentPosts.prev.id } }"
      >
        <span class="post-nav__label">← 이전 글</span>
        <span class="post-nav__title">{{ adjacentPosts.prev.title }}</span>
      </RouterLink>
      <span v-else class="post-nav__item post-nav__item--disabled">
        <span class="post-nav__label">← 이전 글</span>
        <span class="post-nav__title">이전 글이 없습니다</span>
      </span>

      <RouterLink
        v-if="adjacentPosts.next"
        class="post-nav__item post-nav__item--next"
        :to="{ name: 'post-detail', params: { id: adjacentPosts.next.id } }"
      >
        <span class="post-nav__label">다음 글 →</span>
        <span class="post-nav__title">{{ adjacentPosts.next.title }}</span>
      </RouterLink>
      <span v-else class="post-nav__item post-nav__item--next post-nav__item--disabled">
        <span class="post-nav__label">다음 글 →</span>
        <span class="post-nav__title">다음 글이 없습니다</span>
      </span>
    </nav>
  </article>
</template>
