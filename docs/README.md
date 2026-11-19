# 蓝鲸系统文档

## 文档导航（先看这里，再进目录）

| 你想…… | 去哪 |
|---|---|
| 了解项目是什么、现在做到哪、接下来做什么 | [plans/bluewhale-blueprint-2026Q4.md](plans/bluewhale-blueprint-2026Q4.md)（现状基线 + 收线蓝图 + M1–M4 阶段门槛） |
| 查某个 Phase / 波次的完成状态与结论 | [plans/roadmap.md](plans/roadmap.md)（Phase 1–15 总台账，已完成的划线保留） |
| 找某个主题的执行计划 | [plans/INDEX.md](plans/INDEX.md)（plans/ 目录 90+ 文件的分类索引：主控 / 现行 / 主题 / 历史四层） |
| 看某天做了什么、遗留什么 | [devlog/YYYY-MM-DD.md](devlog/)（需求 / 完成 / 待做 / 已修正四段式） |
| 查服务、端口、库 | [service-catalog.md](service-catalog.md) |
| 查某个需求编号 | [requirements/register.md](requirements/register.md) |
| 查版本兼容与升级/回滚 | [version-matrix.md](version-matrix.md) |
| 查全局风险与状态 | [risk-register.md](risk-register.md) |
| 查术语中英文口径 | [glossary.md](glossary.md) |

## 按角色阅读顺序

| 角色 | 建议顺序 |
|---|---|
| 新加入的开发者 | [README.md](../README.md) 技术栈与快速开始 → [architecture.md](architecture.md) → [backend.md](backend.md) / [frontend.md](frontend.md) → [service-catalog.md](service-catalog.md) → [api.md](api.md) / [database.md](database.md) → 当日 [devlog](devlog/) |
| 后端开发 | [architecture.md](architecture.md) → [backend.md](backend.md) → [database.md](database.md) → [api.md](api.md) → [distributed.md](design/distributed.md) → 所属服务设计稿（`design/<域>-service.md`） |
| 前端开发 | [frontend.md](frontend.md) → [api.md](api.md) → [service-catalog.md](service-catalog.md) §二 → 对应 `platform-apps/` 应用说明 |
| 测试 / 验收 | [requirements/register.md](requirements/register.md) → [plans/roadmap.md](plans/roadmap.md) → [checklists/code-review-checklist.md](checklists/code-review-checklist.md) → 波次 `reviews/` 报告 → [risk-register.md](risk-register.md) |
| 运维 / 发布 | [devops.md](devops.md) → [design/network-boundary.md](design/network-boundary.md) → [design/cicd.md](design/cicd.md) → [ops/](ops/) → [risk-register.md](risk-register.md) |
| 架构评审 | [architecture.md](architecture.md) → [design/adr/](design/adr/)（决策历史）→ [design/](design/) 专项设计 → [risk-register.md](risk-register.md) |
| 产品 / 项目管理 | [plans/bluewhale-blueprint-2026Q4.md](plans/bluewhale-blueprint-2026Q4.md) → [plans/roadmap.md](plans/roadmap.md) → [requirements/register.md](requirements/register.md) → [glossary.md](glossary.md) |

## 文档职责边界

