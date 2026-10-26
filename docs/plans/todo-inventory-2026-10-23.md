# 全服务 / 全前端 待办盘点（2026-10-23）

> 口径：以**当前代码与文档事实**为准，只列「尚未完成或未验收」的项；已完成项不重复列出。
> 数据来源：`docs/plans/roadmap.md`（未勾选项）、`docs/plans/whale-{auth,gateway,workflow}-backlog.md`、`docs/service-catalog.md`、`docs/plans/next-steps.md`、`docs/plans/2026-10-22-platform-remediation.md`，以及源码 `TODO / 占位 / mock` 标记实测（2026-10-23）。
> 状态判定：**可用** = 主链路已闭环；**最小实现** = 仅骨架 CRUD；**空壳** = 只有启动类/占位页。

---

## 一、后端服务（16 个）

| # | 服务 | 端口 | 现状 | 待办 |
|---|---|---|---|---|
| 1 | whale-gateway | 8080 | 可用 | ① 为 `/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**`、`/config/**` 补路由（现 11 条，缺 5 条）② 熔断半开探测仅单测覆盖，运行态未单独取证（残余风险，非功能缺口）③ T-CI-7：镜像构建扩至全服务 |
| 2 | whale-auth | 8081 | 可用 | ① `replaceUserAttrs` 非空批量写路径缺单测（需真实 Mapper 代理）② 端口仍硬编码 8081，未走 `${SERVER_PORT}`（service-catalog 偏差 C-02）③ 前端按钮级权限渲染需在 admin 逐页复验 |
| 3 | whale-job | 8082 | 可用 | 调度「多实例失效广播」未覆盖（`next-steps` §五 残余）；归档导出已通，Wave 5.49 起可落对象存储 |
| 4 | whale-workflow | 8083 | 可用 | backlog 6 项全部结案；dnd-kit 真实指针拖拽已于 2026-10-26 人工复核通过（前端侧） |
| 5 | whale-form | 8084 | 可用 | ① 字段级索引自动优化未做（Wave 5.27 起遗留）② 表单 ↔ 工作流联动需端到端验收（发起入口已接 `formId`，缺跨模块回归用例） |
| 6 | whale-file | 8085 | 可用 | 无（Wave 5.49：存储插件化，`local`/`minio` 插件可切换，MinIO 自动建桶已解锁 job/audit 归档；见 [devlog §十九](../devlog/2026-10-23.md)） |
| 7 | whale-pay | 8086 | 最小实现 | 仅订单 CRUD + 支付/取消占位动作；无真实支付渠道、对账、退款 |
| — | ~~whale-i18n~~ | ~~8087~~ | **已删除** | Wave 5.50：国际化下沉 `whale-i18n-sdk`（各服务库自建 `sys_i18n_message`、自持文案），服务/路由/`bluewhale_i18n` 库已移除 |
| 9 | whale-agent | 8090 | 可用 | 问答 / 事实层 / MCP 已闭环；服务目录事实重建的耗时约束（<5s）无持续回归 |
| 10 | whale-audit | 9101 | 可用 | 已闭环（HTTP + MQ 双通道）；RabbitMQ 管理插件未启用，死信队列无法从控制台重放 |
| 11 | whale-monitor | 9102 | 最小实现 | 仅 `/monitor/service` 服务列表；**未接入 Prometheus 抓取/存储**（与 ops 前端同源缺口） |
| 12 | whale-alert | 9103 | 最小实现 | 仅规则/记录 CRUD；**无通知渠道**（邮件 / IM / Webhook 未实现） |
| 13 | whale-config | 9201 | 空壳 | 仅启动类；无接口、无网关路由；配置一律直接用 Nacos |
| 14 | whale-erp | 9001 | 空壳 | 无 DDL、无控制器、无权限种子、无网关路由（roadmap Phase 4 未勾选） |
| 15 | whale-mes | 9002 | 空壳 | 同上 |
| 16 | whale-pdm | 9003 | 空壳 | 同上 |
| 17 | whale-oa | 9004 | 空壳 | 同上；remediation 建议先打通「OA 请假」真实闭环 |

> 源码级 `TODO/FIXME` 实测：后端各服务**均无未实现标记**（`grep` 命中的 `toDomain` 为误报），待办全部是「功能未做」而非「代码留坑」。

---

## 二、前端应用（11 个应用 + 共享包）

