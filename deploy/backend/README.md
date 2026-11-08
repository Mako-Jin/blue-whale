# 后端服务容器化运行（本机开发）

把 gateway / auth / pay / erp / oa / crm 六个 Spring Boot 服务做成容器，统一由 compose 管理。
MySQL 与 Nacos 仍跑宿主机，Redis / RabbitMQ 由 `infrastructure/docker/docker-compose.yml` 提供。

## 拓扑

```
宿主机  MySQL 3306 ────┐
宿主机  Nacos 8848 ────┤
                       ├── docker network: bluewhale-dev_bw-net
bw-redis    6379 ──────┤
bw-rabbitmq 5672/15672 ┤
                       └── bw-gateway 8080 / bw-auth 8081 / bw-pay 8086
                           bw-erp 9001 / bw-oa 9004 / bw-crm 9007
```

所有对外端口只绑 `127.0.0.1`。容器注册到 Nacos 时登记的是容器 IP（172.18.0.x），网关在同一网络内可直连，**宿主机不要用 172.18.x 访问**。

## 启动顺序

```bash
# 1. 中间件（只起 redis / rabbitmq；mysql、nacos 端口与宿主机原生实例冲突）
cd F:/blue-whale/infrastructure/docker
docker compose up -d redis rabbitmq

# 2. 宿主机 Nacos（见 .workbuddy/memory/MEMORY.md「起服手册」，必须带 loader.path 与 nacos.home）
#    确认 8848 已监听后再继续

# 3. 业务服务
cd F:/blue-whale/deploy/backend
docker compose -f docker-compose.services.yml up -d

# 4. 验证
docker ps --format '{{.Names}}\t{{.Status}}'
for p in 8080 8081 8086 9001 9004 9007; do
  curl -s "http://127.0.0.1:$p/actuator/health"; echo
done
```

## 重建镜像

镜像吃的是已构建好的 jar，不做 Maven 构建。先 `mvn package` 出新 jar，再复制到各上下文目录：

```bash
# 各服务的 jar 路径
#   gateway  backend/whale-services/whale-gateway/target/whale-gateway.jar
#   auth     backend/whale-services/whale-service-auth/whale-service-auth-starter/target/whale-service-auth.jar
#   pay      backend/whale-services/whale-pay/target/whale-pay.jar
#   erp      backend/whale-apps/whale-erp/target/whale-erp.jar
#   oa       backend/whale-apps/whale-oa/target/whale-oa.jar
#   crm      backend/whale-apps/whale-crm/target/whale-crm.jar

cp backend/whale-apps/whale-crm/target/whale-crm.jar deploy/backend/jars/crm/app.jar

docker build -f deploy/backend/Dockerfile.runtime \
  -t bluewhale/whale-crm:dev deploy/backend/jars/crm

cd deploy/backend && docker compose -f docker-compose.services.yml up -d --force-recreate crm
```

context 只放一个 `app.jar`。放整个 `deploy/backend`（480MB×6）会把 buildkit 拖死。

## 关键配置（改之前先看这里的坑）

| 项 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL` | 只有 **auth** 需要。它的 yml 里 url 硬编码 `127.0.0.1`，容器内必须覆盖；pay/erp/oa/crm 用 `${MYSQL_HOST:127.0.0.1}` 占位符，给 `MYSQL_HOST` 即可 |
| Redis 密码 | gateway/auth 只支持 `REDIS_HOST/PORT`，**不支持密码**。因此 Redis 容器不启 `requirepass`，并且必须 `protected-mode no`（见 `infrastructure/docker/redis/redis.conf`），否则无密码 + `bind 0.0.0.0` 会拒绝跨容器连接 |
| 内存 | `.wslconfig` 限制 Docker VM 5.8G。6 服务统一 `mem_limit 900m` + `JAVA_OPTS=-Xms192m -Xmx512m -XX:MaxMetaspaceSize=256m`，实测合计 2.45G |
| 健康检查 | 基础镜像 `eclipse-temurin:21-jre-alpine` 只有 `wget`，**没有 curl** |
| 镜像内不加 `RUN` | 本机 buildkit 在 RUN 层偶发 `rpc error: EOF`，时区靠 `ENV TZ` |
| MySQL 账号 | 宿主 CLI 用 `root@localhost`，容器走 `root@%`，密码不同，各自见 `.env` 与起服手册 |

## 排障

```bash
docker logs bw-<svc> --tail 50
docker inspect bw-<svc> --format 'Restart={{.RestartCount}} OOM={{.State.OOMKilled}} Health={{.State.Health.Status}}'
```

- `Invalid or corrupt jarfile /app/app.jar` → 镜像层损坏（源 jar 用 `unzip -t` 可验证），`docker build --no-cache` 重建。
- `RedisConnectionException` / 健康 DOWN 但进程活着 → 查 Redis `protected-mode`。
- 启动即退出且无 Java 异常 → 多半是数据源连不上，确认 `.env` 的 `MYSQL_HOST` 与密码。

## 相关文件

- `deploy/backend/Dockerfile.runtime` — 运行时镜像模板（`ARG JRE_IMAGE` 可换源）
- `deploy/backend/docker-compose.services.yml` — 六服务编排
- `deploy/backend/.env` — 凭据与主机名（已 gitignore）
- `deploy/backend/jars/<svc>/app.jar` — 构建上下文（已 gitignore）
- `infrastructure/docker/` — 中间件编排、`.env`、redis 配置
