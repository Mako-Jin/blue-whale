# 后端服务容器化运行（本机开发）

把后端 32 个 Spring Boot 服务统一交由 compose 管理。MySQL 与 Nacos 仍跑宿主机，
Redis / RabbitMQ / EMQX 由 `infrastructure/docker/docker-compose.yml` 提供。

## 拓扑

```
宿主机  MySQL 3306 ────┐
宿主机  Nacos 8848 ────┤
                       ├── docker network: bluewhale-dev_bw-net
bw-redis    6379 ──────┤
bw-rabbitmq 5672/15672 ┤
bw-emqx     1883/18083 ┤
                       └── bw-gateway 8080 / bw-auth 8081 / bw-crm 9007 ...
```

所有对外端口只绑 `127.0.0.1`。容器注册到 Nacos 时登记的是容器 IP（172.18.0.x），
网关在同一网络内可直连，**宿主机不要用 172.18.x 访问**。

## 启动

```bash
# 1. 中间件（只起 redis / rabbitmq / emqx；mysql、nacos 端口与宿主机原生实例冲突）
cd F:/blue-whale/infrastructure/docker
docker compose up -d redis rabbitmq emqx

# 2. 宿主机 Nacos（见 .workbuddy/memory/MEMORY.md「起服手册」，必须带 loader.path 与 nacos.home）
#    确认 8848 已监听后再继续

# 3. 核心服务（默认 20 个）
cd F:/blue-whale/deploy/backend
docker compose -f docker-compose.services.yml up -d

# 4. 验证
docker ps --format '{{.Names}}\t{{.Status}}'
for p in 8080 8081 8086 9001 9004 9007; do
  curl -s "http://127.0.0.1:$p/actuator/health"; echo
done
```

## 服务分组：为什么 10 个服务默认不启动

Docker VM 上限 **8GB**（`~/.wslconfig`），20 个核心服务已占约 **7.2G**，余量不足 1G。
10 个扩展服务合计需 5~6G，**物理上无法与核心服务同时常驻**——硬塞进默认集会把引擎拖死
（历史事故：MemAvailable < 1G 时 `docker ps` 全部 500，只能重启 Docker Desktop）。

因此这 10 个服务全部纳入本编排文件统一管理，但按域分组、默认关闭：

| profile | 服务（端口） | 预算 | 起法 |
|---|---|---|---|
| （默认） | 核心 20 个 | ~7.2G | `up -d` |
| `ops` | alert(9103) / monitor(9102) | ~1.0G | `--profile ops up -d` |
| `collab` | im(8091) / mail(8099) / assistant(8087) | ~1.9G | `--profile collab up -d` |
| `aiot` | agent(8090) / iot(8088) / scene(8097) | ~1.7G | `--profile aiot up -d` |
| `biz` | pm(9005) / map(8100) | ~1.1G | `--profile biz up -d` |
| `ext` | 以上 10 个全部 | ~5.7G | `--profile ext up -d`（需先停核心服务） |

`music`(8096) / `party`(9009) 是演示模块，单独挂在 `demo` profile。

起扩展组前先腾内存。**不要手工挑服务停**，用换位脚本（按影响面排序停核心服务 → 起目标组 → 可回滚）：

```bash
scripts/dev/profile-swap.sh --status        # 余量 / 各组预算 / 当前可起哪组
scripts/dev/profile-swap.sh ops             # 停够核心服务后起 ops 组（默认最多停 2 个）
scripts/dev/profile-swap.sh collab --max-stop 3
scripts/dev/profile-swap.sh --restore       # 把上次停掉的核心服务拉回来（compose start，不重建）
```

停用顺序按「入站依赖数 + 调度任务数」从小到大排，脚本按此顺序自动挑：

| 顺序 | 服务 | 停用影响 |
|---|---|---|
| 1 | `mall` | 无入站依赖、无调度任务；期间 CRM 的 `order.push` / `order.reconcile` 两个任务失败 |
| 2 | `contract` | 无入站依赖、无调度任务；期间 CRM 的 `contract.sync` 任务失败 |
| 3 | `pay` | 只被 mall 引用；mall 已停时完全无损 |
| 4 | `audit` | 审计事件走 MQ 排队不丢，仅导出/清理任务暂停 |
| 5~8 | `video` / `lms` / `pdm` / `archive` | 各自的定时任务暂停，`misfire` 策略下恢复后补跑 |
| 9~10 | `doc` / `form` | 只被 agent / archive 引用，起 aiot、biz 组时脚本不会选它们 |

`gateway` / `auth` / `file` / `job` / `crm` / `erp` / `message` / `workflow` 永不入列（入站依赖 6~13 个）。

脚本判据：`MemAvailable ≥ 新增服务 mem_limit 合计 × 60% + 512MB 红线`，最多停 `--max-stop` 个（默认 2）。
停用记录落在 `.workbuddy/tmp/profile-swap.state`，`--restore` 用 `compose start` 拉回，不重建镜像。
停到上限仍不够时脚本退出码 2 并提示改起更小的组，**不要硬起**——MemAvailable < 1G 时引擎会整体失联。

**一次只起一组**：两组叠加必然超预算（ops+biz 已 2.1G）。

### 扩展服务的外部依赖

