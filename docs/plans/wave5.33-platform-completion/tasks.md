# Wave 5.33 任务分解

> 规格：[spec.md](spec.md)。每项完成后：模块自检 → 全量回归 → 冒烟 → devlog/report/roadmap → commit & push origin。
> 勾选即代表该子项已完成并通过验证。

## ① whale-i18n 消息 CRUD（新建服务）

- [x] ①-1 模块骨架：`backend/whale-services/whale-i18n/pom.xml` + `WhaleI18nApplication` + `application.yml`（8087 / `bluewhale_i18n` / Nacos / Redis / MyBatis-Plus / `whale.permission.header.enabled=true`）
- [x] ①-2 注册模块：`backend/whale-services/pom.xml` `<modules>` 增 `whale-i18n`
- [x] ①-3 业务：`I18nMessageQueryRequest` + `I18nMessageSaveRequest` + `I18nMessageVO` + `I18nMessageService(+Impl)` + `I18nMessageController`（5 端点 + `@RequirePermission("sys:i18n:*")` + `@AuditLog`）
- [x] ①-4 缓存一致性：写操作后按 `messageKey`+`locale` 调 `I18nCacheEvictHelper.evict`
- [x] ①-5 DDL：`whale-i18n/src/main/resources/db/init.sql`（`sys_i18n_message`）
- [x] ①-6 权限种子：whale-auth `db/init.sql` 追加 id 60–63
- [x] ①-7 网关路由：`whale-gateway/application.yml` 增 `/i18n/**` → `lb://whale-i18n`
- [x] ①-8 单测：`I18nMessageServiceImplTest`（CRUD + 唯一键冲突 + 超长/空校验）
- [x] ①-9 登记：`service-catalog.md` / `naming-conventions.md`
- [x] ①-10 模块自检 + 全量回归 + 冒烟 + 文档 + push

## ② whale-ui 主题 CSS 变量注入

- [x] ②-1 核对 `frontend/shared-packages/whale-common-theme` 现状，确定「补齐 vs 复用」
- [x] ②-2 `whale-ui/src/theme/`：`vars.css`（`:root` `--whale-*` 令牌）+ `index.ts`（`buildThemeVars` + `applyThemeVars`）+ `src/index.ts` 导出
- [x] ②-3 构建验证：`pnpm -C frontend --filter @bluewhale/whale-ui build`（tsup + dts）
- [x] ②-4 文档 + push

## ③ whale-monitor 最小可用

- [x] ③-1 `MonitorServiceController` + `MonitorService`（Nacos `DiscoveryClient` 服务列表 + 实例健康）
- [x] ③-2 权限种子：`sys:monitor:list`（id 64）
- [x] ③-3 单测 + 模块自检 + 回归 + 冒烟 + 文档 + push

## ④ whale-alert 最小可用

- [ ] ④-1 接线：`application.yml` 补 datasource(`bluewhale_alert`)/mybatis + `db/init.sql`（`alert_rule` / `alert_record`）
- [ ] ④-2 业务：`AlertRule(+Service/Impl/Controller)` + `AlertRecord(+Service/Impl/Controller)` + DTO/VO
- [ ] ④-3 权限种子：id 65–69
- [ ] ④-4 网关路由：`/alert/**` → `lb://whale-alert`
- [ ] ④-5 单测 + 模块自检 + 回归 + 冒烟 + 文档 + push

## ⑤ whale-audit 收尾（文档）

- [ ] ⑤-1 `roadmap.md` §2.5 勾选 whale-audit；`task-breakdown.md` 残留标注
- [ ] ⑤-2 MQ 汇聚 / 异步派发 → 标注「环境受限（无 RabbitMQ）backlog」

## ⑥ frontend/ops 运维平台

- [ ] ⑥-1 基座：vite 端口 8104 + 代理 `/auth|/monitor|/alert|/audit` → 8080；`api/http.ts` + `token.ts`（前缀 `whale_ops_`）+ 登录/守卫
- [ ] ⑥-2 `pages/Monitor.tsx` 服务监控面板
- [ ] ⑥-3 `pages/Audit.tsx` 审计日志查询
- [ ] ⑥-4 `pages/Alert.tsx` 告警列表
- [ ] ⑥-5 构建 + 浏览器冒烟 + 文档 + push

## ⑦ whale-portal 门户

- [ ] ⑦-1 工作台统计卡片 + ERP/OA/MES 入口卡片
- [ ] ⑦-2 我的待办（`/workflow/task/page`）+ 消息通知占位
- [ ] ⑦-3 构建 + 文档 + push

## ⑧ whale-erp / oa / mes / pdm 骨架

- [ ] ⑧-1 `whale-erp`(8102) 列表页 + 表单页 + API 层
- [ ] ⑧-2 `whale-oa`(8103) 列表页 + 表单页 + API 层
- [ ] ⑧-3 `whale-mes`(8105) 新建子应用 + 骨架页
- [ ] ⑧-4 `whale-pdm`(8106) 新建子应用 + 骨架页
- [ ] ⑧-5 构建 + 文档 + push

## ⑨ whale-pay 最小可用

- [ ] ⑨-1 接线：`application.yml` 补 datasource(`bluewhale_pay`)/mybatis + `db/init.sql`（`pay_order`）
- [ ] ⑨-2 业务：`PayOrder(+Service/Impl/Controller)`（page/详情/create/`{id}/pay`/`{id}/cancel`）+ DTO/VO
- [ ] ⑨-3 权限种子：`sys:pay:*`（id 70–73）
- [ ] ⑨-4 单测 + 模块自检 + 回归 + 冒烟 + 文档 + push

## 收尾

- [ ] Z-1 全量回归 `mvn -o clean install -T 1C` + `pnpm -C frontend build` 全绿
- [ ] Z-2 roadmap §2.4/§2.5/§3.2/§3.4/Phase 4 勾选 + Phase 1/5 标「环境受限待办」
- [ ] Z-3 补勾 wave5.20 / wave5.27 / wave5.28 文档漏勾
- [ ] Z-4 devlog + review 报告 + push origin
