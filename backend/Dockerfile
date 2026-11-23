# Multi-stage Dockerfile for CryptoNote Fastify Backend
# Stage 1: Build stage
FROM node:20-slim AS builder

WORKDIR /app

# Install OpenSSL for Prisma engine binaries
RUN apt-get update -y && apt-get install -y openssl && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
COPY prisma ./prisma/

# Install all dependencies (including devDependencies for typescript and prisma)
RUN npm ci

COPY . .

# Generate Prisma Client and compile TypeScript to JavaScript
RUN npx prisma generate && npx tsc

# Stage 2: Production runner stage
FROM node:20-slim AS runner

WORKDIR /app

# Install OpenSSL and CA certificates for secure database connections (PostgreSQL TLS)
RUN apt-get update -y && apt-get install -y openssl ca-certificates && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
COPY prisma ./prisma/

# Install production dependencies and keep prisma CLI for release migrations
RUN npm ci --omit=dev && npm install prisma@^6.4.1 --save-dev

# Copy compiled dist and generated Prisma client from builder stage
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/node_modules/@prisma/client ./node_modules/@prisma/client

# Expose internal port
EXPOSE 3000

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=3000

CMD ["node", "dist/src/server.js"]
