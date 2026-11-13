# ============================================================
# 蓝鲸前端 - pnpm + turbo + Nginx 多阶段构建
# 构建上下文：仓库根目录（前端 workspace 位于 whale-frontend/）
#   docker build -f deploy/frontend/Dockerfile -t bluewhale/whale-web .
# ============================================================

ARG NODE_IMAGE=node:24-alpine
ARG NGINX_IMAGE=nginx:alpine

# ---- Stage 1: Build ----
FROM ${NODE_IMAGE} AS builder

# 应用名（whale-frontend/<app>/package.json 的 name），默认主控台
ARG APP_NAME=whale-admin
# 应用目录（相对 whale-frontend/，包名与目录名不一定相同）
ARG APP_DIR=admin

ENV PNPM_HOME="/pnpm" \
    PATH="/pnpm:$PATH" \
    TURBO_TELEMETRY_DISABLED=1 \
    CI=true

# Node 24 自带 corepack，pnpm 版本由 package.json 的 packageManager 字段锁定
RUN corepack enable

WORKDIR /build

# 拷贝前端 workspace 内容到 /build。目录名是 whale-frontend/（历史文档/注释里的 frontend/ 是重命名前的旧名），
# 写错会以 "failed to calculate checksum … \"/frontend\": not found" 形式在 buildx 阶段失败
COPY whale-frontend ./

# 全量安装依赖（含 devDependencies，vite/tsc 构建需要）
RUN pnpm install --frozen-lockfile

# 构建目标应用及其 workspace 依赖（@bluewhale/whale-ui 等需先构建）
# 注意：COPY whale-frontend ./ 拷贝的是目录内容，workspace 直接位于 /build
RUN pnpm turbo run build --filter="${APP_NAME}..." \
    && mkdir -p /out \
    && cp -r "${APP_DIR}/dist/." /out/

# ---- Stage 2: Runtime ----
FROM ${NGINX_IMAGE} AS runner

LABEL org.opencontainers.image.title="bluewhale-web"
LABEL org.opencontainers.image.description="蓝鲸前端 SPA"
LABEL org.opencontainers.image.vendor="BlueWhale"

# 站点挂载前缀：主应用为 /，qiankun 子应用取其 vite base（如 /contract、/erp）。
# 必须与构建产物中的绝对资源路径一致，否则子应用 assets 会 404。
ARG APP_BASE=/

ENV TZ=Asia/Shanghai \
    APP_BASE=${APP_BASE} \
    WEB_ROOT=/usr/share/nginx/html

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

# 产物落到 base 对应子目录：/ → 站点根；/contract → /usr/share/nginx/html/contract
# 同时移除 nginx 镜像自带欢迎页，避免子应用镜像在 / 返回默认页
# 按 APP_BASE 选择站点根配置或前缀配置（前缀配置中的 __APP_BASE__ 由 sed 替换）
COPY --from=builder /out /opt/app-dist
COPY deploy/frontend/nginx/root.conf /tmp/nginx-root.conf
COPY deploy/frontend/nginx/subapp.conf.template /tmp/nginx-subapp.conf.template
RUN set -eu; \
    rm -f "${WEB_ROOT}/index.html" "${WEB_ROOT}/50x.html"; \
    mkdir -p "${WEB_ROOT}${APP_BASE}"; \
    cp -a /opt/app-dist/. "${WEB_ROOT}${APP_BASE}/"; \
    rm -rf /opt/app-dist; \
    if [ "${APP_BASE}" = "/" ]; then conf=/tmp/nginx-root.conf; else conf=/tmp/nginx-subapp.conf.template; fi; \
    sed -e "s|__APP_BASE__|${APP_BASE}|g" "${conf}" > /etc/nginx/conf.d/default.conf; \
    rm -f /tmp/nginx-root.conf /tmp/nginx-subapp.conf.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
