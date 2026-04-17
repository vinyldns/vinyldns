import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { hmacProxyPlugin } from './vite-plugin-hmac-proxy';
import { readFileSync, createReadStream, existsSync } from 'fs';

// Read the canonical version from version.sbt at the repo root.
// version.sbt contains a single line: version in ThisBuild := "x.y.z"
const versionSbt = readFileSync(
  path.resolve(__dirname, '../../version.sbt'),
  'utf-8'
);
const versionMatch = versionSbt.match(/:=\s*"([^"]+)"/);
const appVersion = versionMatch ? versionMatch[1] : 'unknown';

// https://vitejs.dev/config/
export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  plugins: [
    react(),
    // Signs all VinylDNS API requests with AWS V4 HMAC (service=VinylDNS,
    // region=us-east-1) and proxies them to http://localhost:9000.
    // This replaces the signing work the Play portal used to do.
    hmacProxyPlugin(),
    // Serve images directly from the microsite img directory under /img/*
    {
      name: 'serve-microsite-img',
      configureServer(server) {
        const micrositeImgDir = path.resolve(
          __dirname,
          '../docs/src/main/resources/microsite/img'
        );
        const mimeTypes: Record<string, string> = {
          '.png': 'image/png',
          '.jpg': 'image/jpeg',
          '.jpeg': 'image/jpeg',
          '.svg': 'image/svg+xml',
          '.gif': 'image/gif',
          '.webp': 'image/webp',
        };
        server.middlewares.use('/img', (req, res, next) => {
          const filePath = path.join(micrositeImgDir, req.url ?? '/');
          if (existsSync(filePath)) {
            const mime = mimeTypes[path.extname(filePath)] ?? 'application/octet-stream';
            res.setHeader('Content-Type', mime);
            createReadStream(filePath).pipe(res);
          } else {
            next();
          }
        });
      },
    },
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
