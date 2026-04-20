import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { hmacProxyPlugin } from './vite-plugin-hmac-proxy';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Signs all VinylDNS API requests with AWS V4 HMAC (service=VinylDNS,
    // region=us-east-1) and proxies them to http://localhost:9000.
    // This replaces the signing work the Play portal used to do.
    hmacProxyPlugin(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 9001,
    strictPort: true,   // fail hard rather than silently falling back to 9002+
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    coverage: {
      reporter: ['text', 'json', 'html'],
    },
  },
});
