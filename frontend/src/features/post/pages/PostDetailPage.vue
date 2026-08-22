<script setup>
import { reactive, watch } from 'vue';
import { useRoute } from 'vue-router';

import PostHeader from '../components/PostHeader.vue';
import PostArticle from '../components/PostArticle.vue';
import PostAside from '../components/PostAside.vue';
import CommentSection from '../components/CommentSection.vue';
import PostRelated from '../components/PostRelated.vue';
import { getBlogProfile } from '../../home/api/homeApi';
import { getAdjacentPosts, getCategories, getPostDetail, getRelatedPosts } from '../api/postApi';
import { postMock } from '../data/postMock';

const route = useRoute();
const detail = reactive({ ...postMock });
const showAds = false;

watch(
  () => route.params.id,
  (postId) => {
    loadPostPage(postId);
  },
  { immediate: true },
);

async function loadPostPage(postId) {
  const [
    profileResult,
    categoriesResult,
    postDetailResult,
    adjacentPostsResult,
    relatedPostsResult,
  ] = await Promise.allSettled([
    getBlogProfile(),
    getCategories(),
    getPostDetail(postId),
    getAdjacentPosts(postId),
    getRelatedPosts(postId),
  ]);

  if (profileResult.status === 'fulfilled') {
    detail.author = mergeDefined(detail.author, profileResult.value);
  } else {
    console.error(profileResult.reason);
  }

  if (categoriesResult.status === 'fulfilled' && categoriesResult.value.length > 0) {
    detail.categories = categoriesResult.value;
  } else if (categoriesResult.status === 'rejected') {
    console.error(categoriesResult.reason);
  }

  if (postDetailResult.status === 'fulfilled') {
    detail.post = {
      ...postDetailResult.value.post,
      commentCount: detail.comments.total,
    };
    detail.body = postDetailResult.value.body;
    detail.toc = postDetailResult.value.toc;
  } else {
    console.error(postDetailResult.reason);
  }

  if (adjacentPostsResult.status === 'fulfilled') {
    detail.adjacentPosts = adjacentPostsResult.value;
  } else {
    console.error(adjacentPostsResult.reason);
  }

  if (relatedPostsResult.status === 'fulfilled' && relatedPostsResult.value !== null) {
    detail.relatedPosts = relatedPostsResult.value;
  } else if (relatedPostsResult.status === 'rejected') {
    console.error(relatedPostsResult.reason);
  }
}

function mergeDefined(base, next) {
  return Object.entries(next ?? {}).reduce(
    (result, [key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        result[key] = value;
      }

      return result;
    },
    { ...base },
  );
}
</script>

<template>
  <div class="public-shell">
    <PostHeader :title="detail.author.name" :categories="detail.categories" />

    <main class="public-shell__main post-shell">
      <div class="post-shell__layout">
        <!-- 본문·댓글·관련글이 한 컬럼. 오른쪽 레일은 그 옆으로 계속 내려온다 -->
        <div class="post-shell__column">
          <PostArticle
            :post="detail.post"
            :author="detail.author"
            :body="detail.body"
            :adjacent-posts="detail.adjacentPosts"
          />

          <CommentSection :comments="detail.comments" :next-page="detail.nextCommentPage" />

          <PostRelated :posts="detail.relatedPosts" />
        </div>

        <PostAside
          v-if="detail.toc.length > 0 || showAds"
          :toc="detail.toc"
          :ads="detail.asideAds"
          :show-ads="showAds"
        />
      </div>
    </main>

    <!-- 모바일 전용 하단 고정 광고 (시안 2a) -->
    <div v-if="showAds" class="anchor-ad">
      <span class="anchor-ad__label">광고 · AD (하단 고정)</span>
      <div class="anchor-ad__slot">{{ detail.anchorAd.label }}</div>
    </div>
  </div>
</template>
