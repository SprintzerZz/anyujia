import { defineConfig, searchForWorkspaceRoot } from 'vite';

const gatewayTarget = process.env.CHAT_GATEWAY_TARGET || 'http://127.0.0.1:18080';
const chatTarget = process.env.CHAT_SERVICE_TARGET || gatewayTarget;
const houseTarget = process.env.HOUSE_SERVICE_TARGET || gatewayTarget;
const fileTarget = process.env.FILE_SERVICE_TARGET || gatewayTarget;
const legalTarget = process.env.LEGAL_SERVICE_TARGET || process.env.LEGAL_GATEWAY_TARGET || gatewayTarget;

export default defineConfig({
  server: {
    host: '0.0.0.0',
    port: 5173,
    fs: {
      allow: [searchForWorkspaceRoot(process.cwd())],
    },
    proxy: {
      '/chat': {
        target: chatTarget,
        changeOrigin: true,
        ws: true,
      },
      '/house': {
        target: houseTarget,
        changeOrigin: true,
      },
      '/file': {
        target: fileTarget,
        changeOrigin: true,
      },
      '/legal': {
        target: legalTarget,
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
  },
});