| 文档 | 写什么 | 不写什么（去哪写） |
|---|---|---|
| [architecture.md](architecture.md) | 服务全景、限界上下文、依赖关系、全局架构决策 | 单服务实现细节（`design/`）、运维步骤（`devops.md`） |
| [service-catalog.md](service-catalog.md) | 端口 ↔ 服务 ↔ 组件 ↔ 库 ↔ 对外前缀、迁移/表数量、状态 | 接口字段定义（`api.md` + 代码）、建表 DDL（迁移脚本） |
| [api.md](api.md) | 响应信封、错误码、分页/排序、幂等、版本、限流、鉴权头与内网约定 | 各接口业务语义（服务设计稿 / springdoc） |
| [database.md](database.md) | 命名、字段惯例、连接池、逻辑删除、多库演进 | 具体表结构与 ER（服务设计稿 / Flyway 迁移） |
| [devops.md](devops.md) | 环境分层、部署、配置与密钥、备份恢复、发布回滚、运维手册索引 | 业务功能说明（`design/`）、接口契约（`api.md`） |
| [naming-conventions.md](naming-conventions.md) | 库/表/服务/模块/包/配置/Redis/前端命名与号段登记 | 具体业务模型（设计稿） |
| [backend.md](backend.md) | 模块结构、DDD 分层、构建测试、配置基线 | 服务级设计（`design/<域>-service.md`） |
| [frontend.md](frontend.md) | Monorepo、应用族、共享包、构建与约定 | 页面交互稿（应用目录 / 设计稿） |
| [ai.md](ai.md) | Agent 架构、知识索引、评测、接入 | 模型提示词细节（代码）、业务知识内容（文档库） |
| [version-matrix.md](version-matrix.md) | 版本集中视图、兼容约束、升级/回滚流程 | 版本事实源本身（POM / package.json / compose 变量） |
| [risk-register.md](risk-register.md) | 全局风险、评级、状态、缓解 | 波次级风险细节（`reviews/` 报告） |
| [glossary.md](glossary.md) | 术语中英文与一句话定义 | 完整业务规则（设计稿 / 需求册） |
| [design/](design/) | 领域模型、边界、表结构、接口语义、ADR | 运维操作（`devops.md`）、任务排期（`plans/`） |
| [plans/](plans/) | 路线图、波次计划、任务分解 | 已定稿的设计（`design/`）、结果记录（`devlog/`） |

## 目录结构

```
docs/
├── requirements/        # 需求登记册（REQ 编号体系 + 变更控制门禁）
│   └── register.md
├── checklists/          # 检查清单（代码规范 / 安全 / 复杂度 / 魔法值）
│   └── code-review-checklist.md
├── reviews/             # 代码检查报告（每次变更一份）
│   └── YYYY-MM-DD-<主题>.md
├── agent/               # 知识 Agent 评测资产
│   └── golden-set.json      # 40 题黄金集（结构化/表结构/代码/文档）
├── ops/                 # 运维手册
│   ├── agent-index-ops.md   # 知识索引重建/状态/排障
│   ├── agent-mcp.md         # 知识 Agent MCP / IDE 接入
│   ├── doc-service-ops.md   # 在线文档/Wiki 部署/配置/备份/清理/排障
│   ├── external-integration-onboarding.md  # 外部集成凭据预检作业单
│   ├── music-service-ops.md  # 音乐平台部署/配置/迁移/备份/清理/排障
│   └── slo.md               # SLO 与 PromQL
├── design/              # 架构与专项设计（定稿后方可进入编码）
│   ├── adr/                 # 架构决策记录
│   ├── audit-log.md         # 审计日志方案
│   ├── distributed.md       # 分布式 ID / 锁 / 事务 / 幂等
│   ├── ddd-whale-auth.md    # 权限服务 DDD 架构
│   ├── doc-service.md       # 在线文档/Wiki 服务设计（M1–M5 已交付）
│   ├── frozen-optional-modules.md  # 冻结模块清单
│   ├── mes-module-panorama.md      # MES 功能模块全景设计（M0–M5 规划稿）
│   ├── pdm-module-panorama.md      # PDM 功能模块全景设计（M0–M5 规划稿）
│   ├── crm-module-panorama.md      # CRM 功能模块全景设计（M0–M6 规划稿）
│   ├── multi-database.md    # 多数据库（国产化）演进设计
│   ├── network-boundary.md  # 网络边界与上线必检
│   ├── whale-monitor-boundary.md   # whale-monitor 能力边界
│   └── cicd.md              # CI/CD 流水线
├── plans/               # 阶段性规划与波次台账（入口：INDEX.md 分类索引；主控：roadmap / blueprint / 五服务总账）
├── specs/               # 历史 spec 归档（wave5.1b–5.52 等）
├── whale-auth/          # 权限服务蓝图归档（originals / final）
├── whale-audit/         # 审计服务蓝图归档（originals / final）
├── devlog/              # 开发日志（需求/完成/待做/已修正 四类信息）
│   └── YYYY-MM-DD.md
├── architecture.md      # 架构设计文档（含 23 服务全景与限界上下文）
├── naming-conventions.md # 命名规范（库 / 表 / 服务 / 模块 / 包 / 配置 / Redis / 前端）
├── service-catalog.md   # 服务与端口清单（端口 ↔ 服务 ↔ 组件 ↔ 库 ↔ 对外前缀，含第三方组件端口）
├── backend.md           # 后端开发指南（模块结构 / DDD 分层 / 构建测试 / 配置基线）
├── frontend.md          # 前端开发指南（Monorepo / 应用族 / 共享包 / 约定）
├── devops.md            # 运维部署指南（compose / 监控栈 / 生产前置条件 / 运维手册索引）
├── api.md               # API 文档规范（Result 体 / 路径前缀 / 鉴权与内网头 / springdoc）
├── database.md          # 数据库设计规范（命名 / 惯例 / 连接池基线 / 国产化演进）
└── ai.md                # AI Agent 使用说明（whale-agent / whale-ai / 知识索引 / 评测 / 接入）
```

