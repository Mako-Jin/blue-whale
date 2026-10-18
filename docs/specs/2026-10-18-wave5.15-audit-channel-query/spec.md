# Wave 5.15 · 审计服务 ①② 通道查询接口补齐（REQ-SEC-006）

> 状态：待实现 ｜ 规格先行 ｜ 归属：审计服务收尾（用户指令「继续完成审计服务」）
> 上一波：[Wave 5.14 前端日志 SDK](../2026-10-18-wave5.14-whale-log-sdk/spec.md)

---

## 一、需求项

**REQ-SEC-006（四通道日志采集）** 当前四张独立表（`api_log`/`outbound_log`/`frontend_log`/`behavior_log`）均已落库，但**查询能力不对称**：

| 通道 | 表 | 采集（写） | 查询（读） |
|---|---|---|---|
| ① 后端接口日志 | `api_log` | ✅ `ApiLogFilter` | ❌ **缺失** |
| ② 后端出站三方调用日志 | `outbound_log` | ✅ `OutboundLogInterceptor` | ❌ **缺失** |
| ③ 前端开发者日志 | `frontend_log` | ✅ `LogIngestController#frontendBatch` | ✅ `GET /audit/log/frontend/page` |
| ④ 前端用户行为埋点 | `behavior_log` | ✅ `LogIngestController#behaviorBatch` | ✅ `GET /audit/log/behavior/page` |

本波**只补齐 ①② 两条通道的分页查询接口**，使四通道「写—查」对称，闭合审计服务多通道可观测性缺口。

**范围（In Scope）**
- `dto/ApiLogQueryRequest`、`dto/OutboundLogQueryRequest`（分页 + 多维过滤，条件命中既有索引）
- `vo/ApiLogVO`、`vo/OutboundLogVO`（字段与实体一一对应）
- `service/ApiLogService` + `impl/ApiLogServiceImpl`、`service/OutboundLogService` + `impl/OutboundLogServiceImpl`（仅分页查询）
- 控制器端点：`GET /audit/log/api/page`、`GET /audit/log/outbound/page`（并入既有 `LogIngestController`，与 ③④ 查询端点并列）
- 单测：`ApiLogServiceImplTest`、`OutboundLogServiceImplTest`

**范围外（Out of Scope）**
- `@AuditLog(type=QUERY)` 元审计：**③④ 现有查询端点亦未标注**，本波保持四通道查询口径一致（均不做 `@AuditLog`），登记为残留项待统一
- AUTH-08 导出 / AUTH-09 保留归档 / AUTH-10 MQ 汇聚（用户本轮未选）
- 表结构调整、网关路由改造

---

## 二、契约（冻结）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/audit/log/api/page` | `@RequirePermission("sys:audit:log:list")` | 后端接口日志分页查询 |
| GET | `/audit/log/outbound/page` | `@RequirePermission("sys:audit:log:list")` | 出站三方调用日志分页查询 |

**查询条件（`ApiLogQueryRequest` extends `PageQuery`）**：`traceId` / `userId` / `module` / `requestUri` / `httpMethod` / `bizStatus` / `startTime` / `endTime`
**查询条件（`OutboundLogQueryRequest` extends `PageQuery`）**：`traceId` / `userId` / `module` / `targetHost` / `httpMethod` / `bizStatus` / `startTime` / `endTime`

- 返回 `Result<PageResult<XxxVO>>`，与既有 ③④ 查询同结构。
- 分页语义与 ③④ 对齐：`size` 收敛 `[1, 200]`、按 `create_time` 倒序、等值条件带「是否提供」开关、时间范围 `ge/le`。
- 条件走既有索引：`idx_trace_id` / `idx_user_id` / `idx_biz_status` / `idx_create_time`（`outbound_log` 另有 `idx_target_host`）。

---

## 三、落点

| 类型 | 文件 |
|---|---|
| DTO | `backend/whale-ops/whale-audit/.../audit/dto/ApiLogQueryRequest.java`、`OutboundLogQueryRequest.java` |
| VO | `.../audit/vo/ApiLogVO.java`、`OutboundLogVO.java` |
| Service | `.../audit/service/ApiLogService.java`、`OutboundLogService.java` |
| Impl | `.../audit/service/impl/ApiLogServiceImpl.java`、`OutboundLogServiceImpl.java` |
| Controller | `.../audit/controller/LogIngestController.java`（新增 2 端点 + 类注释修订） |
| 单测 | `.../src/test/.../audit/service/impl/ApiLogServiceImplTest.java`、`OutboundLogServiceImplTest.java` |
| 实体复用 | `com.bluewhale.common.log.entity.ApiLogRecord` / `OutboundLogRecord`、`...mapper.ApiLogMapper` / `OutboundLogMapper`（starter 已提供，零改动） |

---

## 四、验收标准

1. `GET /audit/log/api/page`、`GET /audit/log/outbound/page` 可达，均带 `@RequirePermission("sys:audit:log:list")`，返回 `Result<PageResult<VO>>`。
2. `size` 收敛 `[1,200]`；查询结果按 `create_time` 倒序；8 个条件项均可独立与组合生效（未提供则不生成条件）。
3. 单测全绿：分页映射、`size` 上下界收敛、条件拼装 + 倒序、空结果。
4. `mvn -f backend/pom.xml -pl :whale-audit -am test` 通过（含既有不回归）；`checkstyle:check` 0 违规。
5. 留痕齐备：检查报告 + devlog 四节 + 台账销账（`register.md` REQ-SEC-006 / `next-steps.md` / `design/audit-log.md`）。

---

## 五、偏差与残余

- **元审计（`@AuditLog` QUERY）**：本波新增端点**不标注** `@AuditLog`，以与 ③④ 既有查询端点口径一致；四通道查询的「读审计被审计」统一化登记为后续项（如需，应四端点一起加，避免新的不对称）。
- **运行时端到端验证**：本机无 MySQL/Docker，接口真实分页结果无法联调；以单测 + 编译 + checkstyle 静态门禁为证据（与既有多通道日志口径一致）。
