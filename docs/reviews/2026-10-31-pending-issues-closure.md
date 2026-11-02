# 盘点整改清单收尾记录（N-01 ~ N-10）

> 日期：2026-10-31 ｜ 范围：[pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) §五「2026-10-31 盘点新增项」全部编号
> 前置批次：[2026-10-31 安全快修与文档同步检查报告](2026-10-31-security-quickfix-and-doc-sync.md)
> 用途：本轮「能修即修」结束后的**收尾台账**——逐项给出当前状态、证据、剩余动作与解锁条件；后续只按本文件推进，避免清单口径漂移。

## 一、结论

| 类别 | 数量 | 编号 |
|---|---|---|
| 已修复并提交 | 8 | N-01、N-02、N-03、N-04、N-05、N-06、N-07（2026-11-02 复验闭环）、N-10 |
| 环境/决策阻塞 | 1 | N-09（T-07 / T-08 / 其余服务越层）——N-08 三项阻塞（B-01 / B-09 / B-10）已于 2026-11-02 全部闭环 |

**代码与文档层面的问题已全部修复并入库**；剩余 3 项均**不由代码改动可解**（需用户确认删文件、需浏览器与环境、需更大范围专项），因此本轮进入收尾，不再继续扩大改动面。

## 二、逐项状态

| 编号 | 原问题 | 状态 | 证据（代码 / 验证） |
|---|---|---|---|
| N-01 | 脚本硬编码本机 MySQL 口令 `515322` | ✅ 2026-10-31 | `scripts/dev/run-music.ps1:23`、`scripts/smoke/bw-contract-m2-smoke.ps1:17` 均为「未设置 `MYSQL_PASSWORD` 即抛错」，仓库内已无默认值 |
| N-02 | `scripts/agent-eval.ps1` 默认口令 `123456` | ✅ 2026-10-31 | 默认值为空，`-Password` / `WHALE_EVAL_PASSWORD` 二选一，缺失即 `throw`（`agent-eval.ps1:27-30`） |
| N-03 | 非 dev 环境 Cookie `secure=false` 仍可启动 | ✅ 2026-10-31 | `SecretPlaceholderChecker` 非 dev 分支 `throw`（`SecretPlaceholderChecker.java:47-50`）；单测 4/4 |
| N-04 | 知识桶白名单隐式前缀匹配合放宽 | ✅ 2026-10-31 | `FileKnowledgeSyncService` 改精确匹配 + 显式 `*`；`AgentProperties` 加 `@RefreshScope`；`FileKnowledgeSyncServiceTest` 17/17 |
| N-05 | 缺镜像 / Maven 依赖供应链扫描 | ✅ 2026-10-31 | `security-scan.yml` 新增 `trivy-fs`（SARIF）、`backend-dependency-check`（OWASP 周/手动） |
| N-06 | 文档状态失真（音乐 M1/M3–M5、auth 拆分/解耦） | ✅ 2026-10-31 | `architecture.md` / `service-catalog.md` / `roadmap.md` / `music-service-ops.md` / auth `tasks.md` / 解耦 `spec.md` 已同步 |
| N-07 | 仓库卫生：`skills/` 5 个未跟踪 zip | ✅ 2026-11-02 | `.gitignore:107` `/skills/*.zip` 已生效；2026-11-02 复验 `Test-Path skills` = False，zip 随目录删除，无物理残留 |
| N-08 | 环境阻塞（Docker 全链路 / 浏览器 E2E / 通知链路） | ⛔ 阻塞 | 见 §三.1 |
| N-09 | 质量门禁大项（T-07 / T-08 / 其余越层） | 🟡 部分 | T-17（mypy 阻断）、T-18（agent / archive / im）已完成；T-07 / T-08 与其余服务专项仍开放，见 §三.2 |
| N-10 | 前端依赖审计非阻断 | ✅ 2026-10-31 | `security-scan.yml:97` `pnpm audit --prod --audit-level=critical` 已为阻断步骤 |

## 三、未完成项与解锁条件

### 3.1 N-08 环境阻塞

