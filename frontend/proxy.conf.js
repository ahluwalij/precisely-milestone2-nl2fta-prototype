// Determine target based on environment
// In Docker containers, use service name; otherwise use localhost
const backendPort = process.env.BACKEND_PORT || '8081';
const target = process.env.API_HOST || (process.env.NODE_ENV === 'docker' ? `http://backend:${backendPort}` : `http://localhost:${backendPort}`);

const PROXY_CONFIG = {
  "/api/auth": {
    "target": "http://localhost:3001",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info"
  },
  "/api/verify": {
    "target": "http://localhost:3001",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info"
  },
  "/api/**": {
    "target": target,
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info",
    "onProxyReq": (proxyReq, req, res) => {
      // Proxy logging handled by http-proxy-middleware
    },
    "onError": (err, req, res) => {
      // Error logging handled by http-proxy-middleware
    }
  }
};

module.exports = PROXY_CONFIG; 