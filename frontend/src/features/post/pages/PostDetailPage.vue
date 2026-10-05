<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { applyDocumentMeta, summarize } from '../../../app/router/documentMeta';

import PostArticle from '../components/PostArticle.vue';
import PostArticleSkeleton from '../components/PostArticleSkeleton.vue';
import PostAside from '../components/PostAside.vue';
import CommentSection from '../components/CommentSection.vue';
import PostRelated from '../components/PostRelated.vue';
import NotFoundPage from './NotFoundPage.vue';
import { blogProfile, blogProfileLoading } from '../../home/data/blogProfileStore';
import { useSectionLoader } from '../../../shared/composables/useSectionLoader';
import { mergeDefined } from '../../../shared/data/mergeDefined';
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
const { loading, load, restart } = useSectionLoader(['post', 'adjacent', 'related', 'comments']);

// 글쓴이는 블로그 주인 한 사람이라 헤더와 같은 프로필을 쓴다. 글을 옮길 때마다 다시 받지 않는다
const EMPTY_AUTHOR = {
  name: '',
  job: '',
  avatarImageUrl: '',
};
const author = computed(() => mergeDefined(EMPTY_AUTHOR, blogProfile.value));
const notFound = ref(false);
const postLoadError = ref(false);
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

  restart();
  notFound.value = false;
  postLoadError.value = false;
  Object.assign(detail, structuredClone({
    ...EMPTY_DETAIL,
    comments: EMPTY_COMMENTS,
  }));

  if (!/^[1-9]\d*$/.test(requestedPostId)) {
    notFound.value = true;
    loading.post = false;
    applyDocumentMeta({ title: '찾는 글이 없습니다', robots: 'noindex,follow' });
    return;
  }

  load('post', {
    cacheKey: `post:${requestedPostId}`,
    request: () => getPostDetail(postId),
    apply: (postDetail) => {
      detail.post = {
        ...postDetail.post,
        commentCount: detail.comments.total,
      };
      detail.body = postDetail.body;
      detail.toc = postDetail.toc;

      // 라우터는 글 번호만 알아서 제목을 못 채운다. 글이 도착한 지금 채운다
      applyDocumentMeta({
        title: postDetail.post.title,
        description: postDetail.post.excerpt?.trim() || summarize(postDetail.body),
        image: postDetail.post.coverImageUrl,
        path: `/posts/${requestedPostId}`,
        type: 'article',
      });
    },
    onError: (error, { showingCached }) => {
      // 그사이 글이 내려갔으면 전에 본 내용이 있어도 없는 글로 바꾼다
      if (error.status === 404) {
        notFound.value = true;
        applyDocumentMeta({ title: '찾는 글이 없습니다', robots: 'noindex,follow' });
        return;
      }

      console.error(error);
      if (!showingCached) {
        postLoadError.value = true;
      }
    },
  });
  load('adjacent', {
    cacheKey: `post:${requestedPostId}:adjacent`,
    request: () => getAdjacentPosts(postId),
    apply: (adjacentPosts) => {
      detail.adjacentPosts = adjacentPosts;
    },
  });
  load('related', {
    cacheKey: `post:${requestedPostId}:related`,
    request: () => getRelatedPosts(postId),
    apply: (relatedPosts) => {
      detail.relatedPosts = relatedPosts ?? [];
    },
  });
  // 댓글은 들고 있지 않는다. 쓰고 지우는 자리라 전에 받은 목록을 먼저 보이면 지운 댓글이 잠깐 되살아난다
  load('comments', {
    request: () => getPostComments(postId),
    apply: (comments) => {
      detail.comments = comments;
      detail.post = {
        ...detail.post,
        commentCount: comments.total,
      };
    },
  });
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
  <NotFoundPage v-if="notFound" />
  <template v-else>
    <main v-if="postLoadError" class="public-shell__main not-found">
      <h1 class="not-found__title">글을 불러오지 못했습니다</h1>
      <p class="not-found__text">잠시 후 다시 시도해 주세요.</p>
      <div class="not-found__actions">
        <button type="button" class="ui-button ui-button--accent" @click="loadPostPage(route.params.id)">
          다시 시도
        </button>
      </div>
    </main>
    <main v-else class="public-shell__main post-shell">
      <div class="post-shell__layout">
        <!-- 본문·댓글·관련글이 한 컬럼. 오른쪽 레일은 그 옆으로 계속 내려온다 -->
        <div class="post-shell__column">
          <PostArticleSkeleton v-if="loading.post" />
          <PostArticle
            v-else
            @focus-comments="commentSection?.focusCommentInput()"
            @reaction-changed="applyPostReaction"
            :post="detail.post"
            :author="author"
            :body="detail.body"
            :adjacent-posts="detail.adjacentPosts"
            :author-loading="blogProfileLoading"
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
  </template>
</template>
