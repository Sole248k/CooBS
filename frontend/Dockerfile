# ==============================================================================
# Multi-stage Dockerfile for the CooBS staff console
# Stage 1 builds the React app; stage 2 serves the result with Nginx.
# The image carries its own build, so nobody has to run npm on their machine first.
# ==============================================================================

# Stage 1: build the static files
FROM node:22-alpine AS builder
WORKDIR /app

# Dependencies first, so this layer is reused until package-lock.json changes
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# Stage 2: serve them
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
