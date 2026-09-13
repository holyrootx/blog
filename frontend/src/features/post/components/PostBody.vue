<script setup>
import PostInline from './PostInline.vue';
import PostCode from './PostCode.vue';

/**
 * 마크다운 블록을 그린다.
 *
 * 공개 화면(PostArticle)과 편집 화면 미리보기가 이 컴포넌트를 같이 쓴다.
 * 둘이 따로 그리면 "미리보기에선 이랬는데 발행하니 다르다"가 반드시 생긴다.
 */
defineProps({
  body: {
    type: Array,
    default: () => [],
  },
  // 광고는 공개 화면에서만
  showInlineAds: {
    type: Boolean,
    default: false,
  },
});

const calloutLabels = {
  tip: '팁',
  warning: '주의',
  note: '참고',
};
</script>

<template>
  <div class="post-body">
    <template v-for="(block, index) in body" :key="index">
      <p v-if="block.type === 'paragraph'" class="post-body__paragraph">
        <PostInline :tokens="block.inline" />
      </p>

      <!-- 헤딩 단계를 살린다. 전부 h2 로 뭉개면 #과 ###이 화면에서 같아진다 -->
      <component
        :is="`h${Math.min(block.level + 1, 6)}`"
        v-else-if="block.type === 'heading'"
        :id="block.id"
        class="post-body__heading"
        :class="`post-body__heading--h${block.level}`"
      >
        <PostInline :tokens="block.inline" />
      </component>

      <blockquote v-else-if="block.type === 'quote'" class="post-body__quote">
        <PostInline :tokens="block.inline" />
      </blockquote>

      <div
        v-else-if="block.type === 'callout'"
        class="post-callout"
        :class="`post-callout--${block.variant}`"
      >
        <span class="post-callout__label">{{ calloutLabels[block.variant] }}</span>
        <p class="post-callout__text"><PostInline :tokens="block.inline" /></p>
      </div>

      <component
        :is="block.ordered ? 'ol' : 'ul'"
        v-else-if="block.type === 'list'"
        class="post-body__list"
        :class="block.ordered ? 'post-body__list--ordered' : 'post-body__list--bullet'"
      >
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <PostInline :tokens="item" />
        </li>
      </component>

      <hr v-else-if="block.type === 'divider'" class="post-body__divider" />

      <!-- 코드는 원문 그대로 보여준다. 안쪽을 마크다운으로 해석하지 않는다 -->
      <div v-else-if="block.type === 'code'" class="post-body__code">
        <span v-if="block.language" class="post-body__code-language">{{ block.language }}</span>
        <PostCode :code="block.code" :language="block.language" />
      </div>

      <div v-else-if="block.type === 'ad' && showInlineAds" class="post-ad">
        <span class="post-ad__label">광고 · AD</span>
        <div class="post-ad__slot">{{ block.label }}</div>
      </div>
    </template>
  </div>
</template>
