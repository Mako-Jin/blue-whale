# AI Agent 使用说明

## 架构

| 组件 | 位置 | 端口 | 说明 |
|---|---|---|---|
| whale-agent（Java） | `whale-backend/whale-services/whale-agent` | 8090 | 知识 Agent：事实层采集（代码/表结构/端点/文档）、Lucene 索引检索、问答编排、知识桶白名单入库 |
| whale-ai（Python / FastAPI） | `ai/whale-ai` | 8092 | LangChain/LangGraph 编排、工具循环、确认态；**仅内网**（不注册 Nacos/网关），由 `WHALE_AI_BASE_URL` 指向 |

问答引擎开关：`WHALE_AGENT_QA_ENGINE=legacy|langchain`（默认 legacy）。

## 知识入库与可见性

- 索引目录本地 `data/agent-index/`（可重建，不入库）；运行时 `WHALE_AGENT_INDEX_DIR`。
- 文件知识入库走**桶白名单 default-deny**：仅白名单桶可被索引（[决策报告](reviews/2026-10-29-agent-knowledge-bucket-policy.md)）；白名单改动需重启（T-06 热更新待办）。
- 索引重建：`POST /agent/knowledge/rebuild`，操作手册 [ops/agent-index-ops.md](ops/agent-index-ops.md)。

## 评测

- 黄金集：[agent/golden-set.json](agent/golden-set.json)（40 题：结构化/表结构/代码/文档）；评测脚本 `scripts/agent-eval.ps1`。

## 接入

- 前端工作台：`whale-frontend/platform-apps/whale-agent`（8004）。
- MCP / IDE 接入：[ops/agent-mcp.md](ops/agent-mcp.md)。
- IM 机器人：`whale-im` 会话内调 `POST /agent/chat`（SSE），透传 `X-User-Id` / `X-Permission-Codes`。
