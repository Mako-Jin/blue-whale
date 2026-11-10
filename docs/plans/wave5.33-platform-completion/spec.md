# Wave 5.33 平台收尾（多服务 + 前端补齐）— 规格

> 关联：`docs/plans/roadmap.md` §2.4 / §2.5 / §3.2 / §3.4 / Phase 4、`docs/requirements/register.md` REQ-FUNC-005 / 006
> 用户指令（2026-10-20）：**「各个服务还有什么待办？然后依次解决。」**
> 口径（AskUserQuestion 冻结）：**全量依次推进**（按依赖从易到难）；**部署/CI 类（Phase 1 / Phase 5）暂跳过**，标记「环境受限（本机无 Docker）待办」。
> 执行原则：文档先行 → 编码 → 模块自检 → 全量回归 → 冒烟 → devlog/report/roadmap → commit & push origin（Gitee）。

---

## 一、范围（roadmap 未勾项盘点结论）

| # | 待办 | 现状 | 本次目标 |
|---|---|---|---|
| ① | whale-i18n 消息 CRUD 接口（§2.4） | SDK 已具备三级回退 + Redis 缓存 + `I18nCacheEvictHelper`，但**无任何 REST 宿主**（SDK 是库、无服务） | **新建服务 `whale-i18n`**（8087，`/i18n/**`，`bluewhale_i18n`），复用 SDK 实体/Mapper/缓存清理 |
| ② | whale-ui 主题 CSS 变量注入（§3.2） | `@bluewhale/whale-ui` 已二次封装 ProTable/FormModal/SearchForm + PermissionProvider | 在 whale-ui 增加**主题变量层**（`:root` CSS 变量 + 注入器） |
| ③ | whale-monitor（§2.5） | 仅 `WhaleMonitorApplication` + yml 空骨架（9102，路由已配） | 填充**最小可用**：服务实例与健康聚合查询 |
| ④ | whale-alert（§2.5） | 仅 `WhaleAlertApplication` + yml 空骨架（9103，**缺路由**） | 填充**最小可用**：告警规则 CRUD + 告警记录查询；补网关路由 |
| ⑤ | whale-audit 收尾（§2.5） | 四通道 + 元审计 + 出站/接口日志 + 导出/归档 + 保留期 + 网关 `/audit/**` 均已落地 | **仅文档勾选同步**；MQ 汇聚 / 异步派发列 backlog（环境受限：无 RabbitMQ） |
| ⑥ | whale-frontend/ops 运维平台（Phase 4） | 仅占位 SPA（`App.tsx`，端口 8081，无代理） | 服务监控面板 / 审计日志查询 / 告警列表 三页 |
| ⑦ | whale-portal 门户（§3.4） | qiankun 子应用占位（8101，base `/portal`） | 工作台 / 我的待办 / 消息通知 + ERP·OA·MES 入口卡片 |
| ⑧ | whale-erp / oa / mes / pdm（Phase 4） | erp/oa 为子应用占位（8102/8103）；mes/pdm **前端目录不存在** | 各应用列表页 + 表单页 + API 联调骨架 |
| ⑨ | whale-pay（§2.5） | 仅 `WhalePayApplication` + yml 空骨架（8086，路由已配） | 填充**最小可用**：支付单 CRUD + 状态流转 |
| — | Phase 1.1–1.4、Phase 5（Docker/CI/Nginx HTTPS） | 本机**无 Docker** | **不做**；roadmap 标注「环境受限待办」 |

---

## 二、依赖与执行顺序

```
① whale-i18n 服务（独立，无依赖）
   └─ ② whale-ui 主题变量（前端库，独立）
        └─ ③ whale-monitor（独立）→ ④ whale-alert（独立）→ ⑤ 文档同步（零代码）
             └─ ⑥ whale-frontend/ops（依赖 ③④ 与已有 audit 接口）
                  └─ ⑦ whale-portal（依赖 ④ audit/alert? 否；依赖 ② whale-ui 主题）
                       └─ ⑧ erp/oa/mes/pdm 骨架（依赖 ② + backend erp/oa 是否已填充）
                            └─ ⑨ whale-pay（独立）
```

可并行子任务：② 与 ① 并行；③ 与 ④ 并行；⑥ ⑦ ⑧ 三前端应用结构互不影响，可并行。

---

## 三、冻结契约

