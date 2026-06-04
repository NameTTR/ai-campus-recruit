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
    },
    build: {
      rollupOptions: {
        onwarn(warning, defaultHandler) {
          if (
            warning.code === 'INVALID_ANNOTATION' &&
            warning.id?.replaceAll('\\', '/').includes('/node_modules/@vueuse/core/dist/index.js')
          ) {
            return
          }

          defaultHandler(warning)
        },
        output: {
          manualChunks(id) {
            const normalizedId = id.replaceAll('\\', '/')

            if (
              normalizedId.includes('/node_modules/vue/') ||
              normalizedId.includes('/node_modules/vue-router/') ||
              normalizedId.includes('/node_modules/@vue/')
            ) {
              return 'vue-vendor'
            }

            if (normalizedId.includes('/node_modules/lucide-vue-next/')) {
              return 'icons-vendor'
            }

            if (normalizedId.includes('/node_modules/@element-plus/icons-vue/')) {
              return 'element-plus-icons'
            }

            if (normalizedId.includes('/node_modules/element-plus/')) {
              return 'element-plus-vendor'
            }

            if (normalizedId.includes('/node_modules/@vueuse/')) {
              return 'vueuse-vendor'
            }

            if (normalizedId.includes('/node_modules/')) {
              return 'vendor'
            }
          }
        }
      }
    }
  }
})
