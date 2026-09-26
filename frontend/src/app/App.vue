<script setup>
import AppToast from '../shared/components/AppToast.vue';
</script>

<template>
  <RouterView />

  <!--
    결과 알림은 여기 한 번만 붙인다. 관리자는 레이아웃이 하나라 거기 달면 됐지만,
    공개 화면은 페이지마다 제 껍데기를 각자 그려서 한 곳이 없다. 뿌리에 두면
    어느 화면에서 불러도 뜬다
  -->
  <AppToast />
</template>
