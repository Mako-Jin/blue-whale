# Wave 6.11 —— 告警外发增强：恢复通知 / 静默时段 / 接收人选择器

> 承接 [Wave 6.10](wave6.10-alert-notify.md)。6.10 打通了「阈值告警 → webhook / 站内信」的外发链路与场景级通知设置，
> 但只做到了「响」：告警恢复不通知、半夜照样推、接收人要手工抄用户 ID。本波次补齐这三块。
>
> 服务：`whale-twin`（8089）；迁移 V6；无新增服务、无新增端口。

## 一、范围与状态

| 编号 | 项 | 状态 |
|---|---|---|
| M0 | 恢复通知（`twin.alert.recovered`，只回「当初真的发过」的） | ✅ 已落地并实机验证 |
| M1 | 每日静默时段（支持跨零点，critical 可穿透） | ✅ 已落地并实机验证 |
| M2 | 接收人选择器（走 auth 内网通讯录，带关键字检索） | ✅ 已落地并实机验证 |
| M3 | 顺带修复：twin 缺 `api_log` / `audit_log` 导致运行期 500 | ✅ 已落地（V6 迁移） |
| M4 | 顺带修复：auth 通讯录「无部门用户」NPE | ✅ 已落地（非本波次引入） |

## 二、恢复通知

### 语义

- 只有**当初真的外发过**（webhook 或站内信至少一条 `SUCCESS`）的告警，恢复时才回一条「已恢复」；
  被门槛/静默/冷却拦掉的告警不发恢复，避免「没说过响，却说停了」。
- 恢复事件 `event=twin.alert.recovered`，标题前缀 `【孪生恢复】`，正文携带 `recoveredFrom`
  （告警原级别）与恢复时间。
- 恢复后**清冷却位**：同一告警再次触发时立即可通知，不必等冷却窗口耗尽。
- 幂等键前缀 `twin-alert-recovery-`，与告警键 `twin-alert-` 分开，二者互不覆盖。

### 实现

- `TwinAlertNotifyFilter`
  - 新增 `notifiedLevel`（`LinkedHashMap`，`MAX_ENTRIES = 4096`，超限淘汰最旧），`accept` 放行时记点位级别；
  - `acceptRecovery(sceneId, stateKey)`：返回当初通知的级别并移除冷却位与级别记录，返回 `null` 表示「没发过，不发恢复」；
  - `isNotified(...)` 供只读判断。
- `TwinAlertNotifyService`
  - `notify` 拆出 `notifyRecovery(...)` + `execute(sceneId, alert, task)`；
  - `dispatchAll/sendInapp/recordDispatch/title/content/idempotencyKey` 全部加 `event` 参数，
    恢复投递记录 `level=normal`（便于在投递记录里按级别区分）。
- 开关：`twin.alert-notify.recovery-enabled`（全局，默认 `true`）+ 场景级 `recoveryEnabled`（单向收紧，
  全局关时场景设什么都不发）。

## 三、静默时段

### 规则

- 格式 `HH:mm`，`9:05` 归一为 `09:05`；非法值（含 `24:00`、`25:00`）**直接丢弃该键**，回到「无静默」。
- 窗口左闭右开 `[start, end)`，**支持跨零点**（`22:00–08:30` = 当晚 22:00 到次日 08:30）。
- `start == end` 或任一侧非法 → **fail-open**（不静默），宁可多推也不吞告警。
- 静默只拦正常告警；`critical` 是否穿透由全局 `quiet-hours-critical-bypass`（默认 `true`）决定。
- 全局窗口与场景窗口**取并集**（任一命中即静默），不能靠场景配置突破全局静默。

### 实现

- `TwinQuietHours`（新建，纯工具无状态）：`normalize(String)` / `active(start, end, now)` / `overlaps(...)`。
- `TwinAlertNotifySettingsService`
  - 节点常量 `recoveryEnabled` / `quietStart` / `quietEnd`；`mergeConfig` 增 `putOrRemoveQuiet`（非法值删键）；
  - `SceneSettings` 扩为 7 元组；新增 `recoveryAllowed(...)` / `quietActive(settings, level, now)`；
  - `buildVo(sceneId, settings)` 转发到带 `LocalTime` 的重载，便于单测注入时刻。
