import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = process.env.VITE_API_PROXY_TARGET || env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  const aiTarget = process.env.VITE_AI_PROXY_TARGET || env.VITE_AI_PROXY_TARGET || apiTarget

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api/ai': {
          target: aiTarget,
          changeOrigin: true
        },
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    }
  }
})
