<script setup>
import { socialLoginUrl } from '../api/memberAuthApi';

/**
 * 소셜 로그인 시작 버튼.
 *
 * RouterLink 가 아니라 평범한 링크다. 제공자 동의 화면으로 넘어갔다가 돌아와야 하므로
 * 브라우저가 통째로 이동해야 한다. 라우터가 가로채면 주소만 바뀌고 아무 일도 안 일어난다.
 *
 * 가입과 로그인을 나누지 않는다. 누른 사람이 처음인지 아닌지는 제공자가 알려 주기 전까지
 * 알 수 없어서, 서버가 받아 보고 판단한다.
 */
const PROVIDERS = [
  { id: 'google', label: '구글로 계속하기' },
  { id: 'kakao', label: '카카오로 계속하기' },
  { id: 'naver', label: '네이버로 계속하기' },
];
</script>

<template>
  <ul class="member-auth__providers">
    <li v-for="provider in PROVIDERS" :key="provider.id">
      <a
        class="member-auth__provider"
        :class="`member-auth__provider--${provider.id}`"
        :href="socialLoginUrl(provider.id)"
      >{{ provider.label }}</a>
    </li>
  </ul>
</template>
