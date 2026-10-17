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
├── design/              # 架构与专项设计（定稿后方可进入编码）
│   ├── audit-log.md         # 审计日志方案
│   ├── distributed.md       # 分布式 ID / 锁 / 事务 / 幂等
│   ├── ddd-whale-auth.md    # 权限服务 DDD 架构
│   └── cicd.md              # CI/CD 流水线
├── plans/               # 阶段性规划
│   ├── roadmap.md           # Phase 1-5 路线图
│   ├── task-breakdown.md    # 波次任务拆分
│   └── next-steps.md        # 下一步建议与任务清单
├── devlog/              # 开发日志（需求/完成/待做/已修正 四类信息）
│   └── YYYY-MM-DD.md
├── architecture.md      # 架构设计文档
├── naming-conventions.md # 命名规范（库 / 表 / 服务 / 模块 / 包 / 配置 / Redis / 前端）
├── backend.md           # 后端开发指南（待建）
├── frontend.md          # 前端开发指南（待建）
├── devops.md            # 运维部署指南（待建）
├── api.md               # API 文档规范（待建）
├── database.md          # 数据库设计（待建）
└── ai.md                # AI Agent 使用说明（待建）
```

## 文档约定

1. **文档先行（REQ-DOC-002）**：任何需求须先在 `requirements/register.md` 登记 REQ 编号与验收标准；架构类需求须先产出 `design/` 设计文档并定稿，方可进入编码
2. **规划先行**：每个阶段的实施计划写入 `docs/plans/`，完成一项在 roadmap 中勾选
3. **完成必记录（REQ-DOC-001）**：每完成一个任务，在 `docs/devlog/` 当日日期文件中追加记录，条目须同时覆盖**需求项 / 完成项 / 待做项 / 已修正问题项**
4. **变更必检查（REQ-QA-002）**：每次新增或变更代码，须按 `checklists/code-review-checklist.md` 逐条自检，并在 `reviews/` 留存报告，否则不得标记为已完成
5. **重大决策留痕**：版本选型、架构变更等决策及原因记录在当日 devlog 或对应 `design/` 文档中
6. **命名统一（REQ-ARCH-004）**：新增库 / 服务 / 模块 / 包 / 配置项前，须先在 [naming-conventions.md](naming-conventions.md) 登记域词与端口并遵循其格式，纳入检查清单 E15
7. **门禁**：未登记 REQ、设计未定稿、无检查报告、构建未通过 —— 四者任一不满足，变更不得提交

## 快速开始

- 项目总览：[README.md](../README.md)
- 路线图：[plans/roadmap.md](plans/roadmap.md)
- 开发日志：[devlog/](devlog/)
