<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import BaseModal from '../../../shared/components/BaseModal.vue';
import HighlightedText from './HighlightedText.vue';
import { getPostSuggestions } from '../../post/api/postApi';
import { endsWithIncompleteHangul } from '../../../shared/search/keywordHighlight';

/**
 * 헤더의 검색.
 *
 * <p>여는 단추는 헤더마다 생김새가 달라서 바깥에서 넘겨받는다. 대문은 글자 단추,
 * 글보기는 아이콘 단추다.</p>
 *
 * <p>치는 동안 글이 바로 뜬다. 검색어를 추천하지 않는 이유는 추천할 밑천이 없기 때문이다 —
 * 그건 사람들이 무엇을 찾았는지 쌓인 뒤에야 할 수 있다. 글을 바로 보여 주는 쪽은
 * 지금 글이 몇 편이든 뜻이 통한다.</p>
 */

/** 서버의 PostListCondition.MIN_KEYWORD_LENGTH 와 같은 값이어야 한다 */
const MIN_LENGTH = 2;

/**
 * 치기를 멈춘 뒤 기다리는 시간.
 *
 * 너무 짧으면 한 글자마다 요청이 나가고, 너무 길면 멈췄는데도 결과가 안 뜬다.
 * 한글은 한 글자에 자판을 두세 번 누르므로 영문보다 조금 넉넉한 편이 낫다.
 */
const DEBOUNCE_MS = 250;

const router = useRouter();
const route = useRoute();

const open = ref(false);
const field = ref(null);

/**
 * 입력칸의 값을 Vue 가 되돌려 쓰지 않는다({@code :value} 를 걸지 않았다).
 *
 * 한글은 글자가 완성되기 전까지 입력칸 안에서 조합 중인 상태로 머무는데, 그 사이에
 * Vue 가 값을 다시 써 넣으면 조합이 끊긴다. "한글" 이 "ㅎㅏㄴㄱㅡㄹ" 로 풀리는 일이
 * 그래서 생긴다. 값은 입력칸이 들고, 우리는 읽기만 한다.
 */
const keyword = ref('');
const suggestions = ref([]);
const loading = ref(false);
const activeIndex = ref(-1);

let timer = null;
let inflight = null;

const canSearch = computed(() => keyword.value.trim().length >= MIN_LENGTH);

const hint = computed(() => {
  if (keyword.value.trim().length === 0) {
    return '제목과 요약에서 찾습니다. 대소문자를 구분합니다.';
  }

  if (!canSearch.value) {
    return `${MIN_LENGTH}글자 이상 입력해 주세요.`;
  }

  return '';
});

function openBox() {
  open.value = true;

  nextTick(() => {
    field.value?.focus();
  });
}

function closeBox() {
  open.value = false;
  cancelPending();

  keyword.value = '';
  suggestions.value = [];
  activeIndex.value = -1;
  loading.value = false;

  // 입력칸 값은 Vue 가 들고 있지 않으므로 직접 비운다
  if (field.value) {
    field.value.value = '';
  }
}

function cancelPending() {
  clearTimeout(timer);
  timer = null;

  inflight?.abort();
  inflight = null;
}

function onInput(event) {
  keyword.value = event.target.value;
  activeIndex.value = -1;

  schedule();
}

/**
 * 조합이 끝났을 때도 한 번 더 부른다.
 *
 * {@code input} 이 조합 중에도 오기는 하지만, 마지막 글자가 완성되는 순간에는
 * 브라우저에 따라 {@code input} 이 오지 않는 경우가 있다. 그때 "스프리" 에서 멈춘다.
 */
function onCompositionEnd(event) {
  keyword.value = event.target.value;

  schedule();
}

function schedule() {
  clearTimeout(timer);

  const typed = keyword.value.trim();

  if (typed.length < MIN_LENGTH) {
    // 앞선 요청의 답이 뒤늦게 와서 빈 칸에 결과가 남는 일을 막는다
    cancelPending();
    suggestions.value = [];
    loading.value = false;
    return;
  }

  // 낱자로 끝나면 아직 치는 중이다. 이전 결과를 그대로 두고 기다린다
  if (endsWithIncompleteHangul(typed)) {
    return;
  }

  loading.value = true;
  timer = setTimeout(() => load(typed), DEBOUNCE_MS);
}

