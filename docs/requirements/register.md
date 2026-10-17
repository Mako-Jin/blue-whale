# 需求登记册（Requirements Register）

> 用途：本项目所有需求（业务功能 + 工程规范）的统一台账。
> 规则：**任何需求在动手实现之前，必须先在此登记编号与验收标准**（见 REQ-DOC-002）。
> 最后更新：2026-10-17 ｜ 维护：BlueWhale

---

## 一、编号规则

`REQ-<域>-<三位序号>`

| 域代码 | 含义 | 说明 |
|---|---|---|
| `FUNC` | 业务功能 | 面向用户的业务能力（ERP/MES/PDM/OA/门户等） |
| `DOC` | 文档与流程 | 文档体系、变更控制、交付规范 |
| `QA` | 质量与检查 | 代码检查、规范、度量 |
| `SEC` | 安全 | 认证、授权、审计、脱敏 |
| `DIST` | 分布式 | 分布式 ID、锁、事务、幂等等 |
| `ARCH` | 架构 | 分层、限界上下文、模块边界 |
| `CICD` | 交付流水线 | 构建、测试、制品、部署 |
| `INFRA` | 基础设施 | 中间件、容器、环境 |

**状态取值**：`待启动` → `设计已定稿` → `实现中` → `待验证` → `已完成` → `已阻塞` / `已取消`

---

## 二、本轮新增需求（2026-10-17 提出）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点（文档 / 代码） | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-DOC-001 | 每一步任务完成后必须生成文档，覆盖：需求项、完成项、待做项、已修正问题项等 | 用户指令 1 | 高 | 已完成 | [devlog/2026-10-17.md](../devlog/2026-10-17.md) + 本登记册 + [task-breakdown.md](../plans/task-breakdown.md) | 每个任务条目同时具备「需求项 / 完成项 / 待做项 / 已修正问题」四类信息，可逐条追溯 |
| REQ-QA-001 | 制定代码规范检查清单，覆盖：协议漏洞、代码漏洞、圈复杂度、魔法值、命名与格式等 | 用户指令 2 | 高 | 已完成 | [checklists/code-review-checklist.md](../checklists/code-review-checklist.md) | 清单含 5 大类检查项，每项给出判定标准与检查方式 |
| REQ-QA-002 | 每一次新增/变更的代码都必须按上述清单执行检查并留存报告 | 用户指令 2 | 高 | 实现中 | [checklists/code-review-checklist.md](../checklists/code-review-checklist.md) + [reviews/](../reviews/) | 每一批代码变更都有对应的检查报告文件，且报告逐项对应清单条目 |
| REQ-CICD-001 | 搭建 CI/CD 流水线（含代码质量门禁） | 用户指令 3 | 高 | 设计已定稿 | [design/cicd.md](../design/cicd.md) + `.github/workflows/` | 流水线覆盖 提交→检查→构建→测试→制品→镜像→部署；质量门禁可产出报告 |
| REQ-SEC-001 | 设计审计日志能力（记录谁、何时、对什么、做了什么、结果） | 用户指令 4 | 高 | 实现中 | [design/audit-log.md](../design/audit-log.md) + [whale-audit/db/init.sql](../../backend/whale-ops/whale-audit/src/main/resources/db/init.sql) | 审计表字段（T5.9 已落地 7 字段）与设计一致，注解/切面/脱敏/查询接口待实现 |
| REQ-DIST-001 | 分布式 ID 方案（全局唯一、趋势递增、无 JS 精度丢失） | 用户指令 5 | 中 | 实现中 | [design/distributed.md](../design/distributed.md) + whale-spring-boot-starter | 方案定稿；T5.1 已落地 Long→String 序列化 + IdUtils 注释澄清 |
| REQ-DIST-002 | 分布式锁方案（可重入、看门狗续期、锁命名规范、可复用工具） | 用户指令 5 | 中 | 实现中 | [design/distributed.md](../design/distributed.md) + whale-spring-boot-starter | T5.2 已落地 `@DistributedLock` 注解 + AOP 切面，场景接入待做 |
| REQ-DIST-003 | 其他分布式问题（幂等、缓存一致性、分布式事务、限流熔断、链路追踪、分布式会话） | 用户指令 5 | 中 | 设计已定稿 | [design/distributed.md](../design/distributed.md) | 每项含备选方案对比表与推荐结论 |
| REQ-DOC-002 | 所有要求先落到文档，每一步都需遵守（文档先行 / 变更控制） | 用户指令 6 | 高 | 已完成 | 本登记册 + [docs/README.md](../README.md) 约定章节 | 未登记编号、未定稿设计的变更不得进入编码阶段 |
| REQ-ARCH-001 | 权限服务（whale-auth）改造为 DDD 分层架构 | 用户指令 7 | 高 | 设计已定稿 | [design/ddd-whale-auth.md](../design/ddd-whale-auth.md) | 四层架构、限界上下文、聚合设计、渐进迁移路线与回滚方案齐备 |
| REQ-PROC-001 | 按既有波次计划持续推进下一步任务 | 用户指令 8 | 高 | 实现中 | [task-breakdown.md](../plans/task-breakdown.md) + [next-steps.md](../plans/next-steps.md) Wave 5.0 | Wave 5.0 任务逐项闭环，含构建与单测验证 |
| REQ-PROC-002 | 每轮结束输出下一步建议与任务清单 | 用户指令 9 | 中 | 已完成 | [plans/next-steps.md](../plans/next-steps.md) | 清单含任务、依赖、优先级、验收口径 |