- `TwinAlertNotifyService.notify` 在门槛判定之后、冷却判定之前插入静默判断
  （静默拦掉的告警不占冷却位，与 6.10 的门槛处理保持一致）。
- 配置：`quiet-hours-start` / `quiet-hours-end` / `quiet-hours-critical-bypass`。

## 四、接收人选择器

- 新端点 `GET /twin/alert-notify/recipients?keyword=&size=`，权限 `sys:twin:scene`；
  租户从 `WebHeaders.tenantId(request)` 取并透传给 auth（auth 内网通讯录**强制要求** `X-Tenant-Id`，
  缺失直接 400「缺少租户上下文，请通过网关访问」）。
- `TwinUserDirectoryClient`：`GET {authBaseUrl}/user/internal/directory`，走 `InternalTokenSupport.restClient()`
  自动带内网令牌；任何异常（未授权、超时、格式变化）只记 WARN 并返回空列表，**不影响场景页其它功能**。
- 前端：`Select mode="multiple" showSearch filterOption={false}` + 300ms 防抖；
  已选但不在检索结果里的用户保留为 `用户 {id}` 选项，避免切换页面后丢失既有配置。

## 五、顺带修复（实机暴露）

1. **`bluewhale_twin` 缺 `api_log` / `audit_log`**：`ApiLogFilter` 异步写 `api_log`、`@AuditLog` 写 `audit_log`，
   twin V1 基线只有业务表 → 所有带审计注解的写接口直接 500（`Table 'bluewhale_twin.api_log' doesn't exist`）。
   新增 `V6__common_log_tables.sql`（与其它业务服务同构：`InnoDB` / `utf8mb4_general_ci` / `CREATE TABLE IF NOT EXISTS`）。
2. **auth 通讯录 NPE**：`UserDirectoryAppService.toViewObjects` 用 `deptNames.get(user.getDeptId())`，
   用户无部门时 `Map.of().get(null)` 抛 NPE → 整页 500。改为先判 `deptId == null` 再取值，并补回归单测。
3. **`@Pattern` 正则语法错误**：`^([01]?\d|2[0-3]):[0-5]\d)?$` 多一个右括号，编译期不报，
   运行期初始化 `PatternValidator` 抛 `HV000032` → 整个 `PUT` 500。改为 `^(([01]?\d|2[0-3]):[0-5]\d)?$`，
   并加 `TwinAlertNotifySettingsRequestTest` 兜住「正则必须可编译」。

## 六、验证（2026-11-10 实机）

- 单测 `mvn test`：**187/187** 全绿（本波次新增 30 例：静默工具 6 + 过滤器 4 + 外发服务 7 + 设置服务 5 + 通讯录客户端 4 + 请求校验 4），
  Checkstyle **0** 违例。
- 前端 `tsc -b`：0 错误。
- 容器：`docker compose -f docker-compose.services.yml --env-file .env --profile aiot up -d twin` → `healthy`，
  Flyway 实机日志 `Successfully applied 1 migration ... now at version v6`。
- 接口实测：
  - `GET /twin/alert-notify/recipients?keyword=adm` → `{"id":"1","name":"超级管理员","username":"admin","deptName":null}`；无权限头 403。
  - `PUT /twin/scenes/{id}/alert-notify` 写 `recoveryEnabled=false` / `quietStart=22:00` / `quietEnd=8:30` / `userIds=[1]`
    → 回读一致且 `quietEnd` 归一为 `08:30`、`quietNow=true`、`effectiveRecoveryEnabled=false`。
  - 非法时间 `25:00` → `code=400`「静默开始时间格式应为 HH:mm」（不再 500）。
  - 重启后 `api_log doesn't exist` 与全局异常计数均为 0；联调场景已删除。

## 七、遗留

- 告警仍只支持**场景级**配置，不支持「按用户偏好」（谁想收什么级别、谁的免打扰）；
  真要做得接用户偏好中心，属于另一个波次。
- ~~通讯录检索目前只按 `keyword` 全量拉一页（`size` 默认 20、上限 50），未做部门树下钻。~~
  → 已由 [Wave 6.12](wave6.12-recipient-picker-plus.md) 补齐分页 `total` 与部门树筛选（含子部门）。
- Docker 数据盘已迁至 F 盘（用户已自行完成，2026-11-10），C 盘释放约 29G，本条从遗留清单销账。