async function load(typed) {
  // 앞 요청을 끊지 않으면 늦게 온 답이 최신 결과를 덮어써서 한 글자 전으로 되돌아간다
  inflight?.abort();
  inflight = new AbortController();

  const controller = inflight;

  try {
    const found = await getPostSuggestions(typed, { signal: controller.signal });

    // 기다리는 동안 더 쳤으면 이 답은 이미 낡았다
    if (controller !== inflight) {
      return;
    }

    suggestions.value = found;
    activeIndex.value = -1;
  } catch (error) {
    if (error.name === 'AbortError') {
      return;
    }

    // 자동완성이 실패해도 검색 자체는 할 수 있어야 한다. 목록만 비운다
    console.error(error);
    suggestions.value = [];
  } finally {
    if (controller === inflight) {
      loading.value = false;
    }
  }
}

function move(step) {
  if (suggestions.value.length === 0) {
    return;
  }

  const last = suggestions.value.length - 1;
  const next = activeIndex.value + step;

  // 끝에서 한 번 더 누르면 입력칸으로 돌아온다. 갇히지 않게
  activeIndex.value = next < -1 ? last : next > last ? -1 : next;
}

function onEnter() {
  const chosen = suggestions.value[activeIndex.value];

  if (chosen) {
    goToPost(chosen.id);
    return;
  }

  submit();
}

/** 고른 글이 없으면 전체 결과 화면으로 간다 */
function submit() {
  if (!canSearch.value) {
    return;
  }

  const typed = keyword.value.trim();

  closeBox();
  router.push({ name: 'post-list', query: { q: typed } });
}

function goToPost(postId) {
  closeBox();
  router.push({ name: 'post-detail', params: { id: postId } });
}

// 화면을 옮기면 닫는다. 열어 둔 채 이동하면 새 화면 위에 그대로 덮여 있다
watch(() => route.fullPath, closeBox);

watch(open, (isOpen) => {
  // 열려 있는 동안 뒤 화면이 같이 굴러다니면 어디를 보는지 알 수 없다
  document.body.style.overflow = isOpen ? 'hidden' : '';
});

onBeforeUnmount(() => {
  cancelPending();
  document.body.style.overflow = '';
});
</script>

<template>
  <slot name="trigger" :open="openBox" />

  <!--
    덮개·제목·× 는 공용 모달이 만든다. 전에는 여기서 직접 만들어서 제목이 없었고
    닫는 자리도 "닫기" 글자였다 — 팝업마다 손으로 지키면 이렇게 빠진다
  -->
  <BaseModal :open="open" title="검색" :close-on-backdrop="true" @close="closeBox">
    <input
      ref="field"
      class="search-panel__input"
      type="search"
      autocomplete="off"
      placeholder="찾을 말을 입력하세요"
      aria-label="검색어"
      role="combobox"
      aria-expanded="true"
      aria-controls="search-results"
      @input="onInput"
      @compositionend="onCompositionEnd"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="onEnter"
    />

    <p v-if="hint" class="search-panel__hint">{{ hint }}</p>

    <ul v-else-if="suggestions.length > 0" id="search-results" class="search-results" role="listbox">
      <li
        v-for="(post, index) in suggestions"
        :key="post.id"
        role="option"
        :aria-selected="index === activeIndex"
      >
        <button
          class="search-results__item"
          :class="{ 'search-results__item--active': index === activeIndex }"
          type="button"
          @click="goToPost(post.id)"
          @mouseenter="activeIndex = index"
        >
          <span class="search-results__category">{{ post.categoryName }}</span>
          <span class="search-results__title">
            <HighlightedText :text="post.title" :keyword="keyword.trim()" />
          </span>
        </button>
      </li>
    </ul>

    <p v-else-if="loading" class="search-panel__hint">찾는 중…</p>
    <p v-else class="search-panel__hint">맞는 글이 없습니다.</p>

    <template v-if="canSearch" #footer>
      <button class="post-button post-button--accent" type="button" @click="submit">
        “{{ keyword.trim() }}” 전체 결과 보기
      </button>
    </template>
  </BaseModal>
</template>
