/** @type {import('next').NextConfig} */
const nextConfig = {
  experimental: {
    serverComponentsExternalPackages: [],
  },
  // Increase body size limit for large file uploads
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:8080/:path*',
      },
    ];
  },
  // Remove default body size limitations
  api: {
    bodyParser: {
      sizeLimit: '500mb',
    },
    responseLimit: false,
  },
}

module.exports = nextConfig
