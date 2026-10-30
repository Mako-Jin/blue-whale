# 环境验证批次检查报告（B-02 / B-03 / B-06 / B-07）

> 日期：2026-10-30 ｜ 范围：盘点 [2026-10-30-workspace-backlog-inventory.md](2026-10-30-workspace-backlog-inventory.md) 中除 Docker（B-01）外的环境阻塞项
> 关联 devlog：[2026-10-30.md](../devlog/2026-10-30.md)「B-02 / B-03 / B-06 / B-07 环境验证批次」
> 前置说明：用户明确本轮跳过 Docker 验证（B-01 未闭环），其余阻塞项在本机实机尽力关闭。

## 一、结论

| 条目 | 结论 | 依据 |
|---|---|---|
| B-02 中间件与实机链路 | **闭环** | MySQL/Redis/Nacos 监听；auth 8081 + gateway 8080 启动；网关登录 `code=0` |
| B-02 DDL 幂等 | **闭环** | 21 份 `init.sql` 首跑/二跑 0 失败 |
| B-02 实库漂移 | **闭环（有说明）** | 21 库补齐后 `information_schema` 比对 0 差异；视图列误报、`pay_order` 缺列已修 |
| V-01 / V-02 实机复核 | **闭环** | 网关 internal 路径 403；直连服务无/错令牌 403、正确令牌 200 |
| B-03 本地证书 | **闭环（本地）** | `scripts/dev/gen-dev-certs.ps1` 实跑签发成功；生产证书仍待运维 |
| B-06 依赖审计 | **闭环（留档）** | 官方 registry 审计清单 + CI 定期 `frontend-audit` 作业 |
| B-07 Agent 索引重建 | **闭环** | 全量重建 2989 文件 / 19056 块 / 16.2s；台账 0 行 → 白名单外检索 0 |
| B-01 Docker | **未验证（用户跳过）** | 隶属于 Docker/容器矩阵，留待环境具备 |

## 二、变更清单

| 文件 | 变更 |
|---|---|
| `scripts/dev/gen-dev-certs.ps1` | 新增：本地自签 TLS 证书生成（可复现，自动生成最小 `openssl.cnf`） |
| `infrastructure/docker/nginx/ssl/README.md` | 开发环境证书改为指向上述脚本，并注明 `*.pem/*.key/*.crt` 已被 `.gitignore` 忽略 |
| `.github/workflows/security-scan.yml` | 新增 `frontend-audit` job（schedule / workflow_dispatch，pnpm 10.33.0 + Node 24，审计 JSON 工件保留 30 天） |

> 除上述 3 个文件外，本轮未改业务代码；B-02/B-07 为环境与实机验证，无代码变更。

## 三、验证记录

- 中间件：`netstat` 确认 3306 / 6379 / 8848 / 8849 监听；Nacos 日志 `Nacos started successfully in stand alone mode with external storage`。
- DDL 冒烟：`TOTAL=21 FIRST_FAIL=0 SECOND_FAIL=0`（临时库 `<db>_ddltest` 用后即删）。
- 网关链路：`POST http://127.0.0.1:8080/auth/login` → HTTP 200 + `code=0` + accessToken（309 字符，`mustChangePassword=true`）。
- V-01：经网关 `GET /file/internal/ping`、`GET /archive/internal/ping` → 403；`GET /auth/me` 无令牌 → 401。
- V-02：直连 `GET http://127.0.0.1:8081/user/internal/ping` —— 无令牌 403 / 错误令牌 403 / `X-Internal-Token` 正确 200。
- B-03：`openssl x509 -noout -subject -dates -ext subjectAltName` → `CN=bluewhale.local`、SAN 含 `*.bluewhale.local`、`localhost`、`127.0.0.1`，有效期 825 天。
- B-06：`pnpm audit --registry=https://registry.npmjs.org --json` → 3 critical / 1 high / 14 moderate / 2 low（运行期仅 react-router、@tiptap/core 为 moderate）。
- B-07：`whale-agent` 8090 启动 13.1s，`build-on-startup` 触发全量重建；`POST /agent/index/rebuild` → `accepted=true`；状态 `BUILDING → READY`；`indexVersion=20261031000732`、`fileCount=2989`、`chunkCount=19056`、`buildMillis=16176`；`GET /agent/search?q=网关路由` 命中均为仓库 `docs/**`。

## 四、风险与遗留

- **B-01 Docker 全链路未验证**：镜像构建、compose 拓扑、Grafana 看板 provisioning、Nginx HTTPS 入口均未在容器内复跑。
- **B-07 的「白名单外检索为 0」在本机是平凡成立**：`agent_knowledge_file/doc` 两表 0 行（未运行 whale-file 8085 的文件知识同步），因此只验证了「全量重建清空旧块 + 台账重置为 PENDING」机制，未覆盖真实桶数据。待 file 服务在线后按 `included-buckets=bluewhale,whale-archive` / `excluded-buckets=whale-doc` 复验。
- **B-06 未处置漏洞**：审计结果仅留档，critical 项集中在开发期依赖（vitest/vite/esbuild/tinypool）；运行期 2 个 moderate 需评估升级窗口。
- **实库重置副作用**：`db-sync.ps1` 存在性判断被 stderr 污染，对已存在库也执行了 `init.sql`。已复核无跨库污染，影响限于本地 dev 数据；后续脚本判定须只读 stdout。
- **T-03 口令已轮换**：`admin/123456` 失效，实机脚本需从启动日志取初始口令或显式设置 `WHALE_AUTH_ADMIN_INIT_PASSWORD`。
- 历史余项不变：T-07 第二批、T-18 越层治理、T-17 mypy、B-08② 文件知识可见范围待用户拍板。