### ① whale-i18n 消息 CRUD（新建服务）

- 模块：`whale-backend/whale-services/whale-i18n`；base 包 `com.bluewhale.i18n`；artifactId `whale-i18n`；`spring.application.name=whale-i18n`；端口 `${SERVER_PORT:8087}`；库 `bluewhale_i18n`；对外前缀 `/i18n/**`。
- 复用 SDK：`SysI18nMessage`（实体）、`SysI18nMessageMapper`（`@MapperScan("com.bluewhale.**.mapper")` 覆盖）、`I18nCacheEvictHelper`（自动装配 Bean，写后清理 `whale:i18n:{locale}:{code}`）。
- 端点（`I18nMessageController`，`@RequestMapping("/i18n/message")`）：
  | 方法 | 路径 | 权限码 | 审计 |
  |---|---|---|---|
  | GET | `/i18n/message/page` | `sys:i18n:list` | — |
  | GET | `/i18n/message/{id}` | `sys:i18n:list` | — |
  | POST | `/i18n/message` | `sys:i18n:add` | CREATE |
  | PUT | `/i18n/message/{id}` | `sys:i18n:edit` | UPDATE |
  | DELETE | `/i18n/message/{id}` | `sys:i18n:delete` | DELETE |
- 业务规则：`messageKey` + `locale` 唯一（`uk_key_locale`）；新增前查重 → 冲突抛 `PARAM_ERROR`；`messageContent` 非空；写后按（新/旧）`messageKey`+`locale` 调 `I18nCacheEvictHelper.evict`。
- 权限点种子（whale-auth `db/init.sql` 追加 id **60–63**）：`sys:i18n:list/add/edit/delete`。
- 网关路由：whale-gateway `application.yml` 追加 `id: whale-i18n, Path=/i18n/**, uri: lb://whale-i18n`。
- DDL：`whale-backend/whale-services/whale-i18n/src/main/resources/db/init.sql`（`sys_i18n_message`，与 SDK 同名表逐字段一致）。
- 登记：`docs/service-catalog.md` §一 增 #16 行 + §二 路由表增行 + §五 端口速查增 8087；`docs/naming-conventions.md` §八 增域词 `i18n`。

### ② whale-ui 主题 CSS 变量注入

- 位置：`whale-frontend/elemental/whale-ui/src/theme/`。
- 产出：`vars.css`（`:root` 定义 `--whale-*` 设计令牌：主色/成功/警告/错误/文本/边框/圆角/间距，默认取自 antd v5 默认 token）+ `index.ts`（`applyThemeVars(tokens?)` 将变量写入 `document.documentElement`；`buildThemeVars(token)` 纯函数便于单测）+ 通过 `src/index.ts` 导出。
- 约束：**先核对 `whale-frontend/shared-packages/whale-common-theme`**，避免与其重复；若已有等价能力则改为「补齐缺失变量 + 导出统一入口」，不重复造第二套。
- 样式入口：`vars.css` 由 whale-ui 构建产物导出（tsup 不打包 css 时改用 `src/theme/index.ts` 内联注入 `<style>`）。

### ③ whale-monitor 最小可用

- 模块：`whale-backend/whale-ops/whale-monitor`（8087? 否 → **9102**，路由已配 `/monitor/**`）。
- 端点：`GET /monitor/service/list`（Nacos 服务名 + 实例数 + 健康实例数）、`GET /monitor/service/{serviceName}/instances`。基于 `org.springframework.cloud.client.discovery.DiscoveryClient`（Nacos Discovery 已在 pom）。
- 权限码：`sys:monitor:list`（**64**）。无库（采集为主，roadmap 已标「——」）。
- 不做：Prometheus 指标拉取、Grafana 对接、Actuator 聚合（列 backlog）。

### ④ whale-alert 最小可用

- 模块：`whale-backend/whale-ops/whale-alert`（**9103**）；补网关路由 `/alert/**`。
- 库：`bluewhale_alert`；表 `alert_rule`（id/rule_name/rule_code/metric/target/operator/threshold/duration_seconds/level/channel/status/remark/公共字段）、`alert_record`（id/rule_id/rule_code/alert_level/content/status/trigger_time/recover_time/公共字段）。
- 端点：`/alert/rule`〔page/详情/create/update/delete/启停〕+ `/alert/record/page`。
- 权限码：`sys:alert:rule:add/edit/delete/list`（**65–68**）、`sys:alert:record:list`（**69**）。
- 不做：真实通知通道（邮件/短信/Webhook 发送）、规则触发引擎（列 backlog；pom 已含 `spring-boot-starter-mail`）。

