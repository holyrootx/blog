import { START_LOCATION, createRouter, createWebHistory } from 'vue-router';
import { clearAdminSession, ensureAdminSession } from '../../features/admin/data/adminAuthStore';
import { clearMemberSession } from '../../features/member/data/memberAuthStore';
import { onUnauthorized } from '../../shared/api/blogApiClient';
import { applyDocumentMeta } from './documentMeta';
import { scrollToHash } from './scrollToHash';

/*
 * 화면은 전부 () => import 로 받는다.
 *
 * 전에는 정적 import 라 파일 하나에 다 들어갔다. 관리자 화면이 소스의 절반이 넘어서,
 * 글 하나 읽으러 온 사람이 1,500 줄짜리 블록 편집기까지 받아 갔다.
 *
 * 이렇게 하면 첫 화면을 그릴 때 요청이 한 번 더 간다. 대신 받는 양과 파싱할 양이
 * 줄어서 휴대폰에서 이득이 크다.
 */
const HomePage = () => import('../../features/home/pages/HomePage.vue');
const PostListPage = () => import('../../features/post/pages/PostListPage.vue');
const PostDetailPage = () => import('../../features/post/pages/PostDetailPage.vue');
const NotFoundPage = () => import('../../features/post/pages/NotFoundPage.vue');
const MemberLoginPage = () => import('../../features/member/pages/MemberLoginPage.vue');
const OAuthCallbackPage = () => import('../../features/member/pages/OAuthCallbackPage.vue');
const MemberSettingsPage = () => import('../../features/member/pages/MemberSettingsPage.vue');
const PrivacyPolicyPage = () => import('../../features/legal/pages/PrivacyPolicyPage.vue');
const TermsPage = () => import('../../features/legal/pages/TermsPage.vue');
const PublicLayout = () => import('../PublicLayout.vue');
const AdminLayout = () => import('../../features/admin/components/AdminLayout.vue');
const AdminLoginPage = () => import('../../features/admin/pages/AdminLoginPage.vue');
const AdminDashboardPage = () => import('../../features/admin/pages/AdminDashboardPage.vue');
const AdminMenuPage = () => import('../../features/admin/pages/AdminMenuPage.vue');
const AdminCategoryPage = () => import('../../features/admin/pages/AdminCategoryPage.vue');
const AdminPostPage = () => import('../../features/admin/pages/AdminPostPage.vue');
const AdminPostEditPage = () => import('../../features/admin/pages/AdminPostEditPage.vue');
const AdminCommentPage = () => import('../../features/admin/pages/AdminCommentPage.vue');
const AdminHomeSettingsPage = () => import('../../features/admin/pages/AdminHomeSettingsPage.vue');
const AdminMemberPage = () => import('../../features/admin/pages/AdminMemberPage.vue');
const AdminImagePage = () => import('../../features/admin/pages/AdminImagePage.vue');
const AdminCommonCodePage = () => import('../../features/admin/pages/AdminCommonCodePage.vue');

// 샌드박스(src/sandbox)는 gitignore 대상이라 없을 수 있다.
// import.meta.glob은 매칭되는 파일이 없으면 빈 객체를 주므로 빌드가 깨지지 않는다.
const sandboxRoutes = Object.values(
  import.meta.glob('../../sandbox/routes.js', { eager: true, import: 'default' }),
).flat();

