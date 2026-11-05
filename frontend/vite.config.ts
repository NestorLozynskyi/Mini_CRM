/// <reference types="vitest/config" />
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { svelteTesting } from '@testing-library/svelte/vite'
import { defineConfig } from 'vite'

// Dev-сервер проксує /api на бекенд (за замовчуванням http://localhost:8080).
const backendUrl = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [svelte(), svelteTesting()],
  server: {
    port: 5173,
    proxy: { '/api': { target: backendUrl, changeOrigin: false } },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/**/*.test.ts'],
  },
})
