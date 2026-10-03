import { createApp, h, nextTick, ref } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import AdminBlockEditor from '../src/features/admin/components/AdminBlockEditor.vue';
import CommentSection from '../src/features/post/components/CommentSection.vue';
import AppToast from '../src/shared/components/AppToast.vue';
import { useMemberAuth } from '../src/features/member/data/memberAuthStore';
import { setCsrfToken } from '../src/shared/api/blogApiClient';
import '../src/assets/scss/main.scss';
import '../src/assets/scss/admin.scss';

const mode = ref('editor');
const markdown = ref('첫 문단\n\n둘째 문단\n\n셋째 문단');
const postId = ref(1);
const loading = ref(false);
const comments = ref({ total: 0, items: [], hasNext: false, maxLength: 1000 });
const commentSection = ref(null);
const { member } = useMemberAuth();
member.value = { id: 1, nickname: '테스트 회원', role: 'USER' };
setCsrfToken({ headerName: 'X-CSRF-TOKEN', token: 'fixture-token' });
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { render: () => null } },
    { path: '/login', name: 'member-login', component: { render: () => null } },
  ],
});
await router.push('/');
const app = createApp({
  setup: () => () => h('main', { style: 'max-width:900px;margin:24px auto;padding:16px;' }, [
    mode.value === 'editor'
      ? h(AdminBlockEditor, { modelValue: markdown.value, 'onUpdate:modelValue': value => { markdown.value = value; } })
      : h(CommentSection, { ref: commentSection, postId: postId.value, comments: comments.value, initialLoading: loading.value }),
    h(AppToast),
  ]),
});
app.use(router).mount('#fixture');
window.fixture = {
  async editor(value) { markdown.value = value; mode.value = 'editor'; await nextTick(); },
  async comments(value, initialLoading = false, id = 1) {
    postId.value = id;
    comments.value = value;
    loading.value = initialLoading;
    mode.value = 'comments';
    await nextTick();
  },
  async loading(value) { loading.value = value; await nextTick(); },
  async member(value) { member.value = value; await nextTick(); },
  focusComment: () => commentSection.value.focusCommentInput(),
  markdown: () => markdown.value,
  route: () => router.currentRoute.value.name,
  unmount: () => app.unmount(),
};
