const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  // Rewrite for /api/upload
  app.use(
    '/api/upload',
    createProxyMiddleware({
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: {
        '^/api/upload': '/upload',
      },
    })
  );


  app.use(
    '/api/download',
    createProxyMiddleware({
      target: 'http://localhost:8080',
      changeOrigin: true,
      pathRewrite: {
        '^/api/download': '/download',
      },
    })
  );
}