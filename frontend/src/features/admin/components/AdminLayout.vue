<script setup>
import AdminSideBar from './AdminSideBar.vue';
</script>

<template>
  <div class="admin-page">
    <div class="admin-shell">
      <AdminSideBar />

      <main class="admin-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
