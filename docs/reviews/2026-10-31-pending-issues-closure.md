# 盘点整改清单收尾记录（N-01 ~ N-10）

> 日期：2026-10-31 ｜ 范围：[pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) §五「2026-10-31 盘点新增项」全部编号
> 前置批次：[2026-10-31 安全快修与文档同步检查报告](2026-10-31-security-quickfix-and-doc-sync.md)
> 用途：本轮「能修即修」结束后的**收尾台账**——逐项给出当前状态、证据、剩余动作与解锁条件；后续只按本文件推进，避免清单口径漂移。

## 一、结论

| 类别 | 数量 | 编号 |
|---|---|---|
| 已修复并提交 | 8 | N-01、N-02、N-03、N-04、N-05、N-06、N-07（2026-11-02 复验闭环）、N-10 |
| 环境/决策阻塞 | 2 | N-08（B-01 / B-09 / B-10）、N-09（T-07 / T-08 / 其余服务越层） |

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
| B-01 Docker 全链路 | Docker 守护进程**已可用**（`docker version` → `29.9.2`）；`docker compose config -q`（临时 env）exit=0；**但** compose 需 `.env`，且宿主已占用 3306 / 6379 / 8848（本机 MySQL、Redis、Nacos 服务） | 提供 `.env`（或改用覆盖端口 / 独立网络），执行 `docker compose up` 并跑 `bw-*-smoke.ps1` 全绿；届时产出实机报告 |
| B-09 浏览器 E2E | `%LOCALAPPDATA%\ms-playwright` **仅有 `.links` / `__dirlock`**，无 Chromium 二进制；`.github/workflows` 无 e2e job；本机管理员账号与登录态已具备（M5 报告 §4.9） | `pnpm exec playwright install chromium` 成功 → 按「登录 → 首页 → 搜索 → 播放 → 收藏 → 建歌单 → 评论 → 审核 → 申诉 → 下架」逐页回归；或提供浏览器工具环境 |
| B-10 通知链路 | 8094 **未监听**（`whale-message` 未起服）；音乐 `comment-notify-enabled` 默认关闭 | 起服 `whale-message` 8094 + Nacos 注册 → 开启通知开关，验证站内信落 `message_record` 且失败仅告警 |

### 3.2 N-09 质量门禁余量

| 子项 | 现状 | 建议 |
|---|---|---|
| T-07 前端页面级单测与覆盖率 | 仅共享包（`whale-ui` 34 例等）有单测，页面级（admin / 子应用）缺覆盖率门禁 | 先为 music 5 页（SongList / CommentAudit / AppealAudit / MySongs / 播放器）补组件测试，再逐步扩展并设覆盖率阈值 |
| T-08 E2E 入 CI | 无 e2e workflow；本地 E2E 用例存在但依赖浏览器二进制 | 与 B-09 合并：Playwright 可用后新增 `frontend-e2e.yml`（cache 浏览器 + 起服编排） |
| 其余服务越层专项（T-18 余量） | agent / archive / im 已开启 controller→entity/mapper ArchUnit 门禁；其余服务未逐项排查 | 按「controller 不依赖 entity/mapper」规则逐个服务加 ArchUnit 规则，先扫后补 |

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
