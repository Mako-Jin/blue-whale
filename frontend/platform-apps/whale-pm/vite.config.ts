import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

const microAppName = 'whale-pm';

export default defineConfig({
  plugins: [
    react(),
    qiankun(microAppName, {
      useDevMode: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8108,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
  },
  base: '/pm',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