## 文档约定

0. **IDE 协作文档源（T-23 决策：保留入库）**：`.trae/`（`documents/` 波次方案、`specs/` spec/checklist/tasks）登记为历史文档源，与 `docs/plans/`、`docs/specs/` 互补，不做 IDE 私有清理
1. **文档先行（REQ-DOC-002）**：任何需求须先在 `requirements/register.md` 登记 REQ 编号与验收标准；架构类需求须先产出 `design/` 设计文档并定稿，方可进入编码
2. **规划先行**：每个阶段的实施计划写入 `docs/plans/`，完成一项在 roadmap 中勾选
3. **完成必记录（REQ-DOC-001）**：每完成一个任务，在 `docs/devlog/` 当日日期文件中追加记录，条目须同时覆盖**需求项 / 完成项 / 待做项 / 已修正问题项**
4. **变更必检查（REQ-QA-002）**：每次新增或变更代码，须按 `checklists/code-review-checklist.md` 逐条自检，并在 `reviews/` 留存报告，否则不得标记为已完成
5. **重大决策留痕**：版本选型、架构变更等决策及原因记录在当日 devlog 或对应 `design/` 文档中
6. **命名统一（REQ-ARCH-004）**：新增库 / 服务 / 模块 / 包 / 配置项前，须先在 [naming-conventions.md](naming-conventions.md) 登记域词与端口并遵循其格式，纳入检查清单 E15
7. **完成即划线**：`plans/` 下的待办台账（backlog / next-steps）与蓝图（roadmap / task-breakdown）中，**已完成的任务一律加删除线**（`~~…~~`），并**保留** `[x]` / `✅` 状态标记与完成结论；任务编号（如 `AUTH-35` / `T5.2`）在表格中直接划线，使「已完成」与「待做」一眼可辨
8. **门禁**：未登记 REQ、设计未定稿、无检查报告、构建未通过 —— 四者任一不满足，变更不得提交

## 历史文档登记（T-26）

- **重复内容**：`docs/**/*.md` 存在 49 组哈希重复（共 108 份），来自同一波次方案的 `plans/`、`reviews/` 与 `.trae/` 多份拷贝。经 roadmap 确认（2026-10-22 平台评审 P0-4）**originals 与历史副本保留现状、不清理**；检索以「最新日期 + 现行事实文档」为准。
- **编号冲突**：`wave5.35` 同时用于两个独立主题——Sentinel 评估（`reviews/2026-10-21-wave5.35-sentinel-evaluation.md`）与工作流网关（`plans/wave5.35-workflow-gateway.md`、`reviews/2026-10-21-wave5.35-workflow-gateway.md`）；`wave5.61` 的 design/plan/review 属同一波次（合同电子签署）的不同文档类型，非冲突。引用时须带主题后缀（如 `wave5.35-sentinel-evaluation`）。

## 快速开始

- 项目总览：[README.md](../README.md)
- 现状与蓝图：[plans/bluewhale-blueprint-2026Q4.md](plans/bluewhale-blueprint-2026Q4.md)
- 路线图台账：[plans/roadmap.md](plans/roadmap.md)
- 计划索引：[plans/INDEX.md](plans/INDEX.md)
- 服务与端口：[service-catalog.md](service-catalog.md)
- 命名规范：[naming-conventions.md](naming-conventions.md)
- 开发日志：[devlog/](devlog/)
