<script setup>
import { RouterLink } from 'vue-router';

import MemberMenu from '../../member/components/MemberMenu.vue';
import NotificationBell from '../../member/components/NotificationBell.vue';
import SearchBox from '../../search/components/SearchBox.vue';
import SiteMenu from '../../navigation/components/SiteMenu.vue';

defineProps({
  title: {
    type: String,
    required: true,
  },
});

</script>

<template>
  <header class="blog-header">
    <!-- RouterLink 다. a 태그로 두면 눌렀을 때 화면 전체가 다시 뜬다 -->
    <RouterLink class="blog-header__brand" :to="{ name: 'home' }">{{ title }}</RouterLink>


    <nav class="blog-header__actions" aria-label="블로그 도구">
      <!-- 여는 단추를 바깥에서 넘긴다. SearchBox 는 여는 방법을 모르고 여기는 생김새만 안다 -->
      <SearchBox>
        <template #trigger="{ open }">
          <button
            class="blog-header__button blog-header__button--icon"
            type="button"
            aria-label="검색"
            title="검색"
            @click="open"
          >
            <!--
              동그라미만 그리면 돋보기로 안 보인다. 손잡이가 있어야 알아본다.
              글자 "메뉴" 와 나란히 서므로 굵기와 크기를 그쪽에 맞춘다
            -->
            <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" focusable="false">
              <circle
                cx="10.5"
                cy="10.5"
                r="6.5"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              />
              <path
                d="M15.4 15.4 21 21"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </template>
      </SearchBox>

      <SiteMenu>
        <template #trigger="{ open }">
          <button
            class="blog-header__button"
            type="button"
            aria-label="메뉴"
            @click="open"
          >메뉴</button>
        </template>
      </SiteMenu>

      <NotificationBell />
      <MemberMenu />
    </nav>
  </header>
</template>
