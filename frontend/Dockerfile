# ==========================================================
# LegacyGuard 前端镜像（多阶段构建）
#
# 构建产物是纯静态文件，由 nginx 托管并反向代理 /api 到后端。
# 这样生产环境前后端同源，不需要再处理跨域。
# ==========================================================

# ---------- 阶段一：构建 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 容器内没有宿主的 npm 配置，显式指定国内镜像，避免构建时卡在 registry.npmjs.org
RUN npm config set registry https://registry.npmmirror.com

# 先复制依赖清单，让 npm ci 单独成层 —— 依赖没变时这层可以命中缓存
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 阶段二：运行 ----------
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
