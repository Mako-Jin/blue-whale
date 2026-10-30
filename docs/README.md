# 蓝鲸系统文档

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
│   └── slo.md               # SLO 与 PromQL
├── design/              # 架构与专项设计（定稿后方可进入编码）
│   ├── adr/                 # 架构决策记录
│   ├── audit-log.md         # 审计日志方案
│   ├── distributed.md       # 分布式 ID / 锁 / 事务 / 幂等
│   ├── ddd-whale-auth.md    # 权限服务 DDD 架构
│   ├── doc-service.md       # 在线文档/Wiki 服务设计（M1–M5 已交付）
│   ├── frozen-optional-modules.md  # 冻结模块清单
│   ├── multi-database.md    # 多数据库（国产化）演进设计
│   ├── network-boundary.md  # 网络边界与上线必检
│   ├── whale-monitor-boundary.md   # whale-monitor 能力边界
│   └── cicd.md              # CI/CD 流水线
├── plans/               # 阶段性规划与波次台账（roadmap / task-breakdown / pending-issues / wave5.*）
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

1. **文档先行（REQ-DOC-002）**：任何需求须先在 `requirements/register.md` 登记 REQ 编号与验收标准；架构类需求须先产出 `design/` 设计文档并定稿，方可进入编码
2. **规划先行**：每个阶段的实施计划写入 `docs/plans/`，完成一项在 roadmap 中勾选
3. **完成必记录（REQ-DOC-001）**：每完成一个任务，在 `docs/devlog/` 当日日期文件中追加记录，条目须同时覆盖**需求项 / 完成项 / 待做项 / 已修正问题项**
4. **变更必检查（REQ-QA-002）**：每次新增或变更代码，须按 `checklists/code-review-checklist.md` 逐条自检，并在 `reviews/` 留存报告，否则不得标记为已完成
5. **重大决策留痕**：版本选型、架构变更等决策及原因记录在当日 devlog 或对应 `design/` 文档中
6. **命名统一（REQ-ARCH-004）**：新增库 / 服务 / 模块 / 包 / 配置项前，须先在 [naming-conventions.md](naming-conventions.md) 登记域词与端口并遵循其格式，纳入检查清单 E15
7. **完成即划线**：`plans/` 下的待办台账（backlog / next-steps）与蓝图（roadmap / task-breakdown）中，**已完成的任务一律加删除线**（`~~…~~`），并**保留** `[x]` / `✅` 状态标记与完成结论；任务编号（如 `AUTH-35` / `T5.2`）在表格中直接划线，使「已完成」与「待做」一眼可辨
8. **门禁**：未登记 REQ、设计未定稿、无检查报告、构建未通过 —— 四者任一不满足，变更不得提交

## 快速开始

- 项目总览：[README.md](../README.md)
- 路线图：[plans/roadmap.md](plans/roadmap.md)
- 服务与端口：[service-catalog.md](service-catalog.md)
- 命名规范：[naming-conventions.md](naming-conventions.md)
- 开发日志：[devlog/](devlog/)
