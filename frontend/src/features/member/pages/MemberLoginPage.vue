<script setup>
import { RouterLink } from 'vue-router';

import SocialLoginButtons from '../components/SocialLoginButtons.vue';

/**
 * 회원 로그인 시작 화면.
 *
 * 관리자 로그인({@code /admin/login})과 다른 화면이다. 여기는 공개 화면을 보는 사람이
 * 댓글을 쓰려고 들어오는 자리고, 아이디·비밀번호 칸이 없다.
 *
 * 여기서 CSRF 토큰을 미리 받아 두지 않는다. 제공자 화면으로 넘어가면 페이지가 통째로
 * 바뀌어 받아 둔 값이 사라진다. 토큰은 돌아온 뒤 콜백 화면에서 받는다.
 */
</script>

<template>
  <main class="member-auth">
    <div class="member-auth__panel">
      <h1 class="member-auth__title">로그인</h1>
      <p class="member-auth__description">
        댓글을 남기려면 로그인이 필요합니다. 따로 가입하지 않아도 됩니다.
      </p>

      <SocialLoginButtons />

      <RouterLink class="member-auth__back" to="/">블로그로 돌아가기</RouterLink>
    </div>
  </main>
</template>
