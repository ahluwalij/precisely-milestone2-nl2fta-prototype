import { CommonEngine } from '@angular/ssr';
import express from 'express';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createProxyMiddleware } from 'http-proxy-middleware';
import * as crypto from 'node:crypto';
import bootstrap from './main.server';

// The Express app is exported so that it can be used by serverless Functions.
export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const indexHtml = join(serverDistFolder, 'index.server.html');

  const commonEngine = new CommonEngine();

  // Get auth configuration from environment
  const AUTH_PASSWORD = process.env['AUTH_PASSWORD'] || 'development123';
  const JWT_SECRET = process.env['JWT_SECRET'] || 'dev-jwt-secret-key';

  // Add JSON body parser only for auth and local endpoints
  server.use('/api/auth', express.json());
  server.use('/api/verify', express.json());
  server.use('/local', express.json());

  // Auth endpoint
  server.post('/api/auth', (req, res) => {
    const { password } = req.body;

    if (!password) {
      return res.status(400).json({ error: 'Password is required' });
    }

    if (password === AUTH_PASSWORD) {
      const token = crypto
        .createHmac('sha256', JWT_SECRET)
        .update(`${Date.now()}-${Math.random()}`)
        .digest('hex');

      return res.status(200).json({ 
        success: true,
        sessionToken: token
      });
    }

    return res.status(401).json({ error: 'Invalid password' });
  });

  // Verify endpoint
  server.get('/api/verify', (req, res) => {
    const authHeader = req.headers.authorization;
    
    if (authHeader) {
      return res.status(200).json({ authenticated: true });
    }

    return res.status(401).json({ authenticated: false });
  });

  // Local endpoint: provide encrypted AWS defaults without exposing raw values in network
  server.post('/local/aws/defaults', (req, res) => {
    try {
      const { publicKeyPem } = req.body || {};
      const accessKeyId = process.env['AWS_ACCESS_KEY_ID'];
      const secretAccessKey = process.env['AWS_SECRET_ACCESS_KEY'];
      const region = process.env['AWS_REGION'] || 'us-east-1';
      const modelId = process.env['AWS_BEDROCK_MODEL_ID'];

      if (!accessKeyId || !secretAccessKey) {
        return res.status(200).json({ hasDefaults: false });
      }
      if (!publicKeyPem || typeof publicKeyPem !== 'string') {
        return res.status(400).json({ error: 'publicKeyPem is required' });
      }

      const bufferize = (s: string) => Buffer.from(s, 'utf8');
      const encryptWithClientKey = (plaintext: string) =>
        crypto.publicEncrypt(
          {
            key: publicKeyPem,
            padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
            oaepHash: 'sha256',
          },
          bufferize(plaintext)
        ).toString('base64');

      const encryptedAccessKeyId = encryptWithClientKey(accessKeyId);
      const encryptedSecretAccessKey = encryptWithClientKey(secretAccessKey);

      // Do NOT log secrets
      return res.status(200).json({
        hasDefaults: true,
        accessKeyId: encryptedAccessKeyId,
        secretAccessKey: encryptedSecretAccessKey,
        region,
        modelId,
      });
    } catch (e) {
      return res.status(500).json({ error: 'Failed to provide defaults' });
    }
  });

  // Configure API proxy with path filter (preserves full /api path)
  // Use 'backend' for Docker Compose, 'localhost' for ECS
  const isECS = process.env['ECS_CONTAINER_METADATA_URI'] || process.env['AWS_CONTAINER_CREDENTIALS_RELATIVE_URI'];
  const backendHost = isECS ? 'localhost' : 'backend';
  const apiProxy = createProxyMiddleware({
    target: process.env['API_HOST'] || `http://${backendHost}:${process.env['BACKEND_PORT'] || '8081'}`,
    changeOrigin: true,
    ws: true,
    pathFilter: ['/api/**', '!/api/auth', '!/api/verify'], // Exclude auth endpoints from proxy
  });

  // Apply proxy with path filter
  server.use(apiProxy);

  server.set('view engine', 'html');
  server.set('views', browserDistFolder);

  // Serve static files from /browser
  server.get(
    '*.*',
    express.static(browserDistFolder, {
      maxAge: '1y',
    })
  );

  // All regular routes use the Universal engine
  server.get('*', (req, res, next) => {
    const { protocol, originalUrl, headers } = req;

    commonEngine
      .render({
        bootstrap,
        documentFilePath: indexHtml,
        url: `${protocol}://${headers.host}${originalUrl}`,
        publicPath: browserDistFolder,
        providers: [
          { provide: 'REQUEST', useValue: req },
          { provide: 'RESPONSE', useValue: res },
        ],
      })
      .then(html => res.send(html))
      .catch(err => next(err));
  });

  return server;
}

function run(): void {
  const port = process.env['PORT'] || 4000;

  // Start up the Node server
  const server = app();
  server.listen(port, () => {
    // Use structured logging format for production
    const logMessage = JSON.stringify({
      level: 'info',
      message: `Node Express server listening on http://localhost:${port}`,
      timestamp: new Date().toISOString(),
      source: 'server',
    });
    console.log(logMessage);
    
    // Do not log secrets in any environment
  });
}

// Run the server when this module is executed directly
// In production, the compiled server.mjs will be run directly by Node.js
run();

export default bootstrap;
