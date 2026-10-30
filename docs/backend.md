# 后端开发指南

> 技术栈版本以 [backend/whale-parent/pom.xml](../backend/whale-parent/pom.xml) 为唯一事实源；服务/端口/库权威清单见 [service-catalog.md](service-catalog.md)。

## 模块结构

- Maven reactor 7 个聚合模块（parent / common / sdk / services / apps / ops / registry），完整服务全景见 [architecture.md](architecture.md)。
- 命名铁律：`模块目录 == artifactId == spring.application.name == Nacos 服务名 == whale-<域>`（[naming-conventions.md](naming-conventions.md)）。

## DDD 分层约定

每个服务按四层组织（`com.bluewhale.<域>.<层>`）：

| 层 | 包 | 职责 |
|---|---|---|
| interfaces | `…interfaces` | Controller / 请求响应 VO / 参数校验，不写业务规则 |
| application | `…application` | 用例编排、事务边界、跨聚合协调 |
| domain | `…domain` | 实体、值对象、领域服务、仓储接口、业务规则 |
| infrastructure | `…infrastructure` | MyBatis-Plus Mapper、仓储实现、外部客户端（RestClient 等） |

依赖方向：interfaces → application → domain ← infrastructure（架构测试参考 `whale-service-file-architecture-test`）。

## 构建与测试

```bash
cd backend
./mvnw -o -DskipTests -T 1C install     # 全量构建（离线，先装快照）
./mvnw -o -pl whale-services/whale-gateway test   # 定向测试
```

- JDK 21；`-pl` 定向测试前必须先 `install` 刷新本地仓库快照，否则报 `NoSuchMethod`（陈旧快照问题，见 devlog 2026-10-30）。
- Checkstyle 门禁：`config/checkstyle/whale-checkstyle.xml`，类头 `@author` 与 Javadoc 必填。

## 配置基线

- 端口外置：`server.port: ${SERVER_PORT:xxxx}`；注册中心 `NACOS_SERVER`；数据库 `MYSQL_HOST/PORT/USER/PASSWORD`。
- 连接池（T-13 基线）：各服务 `spring.datasource.hikari` 显式配置，环境变量 `DB_POOL_MAX_SIZE`（默认 5）/`DB_POOL_MIN_IDLE`（默认 2）等可覆盖；泄漏检测 60s。
- 内网接口：`/**/internal/**` 强制 `X-Internal-Token`（`bluewhale.internal-token.*`，环境变量 `WHALE_INTERNAL_TOKEN`）；非 dev 空令牌拒绝启动。
- 敏感默认值一律不留生产可用默认（`MYSQL_PASSWORD`、`JWT_SECRET`、`GRAFANA_ADMIN_PASSWORD` 等缺失时启动失败或守卫报错）。

## 门禁

未登记 REQ、设计未定稿、无检查报告（`reviews/`）、构建未通过 —— 任一不满足不得提交（[checklists/code-review-checklist.md](checklists/code-review-checklist.md)，命名符合性见 E15）。
