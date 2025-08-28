const express = require('express');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');

// Only load .env.local if it exists and we're not in Docker
if (!process.env.NODE_ENV || process.env.NODE_ENV === 'development') {
  const envPath = path.join(process.cwd(), '.env.local');
  if (fs.existsSync(envPath)) {
    require('dotenv').config({ path: '.env.local' });
  }
}

const app = express();
app.use(express.json());

// Load environment variables - Docker env vars take precedence over .env.local
const AUTH_PASSWORD = process.env.AUTH_PASSWORD || getDefaultAuthPassword();
const JWT_SECRET = process.env.JWT_SECRET || getDefaultJwtSecret();

function getDefaultAuthPassword() {
  // In Docker, this should be set via docker-compose
  if (process.env.NODE_ENV === 'docker' || process.env.NODE_ENV === 'production') {
    throw new Error('AUTH_PASSWORD environment variable must be set.');
  }
  // For local development without Docker, require .env.local
  throw new Error('AUTH_PASSWORD must be set. Create a .env.local file with AUTH_PASSWORD=your-password');
}

function getDefaultJwtSecret() {
  // In production, JWT_SECRET environment variable should always be set
  if (process.env.NODE_ENV === 'production') {
    throw new Error('JWT_SECRET environment variable must be set in production');
  }
  return 'local-dev-secret-' + Date.now(); // Default for development only
}

// Auth endpoint
app.post('/api/auth', (req, res) => {
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
app.get('/api/verify', (req, res) => {
  const authHeader = req.headers.authorization;
  
  if (authHeader) {
    return res.status(200).json({ authenticated: true });
  }

  return res.status(401).json({ authenticated: false });
});

const PORT = process.env.AUTH_PORT || process.env.PORT || 3001;
app.listen(PORT, () => {
  // Structured log without secrets
  const logMessage = JSON.stringify({
    level: 'info',
    message: `Auth server running on port ${PORT}`,
    timestamp: new Date().toISOString(),
    source: 'auth-server'
  });
  console.log(logMessage);
});