<script setup>
import { reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import BlogHeader from '../../home/components/BlogHeader.vue';
import PostArticle from '../components/PostArticle.vue';
import PostArticleSkeleton from '../components/PostArticleSkeleton.vue';
import PostAside from '../components/PostAside.vue';
import CommentSection from '../components/CommentSection.vue';
import PostRelated from '../components/PostRelated.vue';
import { getBlogProfile } from '../../home/api/homeApi';
import {
  getAdjacentPosts,
  getPostComments,
  getPostDetail,
  getRelatedPosts,
} from '../api/postApi';

const EMPTY_COMMENTS = {
  total: 0,
  placeholder: '따뜻한 댓글 하나가 다음 글을 쓰게 만듭니다.',
  maxLength: 1000,
  items: [],
  nextCursor: null,
  hasNext: false,
};

// API 응답이 오기 전까지의 빈 상태. 화면이 참조하는 필드는 모두 존재해야 하므로
// 값만 비우고 형태는 유지한다.
const EMPTY_DETAIL = {
  post: {
    id: null,
    categoryId: null,
    category: '',
    title: '',
    excerpt: '',
    publishedAt: '',
    views: '',
    likeCount: 0,
    dislikeCount: 0,
    likedByMe: false,
    dislikedByMe: false,
    coverImageUrl: '',
    coverImageAlt: '',
    tags: [],
    commentCount: 0,
  },
  author: {
    name: '',
    job: '',
    avatarImageUrl: '',
  },
  body: [],
  toc: [],
  adjacentPosts: { prev: null, next: null },
  relatedPosts: [],
  asideAds: [],
  anchorAd: { label: '' },
};

const route = useRoute();

// 본문의 "댓글 N" 이 댓글 칸에 커서를 옮기려면 그 컴포넌트를 잡고 있어야 한다
const commentSection = ref(null);
const detail = reactive({
  ...structuredClone(EMPTY_DETAIL),
  comments: { ...EMPTY_COMMENTS },
});
const loading = reactive({
  author: true,
  post: true,
  adjacent: true,
  related: true,
  comments: true,
});
const showAds = false;

watch(
  () => route.params.id,
  (postId) => {
    loadPostPage(postId);
  },
  { immediate: true },
);

function loadPostPage(postId) {
  const requestedPostId = String(postId);

  Object.assign(detail, structuredClone({
    ...EMPTY_DETAIL,
    comments: EMPTY_COMMENTS,
  }));
  Object.keys(loading).forEach((key) => {
    loading[key] = true;
  });

  loadPostData('author', requestedPostId, getBlogProfile, (profile) => {
    detail.author = mergeDefined(detail.author, profile);
  });
  loadPostData('post', requestedPostId, () => getPostDetail(postId), (postDetail) => {
    detail.post = {
      ...postDetail.post,
      commentCount: detail.comments.total,
    };
    detail.body = postDetail.body;
    detail.toc = postDetail.toc;
  });
  loadPostData('adjacent', requestedPostId, () => getAdjacentPosts(postId), (adjacentPosts) => {
    detail.adjacentPosts = adjacentPosts;
  });
  loadPostData('related', requestedPostId, () => getRelatedPosts(postId), (relatedPosts) => {
    detail.relatedPosts = relatedPosts ?? [];
  });
  loadPostData('comments', requestedPostId, () => getPostComments(postId), (comments) => {
    detail.comments = comments;
    detail.post = {
      ...detail.post,
      commentCount: comments.total,
    };
  });
}

function loadPostData(key, requestedPostId, request, apply) {
  request()
    .then((value) => {
      if (String(route.params.id) === requestedPostId) {
        apply(value);
      }
    })
    .catch((error) => console.error(error))
    .finally(() => {
      if (String(route.params.id) === requestedPostId) {
        loading[key] = false;
      }
    });
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

function applyPostReaction(reaction) {
  detail.post = {
    ...detail.post,
    likeCount: Number(reaction?.likeCount ?? 0),
    dislikeCount: Number(reaction?.dislikeCount ?? 0),
    likedByMe: Boolean(reaction?.likedByMe),
    dislikedByMe: Boolean(reaction?.dislikedByMe),
  };
}
</script>

<template>
  <div class="public-shell">
    <BlogHeader :title="detail.author.name || 'JSJ.log'" />

    <main class="public-shell__main post-shell">
      <div class="post-shell__layout">
        <!-- 본문·댓글·관련글이 한 컬럼. 오른쪽 레일은 그 옆으로 계속 내려온다 -->
        <div class="post-shell__column">
          <PostArticleSkeleton v-if="loading.post" />
          <PostArticle
            v-else
            @focus-comments="commentSection?.focusCommentInput()"
            @reaction-changed="applyPostReaction"
            :post="detail.post"
            :author="detail.author"
            :body="detail.body"
            :adjacent-posts="detail.adjacentPosts"
            :author-loading="loading.author"
          />

          <CommentSection
            ref="commentSection"
            :post-id="route.params.id"
            :comments="detail.comments"
            :initial-loading="loading.comments"
          />

          <PostRelated
            :posts="detail.relatedPosts"
            :category-id="detail.post.categoryId ?? null"
            :loading="loading.related"
          />
        </div>

        <aside v-if="loading.post" class="post-aside post-aside--skeleton" aria-hidden="true">
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
          <span class="ui-skeleton"></span>
        </aside>
        <PostAside
          v-else-if="detail.toc.length > 0 || showAds"
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