const router = createRouter({
  history: createWebHistory(),
  // 해시가 있으면 그 자리로, 없으면 맨 위로. 안 주면 Vue Router 가 해시를 무시한다
  scrollBehavior: scrollToHash,
  routes: [
    ...sandboxRoutes,
    // 공개 화면. 헤더와 푸터는 PublicLayout 이 한 번만 그리고, 아래 화면은 본문만 그린다
    {
      path: '/',
      component: PublicLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: HomePage,
        },
        // 대문과 글 하단의 "전체 보기" 가 오는 자리.
        // 이 라우트가 없던 동안 두 링크는 흰 화면으로 떨어졌다
        {
          path: 'posts',
          name: 'post-list',
          component: PostListPage,
          meta: {
            title: '전체 글',
          },
        },
        {
          path: 'posts/:id',
          name: 'post-detail',
          component: PostDetailPage,
        },
        // 회원 로그인. 관리자 로그인과 다른 화면이다 — 아이디·비밀번호 칸이 없고
        // 공개 화면을 보던 사람이 댓글을 쓰려고 들어오는 자리다
        {
          path: 'login',
          name: 'member-login',
          component: MemberLoginPage,
          meta: {
            title: '로그인',
            robots: 'noindex,follow',
          },
        },
        // 소셜 인증을 마친 브라우저가 돌아오는 자리.
        // 서버가 결과를 ?result= 로만 알려 주고, 제공자 식별자는 서버 세션에만 둔다
        {
          path: 'oauth/callback',
          name: 'oauth-callback',
          component: OAuthCallbackPage,
          meta: {
            robots: 'noindex,follow',
          },
        },
        // 내 계정 설정. 로그인 확인은 화면이 직접 한다 — 앱이 뜰 때 시작한 /me 응답을
        // 기다려야 해서, 가드에서 막으면 아직 확인 전인 사람이 로그인 화면으로 튕긴다
        {
          path: 'settings',
          name: 'member-settings',
          component: MemberSettingsPage,
          meta: {
            title: '내 설정',
            robots: 'noindex,follow',
          },
        },
        {
          path: 'privacy',
          name: 'privacy-policy',
          component: PrivacyPolicyPage,
          meta: {
            title: '개인정보 처리방침',
          },
        },
        {
          path: 'terms',
          name: 'terms',
          component: TermsPage,
          meta: {
            title: '이용약관',
          },
        },
        // 어느 라우트와도 안 맞을 때. 없으면 RouterView 가 아무것도 안 그려서
        // 오류라는 것조차 안 보이는 흰 화면이 된다
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: NotFoundPage,
          meta: {
            title: '페이지를 찾을 수 없습니다',
            robots: 'noindex,follow',
          },
        },
      ],
    },
    // 로그인 화면은 관리자 레이아웃 밖에 둔다.
    // 안에 두면 로그인하지 않은 사람에게 사이드바 메뉴가 먼저 보인다
    {
      path: '/admin/login',
      name: 'admin-login',
      component: AdminLoginPage,
      meta: {
        title: '관리자 로그인',
        robots: 'noindex,follow',
      },
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: {
        robots: 'noindex,follow',
        // 이 아래 화면은 전부 로그인이 필요하다
        requiresAdmin: true,
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
          path: 'images',
          name: 'admin-images',
          component: AdminImagePage,
          meta: {
            title: '이미지 정리',
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
        {
          path: 'comments',
          name: 'admin-comments',
          component: AdminCommentPage,
          meta: {
            title: '댓글 관리',
            group: '콘텐츠',
          },
        },
        {
          path: 'members',
          name: 'admin-members',
          component: AdminMemberPage,
          meta: {
            title: '회원 관리',
            group: '운영',
          },
        },
        {
          path: 'codes',
          name: 'admin-common-codes',
          component: AdminCommonCodePage,
          meta: {
            title: '공통 코드',
            group: '운영',
          },
        },
        {
          path: 'home',
          name: 'admin-home',
          component: AdminHomeSettingsPage,
          meta: {
            title: '대문 설정',
            group: '블로그',
          },
        },
      ],
    },
  ],
});

/**
 * 관리자 화면에 들어가기 전에 세션을 확인한다.
 *
 * 이건 편의 장치다. 진짜 차단은 서버가 한다 — 가드를 우회해도 API 가 401 을 준다.
 * 가드가 하는 일은 "이미 끝난 로그인인데 빈 화면과 에러만 보는" 상황을 없애는 것이다.
 */
router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) {
    return true;
  }

  if (await ensureAdminSession()) {
    return true;
  }

  // 로그인한 뒤 원래 가려던 곳으로 보내기 위해 경로를 넘긴다
  return { name: 'admin-login', query: { redirect: to.fullPath } };
});

/**
 * 화면을 옮길 때마다 제목과 공유용 메타를 맞춘다.
 *
 * 글 상세는 여기서 제목을 알 수 없다. 라우트에는 글 번호만 있고 제목은 API 응답에
 * 들어 있다. 그래서 그 화면은 글을 받은 뒤에 직접 다시 부른다.
 *
 * 글 주소로 처음 들어온 경우에는 서버가 첫 HTML에 이미 그 글의 메타를 넣었다. 여기서 덮으면
 * 글 API 가 늦거나 실패했을 때 검색엔진이 보는 화면에 엉뚱한 제목이 남는다.
 * 앱 안에서 옮겨 온 경우에도 불러오는 동안 noindex 를 넣지 않는다 — 실패하면 그대로 남는다.
 */
router.afterEach((to, from) => {
  if (to.name === 'post-detail') {
    if (from !== START_LOCATION) {
      applyDocumentMeta({ title: '글 불러오는 중', path: `/posts/${to.params.id}` });
    }
    return;
  }

  applyDocumentMeta({ title: to.meta.title, path: to.fullPath, robots: to.meta.robots });
});

/**
 * 세션이 도중에 끊긴 경우. 화면을 열어 둔 채 세션만 만료되는 일이 실제로 자주 있다
 * (서버 재시작, 세션 만료). 가드는 화면을 옮길 때만 도니까 여기서 따로 받는다.
 */
onUnauthorized(() => {
  clearAdminSession();
  clearMemberSession();

  const current = router.currentRoute.value;

  if (current.meta.requiresAdmin) {
    router.replace({ name: 'admin-login', query: { redirect: current.fullPath } });
  }
});

export default router;