| # | 应用 | 端口 | 现状 | 待办 |
|---|---|---|---|---|
| 1 | whale-platform（qiankun 基座） | 8000 | 可用 | ① 3 个 `PlaceholderPage` 收口：用户/角色/菜单管理（国际化演示页已于 Wave 5.50 随集中服务删除） ② qiankun 已注册 `erp/oa/mes/pdm` 四子应用，但基座路由只挂载 `portal/erp/oa`，**缺 `/mes/*`、`/pdm/*` 挂载点** |
| 2 | whale-admin | 8001 | 可用 | ① 动态路由仍是静态注册（`dynamicRoutes.tsx` 未配置时显示「页面未配置」兜底）② 角色/菜单删除与「角色分配」交互未逐项复验 ③ 无表单/工作流/审计页面（分别由独立应用承担） |
| 3 | whale-ops | 8104 | 可用 | Monitor 页数据来自 `/monitor/service`；**Prometheus/Grafana 对接未做**，告警页无通知渠道展示 |
| 4 | whale-portal | 8101 | 最小实现 | ① Dashboard 3 个统计写死 0（进行中流程 / 未读通知 / 系统服务数）② 「我的待办」的「去处理」为占位（未跳 `/workflow/task/{id}`）③ 通知中心为静态 mock（`MOCK_NOTICES`） |
| 5 | whale-form-designer | 8002 | 可用 | 主链路闭环；dnd-kit 真实指针拖拽自动化不可复现，需人工复核 |
| 6 | whale-workflow-designer | 8003 | 可用 | backlog 全结案；dnd-kit 拖拽已于 2026-10-26 人工复核通过（[报告](../reviews/2026-10-26-p3-e6-designer-drag-manual.md)） |
| 7 | whale-agent | 8004 | 可用 | 工作台/证据面板/工具链已闭环 |
| 8 | whale-erp | 8102 | 空壳 | 2 页占位，`api/erp.ts` 明确标注「接占位数据源，待后端 `/erp/**`」 |
| 9 | whale-oa | 8103 | 空壳 | 同上，待 `/oa/**` |
| 10 | whale-mes | 8105 | 空壳 | 同上，待 `/mes/**` |
| 11 | whale-pdm | 8106 | 空壳 | 同上，待 `/pdm/**` |
| 12 | whale-games-core（共享包） | — | 空壳 | `engine.ts` 主循环（requestAnimationFrame）与资源清理仍为 TODO，无游戏 UI 应用 |
| 13 | whale-common-tools（共享包） | — | 遗留占位 | `request.ts` 仍是占位（实际请求统一走 `@bluewhale/http`），建议删除或标注废弃 |
| 14 | whale-common-three（共享包） | — | 空壳 | `scene.ts` three.js 场景封装未实现 |

---

## 三、平台 / 部署 / CI

| 项 | 来源 | 待办 |
|---|---|---|
| Phase 4 业务应用 | roadmap L139–141 | ① `whale-apps/*` 后端填充（DDL + 六端点 + 权限种子 + 路由）② 前后端 API 联调（占位数据源替换 + 基座 proxy + 真实登录）③ 基座/门户占位收口 |
| Phase 5 部署 | roadmap L162–163 | ① `docker-compose.prod.yml` 实机验证（本机无 Let's Encrypt 证书，仅做过 `config` 合并校验）② Nginx 生产配置（与 Phase 1 已完成的开发态 HTTPS **内容重叠**，建议合并勾选而非重复实施） |
| 镜像与 CI | roadmap / cicd.md | ① T-CI-7：镜像构建扩至全服务（现仅 gateway + web 进 `docker-deploy.yml`）② `coverage-gate` 尚未接入 CI（AUTH-24 残余）③ Gitee Go 流水线未实机触发（插件 JDK 21 / Node 24 兼容性待验证）④ GHCR 推送未在本机实跑（需 registry 凭据） |
| 可观测性 | remediation | Prometheus/Grafana 栈未编排进 compose；告警通知渠道缺失 |
| 架构类（已明确不采纳/暂缓） | remediation L14–25 | TanStack Query、API 契约自动化、God Service 拆分——**维持暂缓，不算待办** |

---

## 四、建议优先级（Top 8）

1. **Phase 4 轨 A–F**：`whale-apps/*` 选一个域（建议 OA 请假）打通「DDL → 端点 → 权限种子 → 网关路由 → 前端页面」真实闭环，避免四个空壳同时扩张。
2. **网关补 5 条路由** + 基座补 `/mes/*`、`/pdm/*` 挂载点（各 1 处配置，收益立竿见影）。
3. **whale-portal 三处占位收口**（Dashboard 统计 / 待办跳转 / 通知中心）——直接决定门户可用性。
4. **基座 3 个 PlaceholderPage 收口**（用户/角色/菜单），可与 admin 页面复用。
5. ✅ **~~whale-file 接对象存储（S3/MinIO）+ 解锁 job/audit 归档~~**（已完成，Wave 5.49：插件化 `local`/`minio` + 自动建桶；见 [devlog §十九](../devlog/2026-10-23.md)）。
6. **whale-alert 通知渠道**（至少邮件 + Webhook）与 **whale-monitor Prometheus 接入**，补齐运维闭环。
7. **T-CI-7 镜像扩面 + coverage-gate 接 CI**，把已就绪的 Dockerfile 模板铺到全部服务。
8. **Phase 5 两处勾选合并**（prod compose 实机验证、Nginx 生产配置与 Phase 1 去重）。

---

## 五、说明

- 本清单为**盘点**，不改变任何代码或排期；执行时按「小步闭环 + devlog 记录 + roadmap 勾选」惯例逐项推进。
- 业务应用（erp/mes/oa/pdm）与 whale-config 的建议仍是「冻结扩张、单向打通一个域」，与 [2026-10-22-platform-remediation.md](2026-10-22-platform-remediation.md) L160–165 结论一致。
