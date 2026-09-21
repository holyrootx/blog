<script setup>
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';

import { getMyComments } from '../api/memberAuthApi';
import {
  changeNickname,
  ensureMemberSession,
  useMemberAuth,
  withdraw,
} from '../data/memberAuthStore';

/**
 * 내 계정 설정.
 *
 * <p>대시보드가 아니다. 회원이 가진 것은 닉네임과 댓글뿐이고 프로필 사진은 제공자가 주는
 * 값이라 여기서 바꿀 수 없다. 그래서 할 수 있는 일만 세 가지 올려 둔다 —
 * 닉네임 변경, 내가 쓴 댓글 보기, 탈퇴.</p>
 */
const router = useRouter();
const { member, isSignedIn } = useMemberAuth();

const NICKNAME_MAX_LENGTH = 50;

const nickname = ref('');
const savingNickname = ref(false);
const nicknameError = ref('');
const nicknameSaved = ref(false);

const comments = ref([]);
const commentsLoading = ref(true);

const withdrawing = ref(false);
const withdrawConfirming = ref(false);
const withdrawError = ref('');

const nicknameChanged = computed(
  () => nickname.value.trim() !== '' && nickname.value.trim() !== (member.value?.nickname ?? ''),
);

onMounted(async () => {
  // 앱이 뜰 때 시작한 세션 확인을 여기서 기다린다. 기다리지 않으면 아직 응답이 안 온
  // 사이에 "로그인 안 함" 으로 읽혀서, 로그인한 사람도 로그인 화면으로 튕긴다
  await ensureMemberSession();

  if (!isSignedIn.value) {
    await router.replace({ name: 'member-login' });
    return;
  }

  nickname.value = member.value?.nickname ?? '';

  try {
    comments.value = await getMyComments();
  } catch (error) {
    // 댓글을 못 불러와도 닉네임 변경과 탈퇴는 쓸 수 있어야 한다
    console.error(error);
  } finally {
    commentsLoading.value = false;
  }
});

function avatarInitial(name) {
  return Array.from(name ?? '')[0] ?? '';
}

function formatDate(value) {
  if (!value) {
    return '';
  }

  return String(value).slice(0, 10).replaceAll('-', '.');
}

async function saveNickname() {
  if (savingNickname.value || !nicknameChanged.value) {
    return;
  }

  savingNickname.value = true;
  nicknameError.value = '';
  nicknameSaved.value = false;

  try {
    await changeNickname(nickname.value.trim());

    nicknameSaved.value = true;
  } catch (error) {
    nicknameError.value = error?.message ?? '닉네임을 변경하지 못했습니다.';
  } finally {
    savingNickname.value = false;
  }
}

async function confirmWithdraw() {
  if (withdrawing.value) {
    return;
  }

  withdrawing.value = true;
  withdrawError.value = '';

  try {
    await withdraw();
    await router.replace({ name: 'home' });
  } catch (error) {
    withdrawError.value = error?.message ?? '탈퇴하지 못했습니다.';
    withdrawConfirming.value = false;
  } finally {
    withdrawing.value = false;
  }
}
</script>

<template>
  <main v-if="isSignedIn" class="member-settings">
    <header class="member-settings__head">
      <h1 class="member-settings__title">내 설정</h1>
      <RouterLink class="member-settings__back" to="/">블로그로 돌아가기</RouterLink>
    </header>

    <section class="member-settings__section" aria-labelledby="settings-profile">
      <h2 id="settings-profile" class="member-settings__section-title">프로필</h2>

      <div class="member-settings__profile">
        <img
          v-if="member?.profileImageUrl"
          class="member-settings__avatar"
          :src="member.profileImageUrl"
          alt=""
          referrerpolicy="no-referrer"
        />
        <span v-else class="member-settings__avatar">{{ avatarInitial(member?.nickname) }}</span>

        <div class="member-settings__field">
          <label class="member-settings__label" for="settings-nickname">닉네임</label>
          <input
            id="settings-nickname"
            v-model="nickname"
            class="member-settings__input"
            type="text"
            autocomplete="nickname"
            :maxlength="NICKNAME_MAX_LENGTH"
          />
          <p class="member-settings__hint">댓글에 이 이름으로 표시됩니다.</p>
        </div>

        <button
          class="post-button post-button--accent"
          type="button"
          :disabled="savingNickname || !nicknameChanged"
          @click="saveNickname"
        >{{ savingNickname ? '저장 중…' : '변경' }}</button>
      </div>

      <p v-if="nicknameError" class="member-settings__error" role="alert">{{ nicknameError }}</p>
      <p v-else-if="nicknameSaved" class="member-settings__done" role="status">닉네임을 변경했습니다.</p>

      <p class="member-settings__note">
        프로필 사진은 로그인한 소셜 계정에서 가져옵니다. 바꾸려면 그쪽에서 바꾼 뒤 다시 로그인해 주세요.
      </p>
    </section>

    <section class="member-settings__section" aria-labelledby="settings-comments">
      <h2 id="settings-comments" class="member-settings__section-title">내가 쓴 댓글</h2>

      <p v-if="commentsLoading" class="member-settings__empty">불러오는 중…</p>
      <p v-else-if="comments.length === 0" class="member-settings__empty">아직 쓴 댓글이 없습니다.</p>

      <ul v-else class="member-comments">
        <li v-for="comment in comments" :key="comment.id" class="member-comments__item">
          <RouterLink
            class="member-comments__post"
            :to="{ name: 'post-detail', params: { id: comment.postId } }"
          >{{ comment.postTitle }}</RouterLink>

          <p v-if="comment.hidden" class="member-comments__content member-comments__content--hidden">
            관리자가 숨긴 댓글입니다.
          </p>
          <p v-else class="member-comments__content">{{ comment.content }}</p>

          <time class="member-comments__date">{{ formatDate(comment.createdAt) }}</time>
        </li>
      </ul>
    </section>

    <section class="member-settings__section member-settings__section--danger" aria-labelledby="settings-withdraw">
      <h2 id="settings-withdraw" class="member-settings__section-title">회원 탈퇴</h2>

      <!-- "삭제됩니다" 라고 쓰지 않는다. 댓글은 지워지지 않고 닉네임과 함께 그대로 남는다.
           지워진다고 적으면 그렇게 기대하고 누르게 되고, 안 지워지면 항의가 온다 -->
      <p class="member-settings__note">
        탈퇴하면 이메일과 로그인 정보가 지워지고 이 계정으로는 다시 로그인할 수 없습니다.
        <strong>지금까지 쓴 댓글은 지워지지 않고 닉네임과 함께 그대로 남습니다.</strong>
        같은 소셜 계정으로 다시 로그인하면 예전 계정을 되살릴지 물어봅니다.
      </p>

      <p v-if="withdrawError" class="member-settings__error" role="alert">{{ withdrawError }}</p>

      <div v-if="!withdrawConfirming" class="member-settings__actions">
        <button class="post-button" type="button" @click="withdrawConfirming = true">탈퇴하기</button>
      </div>

      <div v-else class="member-settings__actions">
        <span class="member-settings__confirm">정말 탈퇴할까요?</span>
        <button
          class="post-button"
          type="button"
          :disabled="withdrawing"
          @click="withdrawConfirming = false"
        >취소</button>
        <button
          class="post-button member-settings__danger-button"
          type="button"
          :disabled="withdrawing"
          @click="confirmWithdraw"
        >{{ withdrawing ? '처리 중…' : '탈퇴' }}</button>
      </div>
    </section>
  </main>
</template>
