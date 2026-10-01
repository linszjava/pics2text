import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 15173,
    proxy: { '/api': 'http://localhost:18080' },
  },
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
