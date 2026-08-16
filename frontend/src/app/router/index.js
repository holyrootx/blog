import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../../features/home/pages/HomePage.vue';

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
  ],
});

export default router;
