import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../../features/home/pages/HomePage.vue';
import PostDetailPage from '../../features/post/pages/PostDetailPage.vue';
import AdminLayout from '../../features/admin/components/AdminLayout.vue';
import AdminDashboardPage from '../../features/admin/pages/AdminDashboardPage.vue';
import AdminMenuPage from '../../features/admin/pages/AdminMenuPage.vue';
import AdminCategoryPage from '../../features/admin/pages/AdminCategoryPage.vue';
import AdminPostPage from '../../features/admin/pages/AdminPostPage.vue';
import AdminPostEditPage from '../../features/admin/pages/AdminPostEditPage.vue';

// 샌드박스(src/sandbox)는 gitignore 대상이라 없을 수 있다.
// import.meta.glob은 매칭되는 파일이 없으면 빈 객체를 주므로 빌드가 깨지지 않는다.
const sandboxRoutes = Object.values(
  import.meta.glob('../../sandbox/routes.js', { eager: true, import: 'default' }),
).flat();

const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...sandboxRoutes,
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
        // title·group은 화면 제목 줄에서 쓴다.
        // 메뉴 API 에서 찾으면 그 이름으로 덮이고, 못 찾으면 여기 적힌 값이 쓰인다
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: AdminDashboardPage,
          meta: {
            title: '대시보드',
            group: '운영',
          },
        },
        {
          path: 'menus',
          name: 'admin-menus',
          component: AdminMenuPage,
          meta: {
            title: '메뉴 관리',
            group: '블로그',
          },
        },
        {
          path: 'posts',
          name: 'admin-posts',
          component: AdminPostPage,
          meta: {
            title: '글 관리',
            group: '콘텐츠',
          },
        },
        {
          path: 'posts/new',
          name: 'admin-post-new',
          component: AdminPostEditPage,
          meta: {
            title: '새 글 쓰기',
            group: '콘텐츠',
          },
        },
        {
          path: 'posts/:postId',
          name: 'admin-post-edit',
          component: AdminPostEditPage,
          meta: {
            title: '글 편집',
            group: '콘텐츠',
          },
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: AdminCategoryPage,
          meta: {
            title: '카테고리',
            group: '콘텐츠',
          },
        },
      ],
    },
  ],
});

export default router;