| 子项 | 当前探测（2026-10-31 复验） | 解锁条件 / 验收口径 |
|---|---|---|
| B-01 Docker 全链路 | ~~守护进程未运行~~ **已解锁（2026-11-02）**：Docker Desktop 29.9.2 启动；`infrastructure/docker/.env`（端口避让 + 凭据 + daocloud 镜像站，不入仓）→ `docker compose up` **8/8 容器 healthy**（Nacos v3 healthcheck 遗留 TODO 一并修复）；容器 MySQL 17 业务库初始化 / Redis / Nacos v3 管理员初始化+登录 / RabbitMQ API / Nginx TLS 逐项验证；**smoke 5/5 全绿**含 auth 缓存广播「Redis 重启后恢复 PASSED」（[devlog 2026-11-02](../devlog/2026-11-02.md)） | ~~compose up + smoke 全绿~~ 已达成；容器化后端应用镜像编排（`docker-compose.prod.yml`）按实机部署节奏另行推进 |
| B-09 浏览器 E2E | ~~无 Chromium 二进制~~ **已解锁（2026-11-02）**：Chromium 就位 + 宿主中间件起服 + gateway/auth/workflow/form 重建起服；修复 2 个后端真实 Bug（`LoginResponse.userId`、CSRF Cookie Path）与 2 个测试缺陷后 **Playwright E2E 8/8 全部通过**（[devlog 2026-11-02](../devlog/2026-11-02.md)）；`.github/workflows` 仍无 e2e job | ~~逐页 E2E 回归~~ 首轮实机回归已全绿；后续扩页按需推进；E2E 入 CI 与 T-08 合并推进 |
| B-10 通知链路 | ~~8094 未监听~~ **已解锁（2026-11-02）**：`whale-message` 8094 重建起服、`/actuator/health` UP、Nacos 注册 healthy；内网 `/message/internal/send` 实测 INAPP 落 `msg_record`+`msg_delivery`（SENT）、幂等键重发 `duplicate=true`、EMAIL 未启用记 MOCKED「失败仅告警」（[devlog 2026-11-02](../devlog/2026-11-02.md)） | ~~起服 + 通知开关验证~~ 通知链路实机验证已通过；音乐评论通知开关（`comment-notify-enabled`）保持默认关闭，接入真实场景时按需开启 |

### 3.2 N-09 质量门禁余量

| 子项 | 现状 | 建议 |
|---|---|---|
| T-07 前端页面级单测与覆盖率 | ✅ 已落地三批（2026-11-02）：首批 music 5 页 25 例；第二批 admin TemplateList 5 / OrderList 6 / ItemList 5 + whale-portal Inbox 4；第三批 whale-erp SupplierList 5 / SupplierForm 3 / 物料 List 4 / Form 3（页面级累计 55 例），`turbo run test` 门禁已有；剩余页面待逐步扩量 | 已覆盖 music 全部 5 页 + 消息/支付/配置/站内信 + erp 供应商与物料页；覆盖率阈值在剩余页面扩量后设置 |
| T-08 E2E 入 CI | ✅ 已闭环（2026-11-02）：`frontend-e2e.yml` 入库 + **GitHub Actions 首跑验证通过（run#14，8/8 用例，2.3m）**；排障链见 [devlog](../devlog/2026-11-02.md) | 后续作为 main/PR 阻断门禁运行 |
| 其余服务越层专项（T-18 余量） | ✅ 已闭环（2026-11-02）：agent / archive / im 原有门禁之外，对 contract / erp / mes / oa / pdm / pm / alert / audit / monitor / config 共 10 个服务全量扫描，五个维度（controller→entity、controller→mapper、dto/vo→entity、mapper→service、entity→外层）越层命中均为 0，直接补 10 个 ArchUnit 门禁（3–5 例/服务，347 例全绿）；`whale-gateway` 无 controller 与持久化层，本口径不适用 | 全部纳入 `mvn test` 门禁，后续新增接口自动兜底 |
| 后端覆盖率门禁（N-11，2026-11-02 新增） | ✅ 已闭环：`backend-test.yml` 的 `-Pcoverage-gate verify`（行 ≥ 0.40 / 分支 ≥ 0.30）此前 4 模块不达标；新增 10 个测试类 149 例后本地全部通过——music 0.549/0.552（196 例）、contract 0.429/0.310（100 例）、mes/pdm 0.840/0.786（各 16 例） | CI 观察 Backend Test 转绿；零测试模块（common-core/common-domain/auth-sdk）因缺 exec 被 JaCoCo skip 的盲区已在 [devlog](../devlog/2026-11-02.md) 留档，未登记为待办 |

