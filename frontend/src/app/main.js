import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { ensureMemberSession } from '../features/member/data/memberAuthStore';
import '../assets/scss/main.scss';

// 새로고침하면 화면이 들고 있던 로그인 정보가 사라진다. 세션 쿠키는 남아 있으므로
// 서버에 누구인지 한 번 물어본다. 기다리지 않는다 — 로그인하지 않은 방문자가 대부분이고,
// 글을 읽는 데 필요한 값이 아니라 첫 화면을 늦출 이유가 없다
ensureMemberSession();

createApp(App)
  .use(router)
  .mount('#app');
