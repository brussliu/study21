/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@study21/web-shared/styles': fileURLToPath(
        new URL('../packages/web-shared/src/styles/design-tokens.css', import.meta.url)
      ),
      '@study21/web-shared': fileURLToPath(new URL('../packages/web-shared/src/index.ts', import.meta.url))
    }
  },
  server: {
    port: Number(process.env.PC_WEB_PORT ?? 5173),
    strictPort: true,
    proxy: {
      '/api/admin': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/user': { target: 'http://localhost:8082', changeOrigin: true }
    }
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts']
  }
})
