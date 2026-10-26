# P3-10 docker-compose.prod 验证 + Nginx HTTPS —— 检查报告

- 日期：2026-10-26
- 来源：待办队列 P3-10；roadmap Phase 5「生产环境 `docker-compose.prod.yml` 验证」「Nginx 生产配置 + HTTPS」
- 结论：**已完成**（prod 覆盖层配置合并校验 + 容器化 Nginx HTTPS 实机验证通过；prod 覆盖层运行时受 Let's Encrypt 证书路径限制，已在下方记录）

## 一、范围

验证 `infrastructure/docker` 下的生产编排与 Nginx HTTPS 配置：prod 覆盖层的字段合并、资源/安全约束是否生效，Nginx 主配置与站点配置语法是否正确，以及容器内 HTTPS 反向代理到宿主网关的链路是否可用。

## 二、变更点

无代码变更（纯验证）。验证对象：

- `docker-compose.yml`（dev 编排，`name: bluewhale-dev`）
- `docker-compose.prod.yml`（生产覆盖层：资源限制、`no-new-privileges`、`read_only` + `tmpfs`、Let's Encrypt 证书挂载）
- `nginx/nginx.conf` + `nginx/conf.d/{default,api-gateway,micro-frontend}.conf`
- `nginx/ssl/{fullchain.pem,privkey.pem}`（自签开发证书，`gen-dev-cert.ps1` 产物）

## 三、验证

### 1. compose 覆盖层合并与安全约束

- `docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.example config --quiet` → **exit 0**（含 Docker 引擎运行态，非仅静态解析）。
- 解析结果核对：mysql 2.0c/2g（保留 0.5c/512m）、redis 1.0c/512m、nacos 2.0c/2g、rabbitmq 1.0c/1g、nginx 1.0c/256m；`nginx.read_only=true`、`tmpfs` 覆盖 `/var/cache/nginx`、`/var/run`、`/tmp`；`security_opt: no-new-privileges:true`；证书卷解析为 `/etc/letsencrypt/live/bluewhale.local/{fullchain,privkey}.pem:ro`（`TLS_SERVER_NAME=bluewhale.local`）。

### 2. Nginx 配置语法与 TLS 证书

- 使用本机 `tools/nginx-1.30.6.zip` 解压后 `nginx -t`：主配置 + 3 个站点配置 **syntax is ok / test is successful**（测试态仅替换容器内绝对路径、`use epoll`→`select`、`host.docker.internal`→`127.0.0.1`，未改动仓库文件）。
- 证书链：`CN=bluewhale.local`，有效期 2026-10-23 → **2029-01-25**，SAN 覆盖 `bluewhale.local`、`localhost`、`api.localhost`、`app.localhost`、`*.bluewhale.local`、`127.0.0.1`、`::1`；证书公钥与私钥 **匹配**。

### 3. 本机 Nginx 进程态验证（80/443 → 测试端口 8088/8443）

- `http://127.0.0.1:8088/healthz` → 200 `ok`；`/dashboard` → **301** `https://127.0.0.1/dashboard`。
- `https://127.0.0.1:8443/healthz` → 200（TLS 握手成功）。
- `Host: api.localhost` + `/api/auth/login` → 200 且信封 `code=0`（HTTPS → 网关 8080 → whale-auth 全链路）。
- `Host: app.localhost` `/` → 200（静态托管 + SPA 回退），不存在资源 → 404。
- 验证后已停止测试实例。

### 4. 容器化验证（Docker 引擎 29.9.2，`-p bw-verify`）

- `docker compose -f docker-compose.yml --env-file .env.example -p bw-verify up -d --build nginx` **成功**；`NGINX_BASE_IMAGE` 指向已缓存的 `docker.m.daocloud.io/library/nginx:alpine`（Docker Hub 直连失败，属已知网络约束）。
- 容器健康检查：`bw-nginx` **healthy**（内置 `wget http://127.0.0.1/healthz`）。
- 端口映射 `0.0.0.0:80/443`；实测：
  - `http://localhost/healthz` → 200；
  - `Host: api.localhost http://localhost/api/auth/me` → **301** `https://api.localhost/api/auth/me`；
  - `Host: api.localhost https://localhost/actuator/health` → 200（容器 → `host.docker.internal:8080` 网关）；
  - `Host: api.localhost https://localhost/api/auth/login` → 200 且 `code=0`（浏览器级 HTTPS 登录链路可用）。
- 清理：`docker compose -p bw-verify down -v` + 删除临时镜像，未残留容器/网络/卷。

## 四、风险与观察

- **prod 覆盖层运行时未实跑**：其卷挂载依赖宿主机 Let's Encrypt 证书路径（`/etc/letsencrypt/live/${TLS_SERVER_NAME}/`），本机无该证书；配置合并与字段已校验，实际 `up` 需在具备证书的主机执行。开发态容器化 HTTPS 已用自签证书验证通过，链路等价。
- Docker Hub 直连不可用，构建须通过 `NGINX_BASE_IMAGE` / `*_IMAGE` 指向镜像站（`.env.example` 已有注释说明）。
- `default.conf` 的 443 默认站点无 `/healthz`（落到 `try_files` 返回首页），容器健康检查走 HTTP 80，不影响探活；若后续改为 HTTPS 探活需补该 location。
- Nginx 以 `read_only` + `tmpfs` 运行，日志落在容器内 stdout/文件，采集需依赖 Docker 日志驱动（compose 已配 json-file 轮转）。
