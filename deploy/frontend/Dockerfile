# ============================================================
# 蓝鲸前端 - pnpm + turbo + Nginx 多阶段构建
# ============================================================

ARG NODE_VERSION=24
ARG NGINX_VERSION=alpine

# ---- Stage 1: Build ----
FROM node:${NODE_VERSION}-alpine AS builder

ENV PNPM_HOME="/pnpm" \
    PATH="/pnpm:$PATH" \
    TURBO_TELEMETRY_DISABLED=1

# pnpm 8+ 内置 corepack
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /build

# 拷贝 workspace 描述文件, 最大化缓存
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
# 如果是 turborepo, 根目录需要
COPY turbo.json ./

# 全量安装依赖 (prod + dev, 因为要 build)
RUN pnpm install --frozen-lockfile

# 拷贝所有包
COPY . .

# 构建生产包 (按需替换过滤规则)
RUN pnpm turbo run build --filter=@bluewhale/web...

# ---- Stage 2: Runtime ----
FROM nginx:${NGINX_VERSION} AS runner

LABEL org.opencontainers.image.title="bluewhale-web"
LABEL org.opencontainers.image.description="蓝鲸前端 SPA (qiankun 微前端)"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 默认 SPA 回退
COPY --from=builder /build/apps/web/dist /usr/share/nginx/html
# 如存在 qiankun 子应用
# COPY --from=builder /build/apps/sub-app/auth/dist /usr/share/nginx/html/sub-app/auth
# COPY --from=builder /build/apps/sub-app/workflow/dist /usr/share/nginx/html/sub-app/workflow

# 精简版 nginx 配置, 正式环境可由 infrastructure/docker/nginx/conf.d 覆盖
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen       80;
    server_name  _;

    root   /usr/share/nginx/html;
    index  index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(?:js|css|map|woff|woff2|ttf|eot|otf|jpg|jpeg|png|gif|ico|svg|webp)$ {
        expires 30d;
        access_log off;
        add_header Cache-Control "public";
    }
}
EOF

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
