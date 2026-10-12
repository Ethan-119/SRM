# ============================================
# SRM 前端镜像（多阶段构建：Node 构建 → Nginx 托管）
# 构建上下文为仓库根目录，docker compose 里用 dockerfile: frontend/Dockerfile
# ============================================

# ---- 1. 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY frontend/ ./
RUN npm run build

# ---- 2. 运行阶段 ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
