# 知识 Agent MCP 接入手册

> 适用服务：`whale-agent`（端口 8090，**直连服务端口，不经过网关**）
> 协议：MCP over HTTP（JSON-RPC 2.0 单次请求/响应，`protocolVersion = 2024-11-05`）
> 关联规划：[wave5.47-project-agent.md](../plans/wave5.47-project-agent.md) 轨 F
> 关联手册：[agent-index-ops.md](agent-index-ops.md)（索引运维）

---

## 一、能力与边界

MCP 端点为**只读**工具集，供 Trae / IDE 等本地 MCP 客户端复用同一检索内核：

| 工具 | 作用 | 关键参数 |
|---|---|---|
| `search_knowledge` | 代码/文档/DDL/配置的 BM25 语义检索，返回路径、行号与片段 | `query`（必填）、`topK`（默认 8）、`kinds`（`code,doc,sql,config`） |
| `get_fact` | 按类型/键精确查询结构化事实 | `type`、`key`、`limit`（默认 50） |
| `read_file` | 读取仓库内文本文件片段 | `path`（必填，仓库相对路径）、`startLine`、`endLine` |
| `list_services` | 服务目录与端口事实摘要 | 无 |

设计约束：

- **不暴露运行时事实**：`RUNTIME_*` 类型直接拒绝，避免绕过 `sys:agent:runtime:read` 权限体系。
- **不提供写操作**：不暴露索引重建、对话写入等接口。
- **路径封闭**：`read_file` 只允许仓库根内路径，`..` 穿越、绝对路径、符号链接越界均拒绝。
- **敏感文件黑名单**：`.env*`、`.pem/.key/.p12/.jks/.keystore`、`.git`、`node_modules`、`target`、`dist`、`work`、`logs`、`data/agent-index` 等目录段一律拒绝。
- **读取上限**：单次最多 400 行 / 2 万字符，单文件 >1 MB 拒绝，二进制（含 NUL）拒绝。

---

## 二、启用与鉴权

默认关闭；**同时配置启用开关与 Token 后**才对外提供工具。

| 配置项 | 环境变量 | 默认值 |
|---|---|---|
| `whale.agent.mcp.enabled` | `WHALE_AGENT_MCP_ENABLED` | `false` |
| `whale.agent.mcp.token` | `WHALE_AGENT_MCP_TOKEN` | 空（空则端点返回 503） |
| `whale.agent.mcp.max-search-results` | `WHALE_AGENT_MCP_MAX_SEARCH_RESULTS` | `20` |
| `whale.agent.mcp.max-read-lines` | `WHALE_AGENT_MCP_MAX_READ_LINES` | `400` |
| `whale.agent.mcp.max-read-chars` | `WHALE_AGENT_MCP_MAX_READ_CHARS` | `20000` |

启动示例（本机，凭据通过环境变量注入，不入仓）：

```powershell
$env:JAVA_HOME = 'D:\tools\installer\jdk\java21'
$env:MYSQL_PASSWORD = '<mysql-password>'
$env:SPRING_DATA_REDIS_PASSWORD = '<redis-password>'
$env:SPRING_CLOUD_NACOS_USERNAME = 'bluewhale'
$env:SPRING_CLOUD_NACOS_PASSWORD = '<nacos-password>'
$env:WHALE_AGENT_MCP_ENABLED = 'true'
$env:WHALE_AGENT_MCP_TOKEN = '<local-readonly-token>'
java -jar backend\whale-services\whale-agent\target\whale-agent.jar
```

鉴权头（二选一，常量时间比较）：

```
X-MCP-Token: <token>
Authorization: Bearer <token>
```

Token 建议使用一次性生成的随机串（≥ 24 字符），仅用于本机 IDE 客户端；更换后需重启服务。

---

## 三、JSON-RPC 交互示例

端点：`POST http://127.0.0.1:8090/agent/mcp`，`Content-Type: application/json`。

### 1. 初始化

```powershell
curl.exe -s -X POST "http://127.0.0.1:8090/agent/mcp" `
  -H "Content-Type: application/json" -H "X-MCP-Token: <token>" `
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"trae","version":"1.0"}}}'
```

返回 `protocolVersion`、`capabilities.tools`、`serverInfo{name:bluewhale-agent-mcp,version:1.0.0}` 与使用指引。

### 2. 列工具 / 调用工具

```powershell
# tools/list
curl.exe -s -X POST "http://127.0.0.1:8090/agent/mcp" `
  -H "Content-Type: application/json" -H "X-MCP-Token: <token>" `
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# tools/call -> search_knowledge
curl.exe -s -X POST "http://127.0.0.1:8090/agent/mcp" `
  -H "Content-Type: application/json" -H "X-MCP-Token: <token>" `
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_knowledge","arguments":{"query":"MCP 接入","topK":3}}}'

# tools/call -> read_file（行区间）
curl.exe -s -X POST "http://127.0.0.1:8090/agent/mcp" `
  -H "Content-Type: application/json" -H "X-MCP-Token: <token>" `
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"read_file","arguments":{"path":"docs/plans/roadmap.md","startLine":1,"endLine":5}}}'
```

成功响应的 `result.content[0].text` 为 JSON 字符串（与 HTTP 接口同构），`isError=false`；
参数或安全校验失败时 `isError=true` 且 `text` 内为 `{"error":"..."}`；
协议级错误（未知方法、坏 JSON）使用标准 `error.code`（`-32601` / `-32700`）。

### 3. 通知

`notifications/initialized`、`notifications/cancelled` 等无 `id` 的请求返回 **HTTP 202** 且无响应体。

---

## 四、状态码约定

| HTTP | JSON-RPC | 场景 |
|---|---|---|
| 200 | — | 正常响应（含协议错误与工具错误） |
| 202 | — | 通知（无 `id`）已受理 |
| 400 | `-32700` | 空请求体或无法解析 |
| 401 | `-32002` | Token 缺失或不匹配 |
| 503 | `-32001` | MCP 未启用或未配置 Token |

---

## 五、IDE 客户端配置

以 Trae / MCP 客户端为例（HTTP 传输）：

```json
{
  "mcpServers": {
    "bluewhale-agent": {
      "url": "http://127.0.0.1:8090/agent/mcp",
      "headers": { "X-MCP-Token": "<local-readonly-token>" }
    }
  }
}
```

> 端点**不经网关**，因此不参与 JWT 鉴权与 `internal-deny` 规则；请勿把 8090 暴露到公网。
> 共享环境的多个开发者需各自启动本地 agent 服务或使用不同的只读 Token。

---

## 六、验收清单

| 检查项 | 期望 |
|---|---|
| `initialize` | `protocolVersion=2024-11-05`，`serverInfo.name=bluewhale-agent-mcp` |
| `tools/list` | 恰好 4 个工具，含 `inputSchema` |
| `list_services` | 返回 `services` 与 `ports`，`whale-agent` 端口 8090 |
| `get_fact`（`PORT`/`whale-agent`） | `count=1`，值含 `port=8090` |
| `get_fact`（`RUNTIME_SERVICE`） | `isError=true`，提示不暴露运行时事实 |
| `read_file`（`docs/plans/roadmap.md` 1–5 行） | 含 `totalLines`，`truncated=false` |
| `read_file`（`../../Windows/win.ini`） | `isError=true`，「路径必须位于仓库内」 |
| 无 Token / 错误 Token | HTTP 401，`code=-32002` |
| 未知方法 | `code=-32601` |
| 空请求体 / 坏 JSON | HTTP 400 / `code=-32700` |
