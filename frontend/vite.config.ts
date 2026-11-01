import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Unit tests (`npm test`): see docs/testing.md.
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
    // A public placeholder, so code that needs an API address can be imported. Tests never reach a server.
    env: { VITE_API_BASE_URL: 'http://localhost:8080/api' },
    restoreMocks: true,
  },
})
