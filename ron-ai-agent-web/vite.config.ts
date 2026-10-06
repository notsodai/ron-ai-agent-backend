import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

console.log('===== RON VITE CONFIG LOADED =====')

export default defineConfig({
    plugins: [
        vue()
    ],

    server: {
        host: 'localhost',
        port: 5173,

        proxy: {
            /*
             * 后端 application.yml 里：
             *     server.servlet.context-path = /api
             * 所以后端真实接口形如：
             *     http://localhost:8081/api/ai/ronmanus/chat
             *
             * 这里必须把 /api 前缀【原样】转发给后端，
             * 一旦 rewrite 掉 /api，后端收到 /ai/xxx 会直接 404。
             */
            '/api': {
                target: 'http://localhost:8081',
                changeOrigin: true,
                secure: false,

                rewrite: (path: string) => {
                    console.log(
                        '[Vite Proxy]',
                        path,
                        '->',
                        `http://localhost:8081${path}`
                    )

                    return path
                }
            }
        }
    }
})