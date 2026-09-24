<script setup>
import AdminSideBar from './AdminSideBar.vue';
import AdminToast from './AdminToast.vue';
import NotificationBell from '../../member/components/NotificationBell.vue';
</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <AdminSideBar />

      <main class="admin-main">
        <!--
          공개 화면의 종을 그대로 쓴다. 관리자도 회원이라 알림이 같은 자리에 쌓인다.

          신고 알림은 운영자에게 가는데, 여기에 종이 없으면 도착해도 볼 데가 없다 —
          관리자가 공개 블로그를 우연히 열어 볼 때까지 모르게 된다. 그건 신고 알림을
          만들기 전과 같다
        -->
        <div class="admin-topbar">
          <NotificationBell />
        </div>

        <RouterView />
      </main>
    </div>

    <!-- 관리자 화면 어디서 무슨 일을 하든 결과가 여기로 모인다 -->
    <AdminToast />
  </div>
</template>
