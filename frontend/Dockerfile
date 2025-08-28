FROM cgr.dev/chainguard/node:latest-dev AS builder

# Create app directory with proper permissions
RUN mkdir -p /app && \
    chown -R node:node /app

WORKDIR /app

# Switch to node user
USER node

COPY --chown=node:node package*.json ./
COPY --chown=node:node .npmrc ./
RUN npm ci

COPY --chown=node:node . .

# Accept build arguments (minimized to what's actually used)
ARG NODE_ENV=production

# Create start script for the SSR server (auth handled by SSR)
RUN echo '#!/bin/sh' > start-servers.sh && \
    echo 'exec node dist/frontend/server/server.mjs' >> start-servers.sh && \
    chmod +x start-servers.sh

# Build with environment variables
ENV NODE_ENV=$NODE_ENV
# Disable Angular cache and set memory limit
ENV NG_PERSISTENT_BUILD_CACHE=0
ENV NODE_OPTIONS="--max-old-space-size=4096"
RUN npm run build

FROM cgr.dev/chainguard/node:latest

# Create app directory with proper permissions
USER root
RUN mkdir -p /app && \
    chown -R node:node /app

USER node
WORKDIR /app

COPY --from=builder --chown=node:node /app/dist ./dist
COPY --from=builder --chown=node:node /app/server ./server
COPY --from=builder --chown=node:node /app/start-servers.sh ./
COPY --from=builder --chown=node:node /app/package*.json ./
COPY --from=builder --chown=node:node /app/.npmrc ./

RUN npm ci --omit=dev

ARG FRONTEND_PORT=4000

EXPOSE ${FRONTEND_PORT}

ENTRYPOINT []
CMD ["./start-servers.sh"] 