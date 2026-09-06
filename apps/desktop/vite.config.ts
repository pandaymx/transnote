import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

// Tauri 桌面壳（契约 §9.3）：Vite + React，页面复用 packages/core；端口 3001 避开 web dev。
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: {
    port: 3001,
    strictPort: true,
  },
  resolve: {
    alias: {
      '@transnote/schema': fileURLToPath(new URL('../../packages/schema/src/index.ts', import.meta.url)),
      '@transnote/api-client': fileURLToPath(new URL('../../packages/api-client/src/index.ts', import.meta.url)),
      '@transnote/core': fileURLToPath(new URL('../../packages/core/src/index.ts', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
