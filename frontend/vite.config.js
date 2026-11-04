import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development, /api is forwarded to the Spring Boot backend, so the browser sees a single origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
});
