<script setup>
// 마크다운 :::tip / :::warning / :::note 에 대응하는 고정 종류
const calloutLabels = {
  tip: '팁',
  warning: '주의',
  note: '참고',
};

const showInlineAds = false;
const showTags = false;
const showSecondaryActions = false;

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
        <button class="post-button" type="button">공유</button>
      </div>
    </header>

    <figure v-if="post.coverImageUrl" class="post-article__cover">
      <img :src="post.coverImageUrl" :alt="post.coverImageAlt" />
    </figure>

    <div class="post-body">
      <template v-for="(block, index) in body" :key="index">
        <p v-if="block.type === 'paragraph'" class="post-body__paragraph">{{ block.text }}</p>

        <h2 v-else-if="block.type === 'heading'" :id="block.id" class="post-body__heading">
          {{ block.text }}
        </h2>

        <blockquote v-else-if="block.type === 'quote'" class="post-body__quote">
          {{ block.text }}
        </blockquote>

        <div
          v-else-if="block.type === 'callout'"
          class="post-callout"
          :class="`post-callout--${block.variant}`"
        >
          <span class="post-callout__label">{{ calloutLabels[block.variant] }}</span>
          <p class="post-callout__text">{{ block.text }}</p>
        </div>

        <pre v-else-if="block.type === 'code'" class="post-body__code"><span
          class="post-body__code-caption">{{ block.caption }}</span><span
          v-for="line in block.lines"
          :key="line.accent"
          class="post-body__code-line"><span class="post-body__code-accent">{{ line.accent }}</span>{{ line.text }}</span><span
          class="post-body__code-note">{{ block.note }}</span></pre>

        <div v-else-if="block.type === 'ad' && showInlineAds" class="post-ad">
          <span class="post-ad__label">광고 · AD</span>
          <div class="post-ad__slot">{{ block.label }}</div>
        </div>
      </template>
    </div>

    <div v-if="showTags && post.tags?.length" class="post-tags">
      <span v-for="tag in post.tags" :key="tag" class="post-tags__item">#{{ tag }}</span>
    </div>

    <div class="post-reactions">
      <button class="post-reactions__button" type="button">댓글 {{ post.commentCount }}</button>
      <div class="post-reactions__spacer"></div>
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
