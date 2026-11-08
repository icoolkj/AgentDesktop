import { resolve } from 'node:path'
import { defineConfig, externalizeDepsPlugin } from 'electron-vite'
import vue from '@vitejs/plugin-vue'
import { loadEnv } from 'vite'

const sharedAliases = {
  '@renderer': resolve('src/renderer/src'),
  '@agent-desktop/shared-types': resolve('../shared/src/index.ts'),
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
  main: {
    plugins: [
      externalizeDepsPlugin({
        exclude: ['@agent-desktop/shared-types'],
      }),
    ],
    resolve: {
      alias: sharedAliases,
    },
  },
  preload: {
    plugins: [
      externalizeDepsPlugin({
        exclude: ['@agent-desktop/shared-types'],
      }),
    ],
    resolve: {
      alias: sharedAliases,
    },
  },
  renderer: {
    resolve: {
      alias: sharedAliases,
    },
    plugins: [vue()],
    define: {
      // build:e2e 使用 --mode e2e；构建期就注入标志，否则运行时才设置
      // VITE_E2E 不会生效（define 是编译期替换），window.__e2e 永远不会注册。
      'import.meta.env.VITE_E2E': JSON.stringify(
        mode === 'e2e' ? '1' : (env.VITE_E2E ?? process.env.VITE_E2E ?? ''),
      ),
    },
    server: {
      watch: {
        // Coalesce rapid workspace writes (Review accept, replace-all) into one HMR round.
        awaitWriteFinish: {
          stabilityThreshold: 300,
          pollInterval: 100,
        },
      },
    },
  },
  }
})
