import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/uploads': 'http://127.0.0.1:8080',
    },
  },
  plugins: [vue()],
});
