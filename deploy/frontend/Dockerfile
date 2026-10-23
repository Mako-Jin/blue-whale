# ============================================================
# 蓝鲸前端 - pnpm + turbo + Nginx 多阶段构建
# 构建上下文：仓库根目录（前端 workspace 位于 frontend/）
#   docker build -f deploy/frontend/Dockerfile -t bluewhale/whale-web .
# ============================================================

ARG NODE_IMAGE=node:24-alpine
ARG NGINX_IMAGE=nginx:alpine

# ---- Stage 1: Build ----
FROM ${NODE_IMAGE} AS builder

# 应用名（frontend/<app>/package.json 的 name），默认主控台
ARG APP_NAME=whale-admin
# 应用目录（相对 frontend/，包名与目录名不一定相同）
ARG APP_DIR=admin

ENV PNPM_HOME="/pnpm" \
    PATH="/pnpm:$PATH" \
    TURBO_TELEMETRY_DISABLED=1 \
    CI=true

# Node 24 自带 corepack，pnpm 版本由 package.json 的 packageManager 字段锁定
RUN corepack enable

WORKDIR /build

COPY frontend ./

# 全量安装依赖（含 devDependencies，vite/tsc 构建需要）
RUN pnpm install --frozen-lockfile

# 构建目标应用及其 workspace 依赖（@bluewhale/whale-ui 等需先构建）
# 注意：COPY frontend ./ 拷贝的是目录内容，workspace 直接位于 /build
RUN pnpm turbo run build --filter="${APP_NAME}..." \
    && mkdir -p /out \
    && cp -r "${APP_DIR}/dist/." /out/

# ---- Stage 2: Runtime ----
FROM ${NGINX_IMAGE} AS runner

LABEL org.opencontainers.image.title="bluewhale-web"
LABEL org.opencontainers.image.description="蓝鲸前端 SPA"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

COPY --from=builder /out /usr/share/nginx/html

# 精简版 nginx 配置：仅做 SPA 回退与静态缓存；
# 生产环境由 infrastructure/docker/nginx/conf.d（HTTPS + 反代）覆盖
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen       80;
    server_name  _;

    root   /usr/share/nginx/html;
    index  index.html;

    location = /healthz {
        access_log off;
        add_header Content-Type text/plain;
        return 200 'ok';
    }

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
