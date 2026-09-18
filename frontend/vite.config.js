import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/uploads': 'http://127.0.0.1:8080',
      // 소셜 로그인은 브라우저가 통째로 이동하는 경로다. 여기를 안 열면
      // 로그인 버튼이 Vite 개발 서버에 걸려 404 가 난다 (운영은 Nginx 가 /api 처럼 넘긴다)
      '/oauth2': 'http://127.0.0.1:8080',
      '/login/oauth2': 'http://127.0.0.1:8080',
    },
  },
  plugins: [vue()],
});
