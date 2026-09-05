import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../../features/home/pages/HomePage.vue';
import PostDetailPage from '../../features/post/pages/PostDetailPage.vue';
import AdminDashboardPage from '../../features/admin/pages/AdminDashboardPage.vue';
import AdminMenuPage from '../../features/admin/pages/AdminMenuPage.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
      meta: {
        layout: 'public',
      },
    },
    {
      path: '/posts/:id',
      name: 'post-detail',
      component: PostDetailPage,
      meta: {
        layout: 'public',
      },
    },
    {
      path: '/admin',
      redirect: { name: 'admin-dashboard' },
    },
    {
      path: '/admin/dashboard',
      name: 'admin-dashboard',
      component: AdminDashboardPage,
      meta: {
        layout: 'admin',
      },
    },
    {
      path: '/admin/menus',
      name: 'admin-menus',
      component: AdminMenuPage,
      meta: {
        layout: 'admin',
      },
    },
  ],
});

export default router;
