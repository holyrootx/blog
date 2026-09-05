import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../../features/home/pages/HomePage.vue';
import PostDetailPage from '../../features/post/pages/PostDetailPage.vue';
import AdminLayout from '../../features/admin/components/AdminLayout.vue';
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
      component: AdminLayout,
      meta: {
        layout: 'admin',
      },
      children: [
        {
          path: '',
          redirect: { name: 'admin-dashboard' },
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: AdminDashboardPage,
        },
        {
          path: 'menus',
          name: 'admin-menus',
          component: AdminMenuPage,
        },
      ],
    },
  ],
});

export default router;
