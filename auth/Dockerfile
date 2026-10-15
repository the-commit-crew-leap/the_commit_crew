# Build stage
FROM node:24-alpine AS builder

WORKDIR /build

# Copy all auth files
COPY auth ./

# Install all dependencies (including dev for build)
RUN npm ci

# Build
RUN npm run build

# Runtime stage
FROM node:24-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy built application
COPY --from=builder /build/dist ./dist
COPY --from=builder /build/package*.json ./

# Install only production dependencies
RUN npm ci --only=production

# Non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 3000

CMD ["node", "dist/main.js"]