---

## 三、本轮实现的支撑需求（派生）

| 编号 | 需求描述 | 来源 | 优先级 | 状态 | 落点 | 验收标准 |
|---|---|---|---|---|---|---|
| REQ-SEC-002 | 登出（logout）令牌失效：接入分布式黑名单，使已登出 token 立即不可用 | REQ-DIST-002 + Wave 4 T4.1 | 高 | 待验证 | `whale-auth` `security/TokenBlacklist` + `JwtAuthenticationFilter` + `AuthService#logout` | 登出后原 accessToken 再访问受保护接口返回 401；Redis 不可用时降级不阻断登录 |
| REQ-SEC-003 | 令牌黑名单的网关侧校验 | REQ-SEC-002 延伸 | 中 | 待启动 | `whale-gateway` `JwtAuthGlobalFilter` | 网关层命中黑名单直接 401，避免跨服务漏放行 |
| REQ-SEC-005 | 生产密钥强制校验：非 dev 环境启动时若 JWT 签名密钥仍为默认占位值则拒绝启动 | 用户指令 5 + Wave 5 T5.8 | 中 | 实现中 | `whale-auth` `config/JwtProperties` + `config/SecretPlaceholderChecker` | 非 dev profile 下密钥为占位值时应用启动失败并输出明确告警；dev 环境不受影响 |

---

## 四、既有需求基线（引自路线图，仅登记不重复展开）

| 编号 | 需求描述 | 状态 | 来源 |
|---|---|---|---|
| REQ-FUNC-001 | 基础设施就绪：Docker Compose 中间件（MySQL/Redis/Nacos/RabbitMQ/Nginx） | 已阻塞（本机无 Docker） | [roadmap.md](../plans/roadmap.md) Phase 1 |
| REQ-FUNC-002 | whale-auth 权限中心（RBAC + ABAC 六层级） | 已完成（API 齐备，前端未对接） | roadmap Phase 2.1 |
| REQ-FUNC-003 | whale-gateway 网关（动态路由 + JWT 鉴权 + TraceId） | 已完成（Sentinel 限流待做） | roadmap Phase 2.2 |
| REQ-FUNC-004 | whale-common 公共组件（Result / 异常 / 分页 / 自动填充） | 已完成 | roadmap Phase 2.3 |
| REQ-FUNC-005 | whale-i18n 国际化（三级回退 + Redis 缓存） | 已完成（消息 CRUD 接口待做） | roadmap Phase 2.4 |
| REQ-FUNC-006 | 其余服务骨架填充（job / workflow / form / file / pay / audit / monitor） | 待启动 | roadmap Phase 2.5 |
| REQ-FUNC-007 | 前端基座（登录 + 路由守卫 + 主布局 + 主题） | 已完成 | roadmap Phase 3.1 |
| REQ-FUNC-008 | whale-ui 组件库（ProTable / FormModal / SearchForm） | 待启动 | roadmap Phase 3.2 |
| REQ-FUNC-009 | admin 后台管理（用户/角色/菜单 + 对接 auth） | 待启动 | roadmap Phase 3.3 |
| REQ-FUNC-010 | whale-portal 门户（工作台 / 待办 / 消息） | 待启动 | roadmap Phase 3.4 |

---

## 五、变更控制流程（REQ-DOC-002 落地）

```
提出需求
  └─► ① 在本登记册登记 REQ 编号、优先级、验收标准
        └─► ② 产出/更新设计文档（docs/design/ 或 docs/plans/）并定稿
              └─► ③ 拆分为任务，写入 task-breakdown.md（含依赖与波次）
                    └─► ④ 编码实现
                          └─► ⑤ 按 code-review-checklist.md 逐项自检 → 产出 docs/reviews/ 报告
                                └─► ⑥ 构建/测试验证通过
                                      └─► ⑦ 更新 roadmap 勾选 + devlog 记录（四类信息）
                                            └─► ⑧ 提交推送
```

**门禁规则**：

1. 无 REQ 编号的需求不得进入编码；
2. 设计未定稿（状态未到 `设计已定稿`）的架构类需求不得进入编码；
3. 代码变更未产出检查报告（REQ-QA-002）不得标记为 `已完成`；
4. 构建验证未通过的变更不得提交。
