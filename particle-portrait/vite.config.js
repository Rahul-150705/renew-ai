import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import glsl from 'vite-plugin-glsl';

export default defineConfig({
  plugins: [
    react(),
    glsl({
      include: ['**/*.vert', '**/*.frag', '**/*.glsl'],
      warnDuplicatedImports: true,
      defaultExtension: 'glsl',
      compress: false,
      watch: true,
    }),
  ],
  server: {
    port: 5173,
    open: true,
  },
  build: {
    target: 'esnext',
  },
});