### ⑤ whale-audit 收尾（零代码）

- 核实结论：MQ 汇聚（无 RabbitMQ）、异步派发丢采集 → **不在本轮**；`/audit/**` 前缀与归档/保留期均已落地。
- 动作：`roadmap.md` §2.5 勾选 whale-audit 行；`task-breakdown.md` 残留行标注状态。

### ⑥ whale-frontend/ops 运维平台

- 目录：`whale-frontend/ops`（独立 SPA，**vite 端口调整为 8104**，避开 8081 与 whale-auth 冲突；代理 `/auth|/monitor|/alert|/audit` → 网关 8080）。
- 三页：`pages/Monitor.tsx`（服务监控面板：`GET /monitor/service/list` + 实例列表）、`pages/Audit.tsx`（审计日志查询：`GET /audit/log/page`）、`pages/Alert.tsx`（告警列表：`GET /alert/record/page`）。
- 复用 `@bluewhale/whale-ui` 的 ProTable + 主题变量；token 存储前缀 `whale_ops_`。

### ⑦ whale-portal 门户

- 目录：`whale-frontend/platform-apps/whale-portal`（qiankun 子应用，8101，base `/portal`）。
- 三区：**工作台**（统计卡片）、**我的待办**（`GET /workflow/task/page` 当前用户待办）、**消息通知**（占位列表）+ **ERP/OA/MES 入口卡片**（跳转 `/erp` `/oa` `/mes`）。

### ⑧ whale-erp / oa / mes / pdm 骨架

- `whale-erp`(8102,`/erp`)、`whale-oa`(8103,`/oa`) 已存在子应用 → 补 列表页 + 表单页；
- `whale-mes`、`whale-pdm` 前端目录**新建**（8105 `/mes`、8106 `/pdm`），结构对齐 erp/oa，页面以骨架 + 后端 `/erp|/oa|/mes|/pdm/**` 接口对齐（后端未填充时先接占位数据源，标注 TODO）。
- 后端 `whale-apps/*`（erp/mes/pdm/oa，9001–9004）本轮**不填充**（避免与前端骨架同时铺开，列后续 wave）。

### ⑨ whale-pay 最小可用

- 模块：`whale-backend/whale-services/whale-pay`（**8086**，路由已配 `/pay/**`）。
- 库：`bluewhale_pay`；表 `pay_order`（id/order_no/business_key/amount/currency/channel/status/pay_time/remark/公共字段）。
- 端点：`/pay/order`〔page/详情/create/`{id}/pay`/`{id}/cancel`〕；状态 `CREATED→PAID/CANCELLED` 单向。
- 权限码：`sys:pay:add/list/pay/cancel`（**70–73**）。

---

## 四、验证口径（每项）

1. 模块自检：`mvn -o -f whale-backend/pom.xml -pl <module> -am test checkstyle:check`（`JAVA_HOME=java21`，`dangerouslyDisableSandbox`）。
2. 全量回归：`mvn -o clean install -T 1C`（Checkstyle `severity=error` 全绿）。
3. 前端：`pnpm -C frontend --filter <pkg> build`（tsc + vite/tsup）。
4. 运行时冒烟（中间件已就绪时）：经网关 8080 走一遍 CRUD；信封口径 `code=0` / 404 / 400 / 401。
5. 文档：`docs/devlog/2026-10-2x.md` 追加 + `docs/reviews/` 报告 + roadmap/tasks 勾选。
6. 提交：`git commit` + `push origin`（Gitee）。

---

## 五、明确不做（本轮）

- Phase 1.1–1.4（Docker Compose / .env / Nginx 联调）、Phase 5（Dockerfile 多阶段 / GitHub Actions / prod compose / HTTPS）——**环境受限（本机无 Docker）**。
- 审计 MQ 汇聚、告警通知通道、监控 Prometheus/Grafana 对接、后端 erp/mes/pdm/oa 业务填充（列后续 wave）。
- @dnd-kit 真实指针拖拽手势的自动化（Wave 5.30/5.32 遗留，需人工复核）。
