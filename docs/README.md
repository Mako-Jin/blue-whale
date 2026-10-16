# 蓝鲸系统文档

## 目录结构

```
docs/
├── plans/               # 阶段性规划（搭路线图、专项计划）
│   └── roadmap.md       # Phase 1-5 搭建填充路线图
├── devlog/              # 开发日志（按日期记录已完成任务）
│   └── YYYY-MM-DD.md
├── architecture.md      # 架构设计文档
├── backend.md           # 后端开发指南（待建）
├── frontend.md          # 前端开发指南（待建）
├── devops.md            # 运维部署指南（待建）
├── api.md               # API 文档规范（待建）
├── database.md          # 数据库设计（待建）
└── ai.md                # AI Agent 使用说明（待建）
```

## 文档约定

1. **规划先行**：每个阶段的实施计划写入 `docs/plans/`，完成一项在 roadmap 中勾选
2. **完成必记录**：每完成一个任务，在 `docs/devlog/` 当日日期文件中追加记录（格式见日志文件末尾模板）
3. **重大决策留痕**：版本选型、架构变更等决策及原因记录在当日 devlog 中

## 快速开始

- 项目总览：[README.md](../README.md)
- 路线图：[plans/roadmap.md](plans/roadmap.md)
- 开发日志：[devlog/](devlog/)
