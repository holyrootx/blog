<script setup>
import AdminSideBar from './AdminSideBar.vue';
import AdminToast from './AdminToast.vue';
</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <AdminSideBar />

      <main class="admin-main">
        <RouterView />
      </main>
    </div>

    <!-- 관리자 화면 어디서 무슨 일을 하든 결과가 여기로 모인다 -->
    <AdminToast />
  </div>
</template>
