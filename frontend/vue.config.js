const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    port: 8081,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 关键：禁用代理缓冲，确保 SSE 流式传输正常
        onProxyRes(proxyRes) {
          // 移除可能导致缓冲的头部
          delete proxyRes.headers['content-length']
          // 确保 SSE 响应不被缓冲
          if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
            proxyRes.headers['cache-control'] = 'no-cache'
            proxyRes.headers['x-accel-buffering'] = 'no'
          }
        }
      }
    }
  }
})
