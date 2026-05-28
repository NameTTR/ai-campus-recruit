import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = process.env.VITE_API_PROXY_TARGET || env.VITE_API_PROXY_TARGET
  const aiTarget = process.env.VITE_AI_PROXY_TARGET || env.VITE_AI_PROXY_TARGET

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        ...(aiTarget
          ? {
              '/api/ai': {
                target: aiTarget,
                changeOrigin: true
              }
            }
          : {}),
        ...(apiTarget
          ? {
              '/api': {
                target: apiTarget,
                changeOrigin: true
              }
            }
          : {})
      }
    }
  }
})