| 服务 | 依赖 | 缺失时的表现 |
|---|---|---|
| assistant / im（机器人） | `whale-ai` 内网 8092（**不在本仓库/编排内**） | 服务正常起，对话类接口返回上游不可用 |
| agent | 外部 LLM 端点（`WHALE_AGENT_LLM_BASE_URL` + `WHALE_AGENT_LLM_API_KEY`） | 知识库/检索可用，生成不可用 |
| mail | Mailcow（IMAP/SMTP） | 账号/文件夹可管理，收发信不可用；收信同步默认关闭 |
| iot | `bw-emqx`（infrastructure 编排内） | 设备管理可用，遥测上行不可用 |
| map | 腾讯/高德 API Key | 服务可起，底图/逆地理关闭 |
| alert | Alertmanager 入站令牌 `ALERT_WEBHOOK_TOKEN` | 默认值仅本地可用，非本机部署必须覆盖 |

## 重建镜像

镜像吃的是已构建好的 jar，不做 Maven 构建。用脚本一次搞定（覆盖 32 个服务）：

```bash
# 1. 打包（新增/改动的模块）
mvn -o -DskipTests -Dcheckstyle.skip=true -Dmaven.test.skip=true package

# 2. 构建镜像（默认全部；也可只传服务名）
scripts/dev/build-service-images.sh              # 全部
scripts/dev/build-service-images.sh crm pm mail  # 指定
scripts/dev/build-service-images.sh --list       # 看服务名与 jar 目录
```

脚本会：校验 VM 余量（< 1G 直接拒绝，避免拖死引擎）→ 把 jar 复制到
`deploy/backend/jars/<svc>/app.jar` → `docker build`。单独重建一个服务：

```bash
cd deploy/backend
docker compose -f docker-compose.services.yml up -d --force-recreate crm
```

context 只放一个 `app.jar`。放整个 `deploy/backend`（32×80MB≈2.5G）会把 buildkit 拖死。

## 关键配置（改之前先看这里的坑）

| 项 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL` | 只有 **auth** 与 **job** 需要。两者 yml 的 url 硬编码 `127.0.0.1`，容器内必须整条覆盖；其余服务用 `${MYSQL_HOST:127.0.0.1}` 占位符，给 `MYSQL_HOST` 即可 |
| Redis 密码 | gateway/auth 只支持 `REDIS_HOST/PORT`，**不支持密码**。因此 Redis 容器不启 `requirepass`，并且必须 `protected-mode no`（见 `infrastructure/docker/redis/redis.conf`） |
| 内存 | 统一 `mem_limit` + `JAVA_OPTS=-Xms128~192m -Xmx320~448m -XX:MaxMetaspaceSize=256~320m`，扩展服务单容器上限 500~800m |
| 健康检查 | 基础镜像 `eclipse-temurin:21-jre-alpine` 只有 `wget`，**没有 curl** |
| 镜像内不加 `RUN` | 本机 buildkit 在 RUN 层偶发 `rpc error: EOF`，时区靠 `ENV TZ` |
| MySQL 账号 | 宿主 CLI 用 `root@localhost`，容器走 `root@%`，密码不同，各自见 `.env` 与起服手册 |
| 扩展服务不写跨 profile 的 `depends_on` | compose 在依赖服务未启用时会拒绝起服；一律容器名直连 + 客户端重试 |

## Flyway：双线合并遗留环境自愈

2026-11-09 合并时删除了远程重复的 `V2__seed_crm_remote_jobs.sql` / `V3__audit_log.sql` /
`V4__seed_crm_sync_jobs.sql`。**应用过这三个脚本的环境不需要人工 `flyway repair`**：

- `FlywayLegacyRepairConfig`（whale-job）在 migrate 前检测历史表里的这三个脚本名，
  命中才删除对应行（含同版本失败残行），随后本地 V2/V3/V4 正常补跑；
- 补跑是必须的：远程 V2 只做种子，`job_executor` 表与 `job_info.executor_app`/`job_code`
  两列只存在于本地 V2，历史行占着版本号会让它们永远不执行；
- 未受影响的环境（新库、本地开发库）历史表里没有这三个脚本名，完全走 Flyway 默认路径。

`WHALE_FLYWAY_VALIDATE_ON_MIGRATE` 仍保留为逃生口，但自愈生效后无需再关校验。

## 排障

```bash
docker logs bw-<svc> --tail 50
docker inspect bw-<svc> --format 'Restart={{.RestartCount}} OOM={{.State.OOMKilled}} Health={{.State.Health.Status}}'
```

- `Invalid or corrupt jarfile /app/app.jar` → 镜像层损坏（源 jar 用 `unzip -t` 可验证），`docker build --no-cache` 重建。
- `RedisConnectionException` / 健康 DOWN 但进程活着 → 查 Redis `protected-mode`。
- 启动即退出且无 Java 异常 → 多半是数据源连不上，确认 `.env` 的 `MYSQL_HOST` 与密码。
- `Unknown database 'bluewhale_xxx'` → `infrastructure/docker/mysql/init/01-init-databases.sql` 未覆盖该库，补建后重启 MySQL 容器（或手工建库）。
- `OOMKilled=true` / 引擎整体失联 → VM 余量不足，停扩展组与演示模块，必要时 `docker-desktop desktop restart`。

## 相关文件

- `deploy/backend/Dockerfile.runtime` — 运行时镜像模板（`ARG JRE_IMAGE` 可换源）
- `deploy/backend/docker-compose.services.yml` — 32 服务编排（4 个扩展 profile + demo）
- `deploy/backend/docker-compose.verify.yml` — 多副本验证编排（只放第二副本）
- `scripts/dev/build-service-images.sh` — jar → 镜像构建脚本
- `scripts/dev/profile-swap.sh` — 扩展 profile 换位（停核心服务腾内存 → 起扩展组 → 可回滚）
- `deploy/backend/.env` — 凭据与主机名（已 gitignore）
- `deploy/backend/jars/<svc>/app.jar` — 构建上下文（`*.jar` 已 gitignore）
- `infrastructure/docker/` — 中间件编排、`.env`、redis 配置
