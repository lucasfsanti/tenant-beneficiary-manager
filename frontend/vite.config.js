import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    coverage: {
      // Always on, via the existing `npm test` (`vitest run`) — no separate coverage command to
      // remember (spec 008-test-coverage-tracking research.md §5).
      enabled: true,
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**'],
      // Report every file matching `include` above, even one no test imports at all — without
      // this, an untested file would simply be absent from the report instead of showing 0%,
      // which would make "100%" trivially satisfiable by omission.
      all: true,
      // Deliberately no `thresholds` here — a coverage shortfall must be visible in the report,
      // never a reason `npm test` fails (research.md §7, FR-004).
      exclude: [
        // src/main.js only wires up the Vue app instance, Pinia, and the router, then calls
        // app.mount('#app') — there is no #app element in the jsdom test environment, so
        // importing this file at all would attempt a real mount rather than exercise anything
        // this project owns. Mirrors the backend's main() exclusion (research.md §6).
        'src/main.js'
      ]
    }
  }
})
