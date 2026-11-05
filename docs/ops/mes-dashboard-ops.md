# MES 综合看板运维手册（M5-B）

> 适用版本：Wave 5.79 M5-B（2026-11-05）
> 范围：`whale-mes` 综合看板聚合、SSE 实时推送、CSV 导出
> 关联计划：[2026-11-04-wave5.79-mes-panorama.md](../plans/2026-11-04-wave5.79-mes-panorama.md) §2.6/§5

---

## 1. 端点与权限

| 端点 | 方法 | 权限码 | 说明 |
|---|---|---|---|
| `/mes/dashboard` | GET | `sys:mes:dashboard:view` (1381) | 综合看板快照（生产/质量/设备/人员四域） |
| `/mes/dashboard/stream` | POST | `sys:mes:dashboard:stream` (1382) | SSE 推送（`text/event-stream`，事件名 `dashboard`） |
| `/mes/dashboard/export` | GET | `sys:mes:dashboard:export` (1383) | CSV 导出（独立权限码控制，验收标准） |

授权口径：查看/订阅对全部 MES 角色开放；导出仅 `tenant_admin` / `mes_manager` / `mes_team_lead`。
菜单：`sys_menu` 288「综合看板」（`/mes/dashboard`）。

## 2. SSE 推送

- **通道形态**：Spring MVC `SseEmitter`（`POST` + JSON body + `Authorization: Bearer`），前端经共享 http 客户端 `streamSse` 订阅（401 自动续期后重放）。
- **推送间隔**：服务端默认 10 秒（配置键 `mes.dashboard.push-interval-seconds`，D14 口径）；入参钳制 5–60 秒。
- **连接生命周期**：不设服务端超时（`SseEmitter(0L)`）；客户端断开、写回失败（`IOException`/`IllegalStateException`）时自动清理调度任务；聚合查询瞬时异常仅告警跳过本轮，不断开连接。
- **连接数观测**：`MesDashboardStreamService#activeConnections()`（当前活跃推送连接数）。

### 运维要点

1. **线程模型**：单线程守护调度器（`mes-dashboard-sse`），推送任务为轻量只读聚合。连接数增长后若成为瓶颈，可扩容调度池并评估聚合 SQL 索引。
2. **网关超时**：生产 Nginx/网关如配置 `proxy_read_timeout`，需大于推送间隔（默认 10s），建议 ≥60s，避免空读断连。
3. **容量**：每个连接每 10 秒触发一次四域聚合查询（其中生产/质量看板各 1–2 条 SQL）。按 100 并发连接估算约 20 QPS 只读查询，MySQL 常规规格可承载；再高需引入本地缓存或降低推送频率。
4. **降级**：SSE 不可用时前端自动回落手动刷新；页面提供开关可随时断开推送。

## 3. 指标口径（SLO 一致性基线）

| 域 | 数据源 | 口径约束 |
|---|---|---|
| 生产 | `MesDispatchService.board` | 与 `/mes/dispatch/board` 同源同实例（冒烟项 3a 断言） |
| 质量 | `MesInspectionService.board` | 与 `/mes/quality/board` 同源同实例（冒烟项 4a 断言） |
| 设备 | 台账状态快照 + 当日停机单 + 当日 OEE 快照均值 | OEE 均值 4 位 HALF_UP，空值忽略 |
| 人员 | 当日报工/有效工时/排班/交接班 + 当月绩效 | 净工时=结束−开始−暂停且非负；锁定进度 `locked/total` |

**SLO**：综合看板快照 P95 < 1s（单实例、10 万级报工数据内）；导出 P95 < 3s。口径一致性由 `scripts/smoke/bw-mes-m5b-smoke.ps1` 持续回归。

## 4. 导出与保留策略

- **导出格式**：CSV（UTF-8 带 BOM，Excel 直接打开不乱码），文件名 `mes-dashboard-yyyyMMddHHmmss.csv`，`Content-Disposition: attachment`。
- **数据范围**：导出内容与页面快照完全一致（同一 `dashboard()` 聚合），含生成时间戳。
- **归档/保留**：
  - 看板本身为实时聚合查询，不落地快照表，无独立保留周期需求；
  - 底层事实数据的保留沿用各模块策略——采集样本归档策略见 M4 `mes_collect_archive_policy`（候选计数 + `lastRunAt`），物理清理待 D14 定稿；
  - 报工/工时/绩效为业务留痕数据，随库备份策略保留（当前 MySQL 每日备份）。

## 5. 故障排查

| 现象 | 排查步骤 |
|---|---|
| SSE 连不上（前端提示「实时推送已断开」） | 1) 网关是否放行 `/mes/dashboard/stream`（权限 1382）；2) Nginx 是否缓冲了 `text/event-stream`（需 `proxy_buffering off`）；3) `whale-mes` 日志搜 `看板快照推送失败` |
| 推送间隔不生效 | 入参钳制 5–60；服务端默认由 `mes.dashboard.push-interval-seconds` 控制，重启生效 |
| 导出 403 | 用户角色缺权限 1383；用 `SELECT 1 FROM sys_role_permission WHERE permission_id=1383 AND role_id IN (用户角色)` 核对 |
| 指标与独立看板不一致 | 先比对 `boardDate`；生产/质量域为同源调用，不一致需检查网关路由是否打到不同 `whale-mes` 实例 |
| 设备/人员域为空 | 确认 `bluewhale_mes` 的 `mes_eq_equipment`/`mes_wf_*` 表有当日数据；OEE 需当日快照，无快照均值为空是预期行为 |

## 6. 验收与回归

- 冒烟：`pwsh -File scripts/smoke/bw-mes-m5b-smoke.ps1`（需 `SMOKE_PASSWORD`），覆盖聚合、口径一致、SSE、导出 BOM/文件名、匿名 401。
- 单测：`mvnw -pl :whale-mes "-Pcoverage-gate" verify`（含 ArchUnit、Checkstyle、覆盖率门禁）。
- 权限迁移：`db/migration-5.79-mes-m5b.sql`（幂等，可重复执行）。
