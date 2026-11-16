<script setup>
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import PostBody from './PostBody.vue';
import { removePostReaction, setPostReaction } from '../api/postApi';
import { useMemberAuth } from '../../member/data/memberAuthStore';
import { rememberReturnPath } from '../../member/data/memberReturnPath';
import { dismissToast, notifyError } from '../../../shared/toast/toastStore';
import { getAuthenticationGeneration } from '../../../shared/api/blogApiClient';

const showInlineAds = false;
const showTags = false;
const showSecondaryActions = false;

// 댓글 칸은 이 컴포넌트 밖에 있다. 직접 건드리지 않고 부모에게 넘긴다
const emit = defineEmits(['focus-comments', 'reaction-changed']);

const props = defineProps({
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
	authorLoading: {
		type: Boolean,
		default: false,
	},
});

const route = useRoute();
const router = useRouter();
const { member, isSignedIn } = useMemberAuth();
const reactionPending = ref(false);
const reactionError = ref('');
let active = true;
let reactionRequest = null;
let reactionErrorToast = null;
let reactionIdentity = readReactionIdentity();

function clearReactionError() {
	reactionError.value = '';
	if (reactionErrorToast !== null) {
		dismissToast(reactionErrorToast);
		reactionErrorToast = null;
	}
}

function readReactionIdentity() {
	return {
		memberId: member.value?.id ?? null,
		authentication: getAuthenticationGeneration(),
		postId: props.post.id,
	};
}

function synchronizeReactionIdentity() {
	if (!active) {
		return;
	}
	const next = readReactionIdentity();
	const accountChanged = next.memberId !== reactionIdentity.memberId
		|| next.authentication !== reactionIdentity.authentication;
	if (!accountChanged && next.postId === reactionIdentity.postId) {
		return;
	}
	reactionIdentity = next;
	reactionRequest = null;
	reactionPending.value = false;
	clearReactionError();

	if (accountChanged) {
		// 합계는 서버가 알려준 값이다. 계정 전환으로 내 표시만 비우고 수를 추정해 바꾸지 않는다.
		emit('reaction-changed', {
			likeCount: props.post.likeCount,
			dislikeCount: props.post.dislikeCount,
			likedByMe: false,
			dislikedByMe: false,
		});
	}
}

// 같은 ID의 닉네임 갱신은 유지하고, A→B→A 전환은 각 변경 시점에 이전 요청을 끊는다.
watch([member, () => props.post.id], synchronizeReactionIdentity, { flush: 'sync' });

function isCurrentReaction(request) {
	// 인증 세대는 반응형 값이 아니므로 응답 경계에서도 실제 값을 확인한다.
	synchronizeReactionIdentity();
	return active && reactionRequest === request;
}

async function toggleLike() {
	synchronizeReactionIdentity();
	if (!isSignedIn.value) {
		rememberReturnPath(route.fullPath);
		await router.push({ name: 'member-login' });
		return;
	}

	if (reactionPending.value) {
		return;
	}

	reactionPending.value = true;
	clearReactionError();
	const request = { ...reactionIdentity };
	reactionRequest = request;

	try {
		const reaction = props.post.likedByMe
			? await removePostReaction(request.postId, 'LIKE')
			: await setPostReaction(request.postId, 'LIKE');

		if (isCurrentReaction(request)) {
			emit('reaction-changed', reaction);
		}
	} catch (error) {
		if (!isCurrentReaction(request)) {
			return;
		}
		reactionError.value = error?.message ?? '좋아요를 저장하지 못했습니다.';
		reactionErrorToast = notifyError(reactionError.value);
	} finally {
		if (isCurrentReaction(request)) {
			reactionRequest = null;
			reactionPending.value = false;
		}
	}
}

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

onBeforeUnmount(() => {
	active = false;
	reactionRequest = null;
	clearReactionError();
	clearTimeout(noticeTimer);
});
</script>

<template>
	<article class="post-article">
		<header class="post-article__header">
			<div class="post-article__meta">
				<span class="post-article__category">{{ post.category }}</span>
				<time :datetime="post.publishedAtIso">{{ post.publishedAt }}</time>
				<span class="post-article__dot">·</span>
				<span>조회 {{ post.views }}</span>
			</div>

			<h1 class="post-article__title">{{ post.title }}</h1>
			<p class="post-article__excerpt">{{ post.excerpt }}</p>

			<div class="post-author" :aria-busy="authorLoading">
				<template v-if="authorLoading">
					<span class="ui-skeleton ui-skeleton--circle post-author__avatar" aria-hidden="true"></span>
					<div class="post-author__info post-author__info--skeleton" aria-hidden="true">
						<span class="ui-skeleton"></span>
						<span class="ui-skeleton"></span>
					</div>
				</template>
				<template v-else>
					<img
						v-if="author.avatarImageUrl"
						class="post-author__avatar"
						:src="author.avatarImageUrl"
						:alt="author.name"
						width="52"
						height="52"
						decoding="async"
					/>
					<div class="post-author__info">
						<div class="post-author__name">{{ author.name }}</div>
						<div class="post-author__desc">{{ author.job }}</div>
					</div>
				</template>
			</div>
		</header>

		<figure v-if="post.coverImageUrl" class="post-article__cover">
			<img
				:src="post.coverImageUrl"
				:alt="post.coverImageAlt"
				loading="eager"
				decoding="async"
				fetchpriority="high"
			/>
		</figure>

		<!-- 이어서 읽기가 본문 덩어리를 찾는 표식. 토글 안쪽의 본문에는 붙이지 않는다 -->
		<PostBody data-reading-root :body="body" :show-inline-ads="showInlineAds" />

		<div v-if="showTags && post.tags?.length" class="post-tags">
			<span v-for="tag in post.tags" :key="tag" class="post-tags__item">#{{ tag }}</span>
		</div>

		<div class="post-reactions">
			<button
				class="ui-button ui-button--pill ui-button--like"
				:class="{ 'ui-button--on': post.likedByMe }"
				type="button"
				:disabled="reactionPending"
				:aria-pressed="post.likedByMe"
				@click="toggleLike"
			>
				<span class="post-reactions__heart" aria-hidden="true">{{ post.likedByMe ? '♥' : '♡' }}</span>
				<span>좋아요</span>
				<strong>{{ post.likeCount }}</strong>
			</button>
			<button
				class="ui-button ui-button--pill"
				type="button"
				@click="emit('focus-comments')"
			>댓글 {{ post.commentCount }}</button>
			<span v-if="reactionError" class="post-reactions__error" role="alert">
				{{ reactionError }}
			</span>
			<div class="post-reactions__spacer"></div>
			<!--
				공유는 글 맨 위가 아니라 여기다. 남에게 보내고 싶어지는 것은 읽기 전이 아니라
				다 읽은 뒤라, 스크롤을 거슬러 올라가게 만들 이유가 없다.

				화면 낭독기도 알아야 하므로 aria-live 로 알린다. 색과 글자만 바뀌면 안 들린다
			-->
			<button class="ui-button ui-button--pill ui-button--steady" type="button" @click="share">
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
