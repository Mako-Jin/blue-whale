# Wave 5.33 验收清单（checklist）

> 口径：逐项核对「代码是否按冻结契约实现 + 构建/校验是否通过」。
> 勾选即代表该项已实测通过。

## ① whale-i18n

- [x] whale-i18n 服务骨架落地（8087 / `bluewhale_i18n` / `/i18n/**`），5 个消息 CRUD 端点与 `sys:i18n:*` 权限码一一对应
- [x] 权限种子 id 60–63 已入 whale-auth `db/init.sql`，网关 `/i18n/**` 路由已配
- [x] 模块自检 + 全量回归 + 网关链路冒烟通过，已登记 service-catalog/naming-conventions

## ② whale-ui 主题变量

- [x] `whale-frontend/elemental/whale-ui/src/theme/` 提供 `buildThemeVars` + `applyThemeVars`，经 `src/index.ts` 导出
- [x] 未新增第二套 `--whale-*` 变量，复用 `@bluewhale/whale-common-theme` 的 `--bw-*` 令牌体系
- [x] `pnpm --filter @bluewhale/whale-ui build` 通过

## ③ whale-monitor

- [x] `GET /monitor/service/list` 与 `GET /monitor/service/{serviceName}/instances` 落地，`sys:monitor:list`（id 64）已入种子
- [x] 单测 + 模块自检通过
- [x] 运行期可启动（`${SERVER_PORT:9102}` + Spring Boot 4 正确包名排除无库自动配置），经网关实测 `code=0` / 未知服务名 `code=404`

## ④ whale-alert

- [x] `application.yml` 补 `bluewhale_alert` 数据源 + mybatis-plus + `whale.permission.header.enabled=true`
- [x] `db/init.sql` 含 `alert_rule` / `alert_record` 两表（列与 spec §三 ④ 逐字段一致）
- [x] `/alert/rule`（page/详情/create/update/delete/启停）+ `/alert/record/page` 端点落地，权限码 65–69 齐全
- [x] 网关 `/alert/**` → `lb://whale-alert` 路由已配
- [x] 单测 + 模块自检通过

## ⑤ whale-audit 收尾

- [x] `roadmap.md` §2.5 whale-audit 行已勾选，`task-breakdown.md` 残留状态已标注
- [x] MQ 汇聚 / 异步派发已明确标注「环境受限（无 RabbitMQ）backlog」

## ⑥ whale-frontend/ops

- [x] vite 端口 = 8104，代理 `/auth|/monitor|/alert|/audit` → 网关 8080
- [x] token 存储前缀 = `whale_ops_`，`Result` 信封解包（`code=0` 成功 / 真实 401 跳登录）
- [x] Monitor / Audit / Alert 三页落地并可构建（Alert 页为「告警规则 + 告警记录」双 Tab）
- [x] `pnpm --filter whale-ops build` 通过
- [x] 浏览器实机冒烟通过（登录/守卫 + Monitor 6 服务 + Audit 14 条 + Alert 3 条规则，无 console error / 无真实 4xx·5xx）

## ⑦ whale-portal

- [x] 工作台统计卡片 + ERP/OA/MES 入口卡片落地
- [x] 我的待办接 `/workflow/task/page`，消息通知为占位
- [x] `pnpm --filter whale-portal build` 通过

## ⑧ whale-erp / oa / mes / pdm

- [x] `whale-erp`(8102) / `whale-oa`(8103) 补列表页 + 表单页 + API 层
- [x] `whale-mes`(8105) / `whale-pdm`(8106) 子应用新建并纳入 workspace
- [x] 四个应用构建通过（全量 16/16 tasks）

## ⑨ whale-pay

- [x] `application.yml` 补 `bluewhale_pay` 数据源 + mybatis-plus；`db/init.sql` 含 `pay_order`
- [x] `/pay/order`（page/详情/create/`{id}/pay`/`{id}/cancel`）落地，状态 `CREATED→PAID/CANCELLED` 单向
- [x] 权限码 `sys:pay:add|list|pay|cancel`（70–73）已入种子
- [x] 单测 + 模块自检通过

## 收尾

- [x] 全量回归 `mvn -o clean install -T 1C` 全绿（Checkstyle severity=error）
- [x] `pnpm -C frontend build` 全绿
- [x] roadmap 相应项勾选 + Phase 1/5 标「环境受限待办」
- [x] devlog + review 报告归档，commit 并 push origin（Gitee）
- [x] 验证驱动修复：monitor 运行期阻塞（Z-5）、实机库缺失权限种子 id 64（Z-6）、ops 告警页空表格（Z-7）