### 3.3 N-07 仓库卫生（已闭环，2026-11-02）

- `.gitignore` 已忽略 `/skills/*.zip`，不会再误入库；
- 2026-11-02 复验 `skills/` 目录已不存在，5 个 zip 无物理残留；
- `.trae` 16 个文件为 2026-10-22 既定保留，**不按缺陷处理**（本条已关闭）。

## 四、本轮复验证据

```
git status -sb                     → 工作区干净，无未跟踪文件（skills/*.zip 已忽略）
git log                            → 3aaa488 之后 6 个整改提交（22:14），main 领先 origin 6 → 本文件提交后一并推送
docker version                     → Server 29.9.2（守护进程可用）
Get-NetTCPConnection 8094/8096/8080/8081/8085
                                   → 8096/8085/8081/8080 在监听；8094 未监听（B-10）
%LOCALAPPDATA%\ms-playwright       → 无浏览器二进制（B-09）
scripts/dev/run-music.ps1:23       → 未设 MYSQL_PASSWORD 即抛错（N-01）
scripts/smoke/bw-contract-m2-smoke.ps1:17 → 同上（N-01）
scripts/agent-eval.ps1:27-30       → 默认口令已移除（N-02）
SecretPlaceholderChecker.java:47-50 → 非 dev 且 Cookie 非 Secure 拒绝启动（N-03）
security-scan.yml:97 / 99 / 137    → pnpm audit 阻断 / trivy-fs / OWASP dependency-check（N-05、N-10）
```

> 说明：前次批次已记录的单测与 YAML 解析结论（auth 4/4、agent 17/17、`whale-ui` 34/34、mypy 2.4.1 exit=0、`code-quality.yml` / `security-scan.yml` YAML OK）仍在 [前置报告](2026-10-31-security-quickfix-and-doc-sync.md) §六.4 留档，本文件不重复执行。

## 五、提交记录（本轮及关联）

| 提交 | 内容 |
|---|---|
| `c522434` | `fix(security)`: 下线内置口令 + 非 dev Cookie Secure 守卫（N-01~N-03） |
| `233771d` | `feat(agent)`: 知识桶精确匹配 / 显式通配 / 热刷新（N-04） |
| `9a15f58` | `ci(quality)`: 供应链扫描、audit 与 mypy 阻断（N-05 / N-10 / T-17） |
| `1e17988` | `refactor(layer)`: controller 响应装配下沉与分层门禁（T-18 增量） |
| `841d6d8` | `test(whale-ui)`: FormRenderer 值转换单测（T-07 首批） |
| `7e63f1e` | `docs`: 音乐口径同步、auth spec 回填与整改记录（N-06） |
| 本文件 | `docs(review)`: 盘点清单收尾记录 |

## 六、风险与后续节奏

1. **N-01/N-03 是有意的安全取舍**：相关脚本不再开箱即用、非 dev 环境漏配 `WHALE_AUTH_COOKIE_SECURE=true` 会启动失败；部署清单需同步下发该变量。
2. **N-04 上线风险**：`included-buckets` 生产值必须与文件服务真实桶名逐一对齐（原前缀兜底已移除），否则知识同步会少采集。
3. **下一步建议顺序**：B-09 浏览器二进制（解锁 E2E 与 M5 逐页点检）→ B-10 起服 `whale-message` → T-07 页面级单测 → B-01 Docker 全链路 → 其余服务 T-18 越层专项。
4. 本文件为 §五 N-01~N-10 的**唯一收尾口径**；再次推进时更新本文件状态列与 `pending-issues` 勾选，不再新开平行清单。
