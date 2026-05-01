# ===== Build =====
FROM node:21-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# ===== Production =====
FROM node:21-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
ENV NODE_ENV=production
ENV PORT=8086
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8086/health || exit 1
CMD ["node", "dist/index.js"]
