# CyberLearnix website-service — Production Dockerfile
# Deploys server.js as a Kubernetes pod (website-service)
# Port: 8082 | Health: GET /api/health

FROM node:22-alpine

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy dependency manifests first for layer caching
COPY package.json package-lock.json ./

# Install production dependencies only
RUN npm ci --only=production

# Copy only the files server.js needs at runtime
COPY server.js ./
COPY api/ ./api/

# Ensure non-root user owns the app directory
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8082

ENV PORT=8082 \
    NODE_ENV=production

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8082/api/health || exit 1

CMD ["node", "server.js"]
