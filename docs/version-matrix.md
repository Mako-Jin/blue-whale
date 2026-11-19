# 版本兼容矩阵

> 定位：**集中视图**，不是版本事实源。版本以代码清单为准（后端 `whale-backend/whale-parent/pom.xml`、
> 前端 `whale-frontend/**/package.json`、基础设施 `infrastructure/docker/.env.example`）。
> 本表与事实源冲突时，**以事实源为准并立即修正本表**；`scripts/check-doc-consistency.ps1` 校验显式版本声明。
>
> 最后核对：2026-11-19

## 1. 后端

| 组件 | 版本 | 事实源 | 校验方式 |
|---|---|---|---|
| JDK | **21** | `pom.xml` → `java.version` / `maven.compiler.*` | `./mvnw -v`；`java -version` |
| Spring Boot | **4.0.8** | `pom.xml` → `spring-boot.version` | 父 POM 属性 + 本表声明 |
| Spring Cloud | **2025.1.2** | `pom.xml` → `spring-cloud.version` | 同上 |
| Spring Cloud Alibaba | **2025.1.0.0** | `pom.xml` → `spring-cloud-alibaba.version` | 同上 |
| MyBatis-Plus | 3.5.17 | `pom.xml` → `mybatis-plus.version` | 父 POM |
| Redisson | 4.8.0 | `pom.xml` → `redisson.version` | 父 POM |
| springdoc | 3.1.1 | `pom.xml` → `springdoc.version` | 父 POM |
| Knife4j | 4.5.0 | `pom.xml` → `knife4j.version` | 父 POM |
| JJWT | 0.12.5 | `pom.xml` → `jjwt.version` | 父 POM |
| Micrometer | 1.16.7 | `pom.xml` → `micrometer.version` | 父 POM |
| MySQL 驱动 | 8.3.0 | `pom.xml` → `mysql.version` | 父 POM |
| 达梦 JDBC | 8.1.3.140 | `pom.xml` → `dm-jdbc.version` | 父 POM |
| Kingbase JDBC | 8.6.0 | `pom.xml` → `kingbase8.version` | 父 POM |
| openGauss JDBC | 5.1.0-og | `pom.xml` → `opengauss-jdbc.version` | 父 POM |
| Oracle JDBC | 23.9.0.25.07 | `pom.xml` → `ojdbc11.version` | 父 POM |

## 2. 前端

| 组件 | 版本 | 事实源 | 校验方式 |
|---|---|---|---|
| Node.js | **24** | [frontend.md](frontend.md) 基线；本机 `node -v` | `node -v` |
| pnpm | **10.33.0** | `whale-frontend/package.json` → `packageManager` | `pnpm -v` / `corepack` |
| Turborepo | ^2.3.0 | `whale-frontend/package.json` | 根 `package.json` |
| TypeScript | ^5.6.3 | 根 / 各应用 `package.json` | 同上 |
| React / React DOM | ^19.0.0 | 各应用 `package.json`（如 `platform-apps/whale-crm`） | 应用级 `package.json` |
| Vite | ^6.0.0 | 各应用 `devDependencies` | 同上 |
| antd | ^5.21.0 | 各应用 `dependencies` | 同上 |
| react-router-dom | ^6.27.0 | 各应用 `dependencies` | 同上 |
| ESLint | ^9.39.5 | 根 `package.json` | 根 `package.json` |

> 前端版本按**各应用**独立声明（同一 workspace 内允许小版本差异）；新增应用须与上表大版本一致。

## 3. 基础设施（开发 compose）

| 组件 | 镜像 / 版本 | 事实源 |
|---|---|---|
| MySQL | `mysql:8.4`（驱动 8.3.0） | `infrastructure/docker/.env.example` → `MYSQL_BASE_IMAGE` |
| Redis | `redis:7-alpine` | `REDIS_BASE_IMAGE` |
| Nacos | `nacos/nacos-server:v3.2.4` | `NACOS_IMAGE` |
| RabbitMQ | `rabbitmq:4-management-alpine` | `RABBITMQ_IMAGE` |
| EMQX | `emqx/emqx:5.8` | `EMQX_IMAGE` |
| Prometheus | `prom/prometheus:v2.54.1` | `PROMETHEUS_IMAGE` |
| Alertmanager | `prom/alertmanager:v0.27.0` | `ALERTMANAGER_IMAGE` |
| Grafana | `grafana/grafana:11.2.0` | `GRAFANA_IMAGE` |
| Nginx | `nginx:alpine` | `NGINX_BASE_IMAGE` |

## 4. AI 侧

| 组件 | 版本 | 事实源 |
|---|---|---|
| Python | >= 3.11 | `ai/whale-ai/pyproject.toml` → `requires-python` |
| FastAPI | >= 0.115 | 同上 |
| LangGraph / LangChain Core | >= 0.2 / >= 0.3 | 同上 |
| pytest | >= 8（dev） | 同上 |

## 5. 兼容性约束

1. **JDK 21 是编译与运行基线**：父 POM `release=21`；低版本 JDK 无法构建。
2. **Spring Boot 4 使用 Jackson 3**（包名 `tools.jackson.*`）：公共组件与业务代码新增 JSON 处理时不得引入 Jackson 2 的 `com.fasterxml.jackson.databind` 混用；确有第三方依赖传递 Jackson 2 时隔离在适配层。
3. **Spring Cloud / Alibaba 版本必须成组升级**：Boot 4.0.x ↔ Cloud 2025.1.x ↔ Alibaba 2025.1.0.x，单独升级其一不做验证不得合并。
4. **前端 React 19 + antd 5 + Vite 6**：新应用不得回退到 React 18 / antd 4；共享包 `@bluewhale/*` 的 peer 约束以 workspace 根声明为准。
5. **数据库双轨**：开发环境 MySQL 8.4；国产化目标库（达梦 / Kingbase / openGauss / Oracle）驱动版本已锁定在父 POM，SQL 方言差异见 [design/multi-database.md](design/multi-database.md)。

## 6. 升级流程（强制）

1. 只改**事实源**（POM / package.json / compose 变量），不改本表先行"预告"。
2. 全量编译 + 定向单测：`./mvnw -o -f whale-backend/pom.xml -DskipTests install` → 受影响模块 `test`。
3. 前端：`pnpm install && pnpm run build && pnpm run lint`。
4. 更新本表「版本」列与「最后核对」日期；运行 `pwsh -File scripts/check-doc-consistency.ps1`。
5. 在 `docs/devlog/` 当日日志记录「升级原因 / 影响面 / 验证结果 / 回滚方式」。

## 7. 回滚

- **后端**：父 POM 属性回退到上一版本 → 重新 `install`；涉及 Flyway 的变更不随版本回退自动回滚，必须使用对应的补偿脚本。
- **前端**：`package.json` 版本回退 + `pnpm install --frozen-lockfile`；lockfile 一并提交。
- **基础设施**：compose 镜像变量回退后 `docker compose up -d`；数据卷不自动降级，跨大版本（如 MySQL 8.4 → 8.0）禁止直接回退，需导出/导入。
- 回滚后同样更新本表与 devlog，并保留失败版本的验证结论，避免重复踩坑。
