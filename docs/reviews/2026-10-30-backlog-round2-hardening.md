# 余项加固批次检查报告（T-14 / T-17 / T-26 / T-27 / T-07 / T-28）

> 日期：2026-10-30 ｜ 范围：盘点 [2026-10-30-workspace-backlog-inventory.md](2026-10-30-workspace-backlog-inventory.md) 中「无环境阻塞」的 P2/P3 余项
> 对照清单：[code-review-checklist.md](../checklists/code-review-checklist.md)
> 关联 devlog：[2026-10-30.md](../devlog/2026-10-30.md)「余项加固批次」「前端单测基线」「smoke 公共库收敛」

## 一、变更摘要与自检

| 项 | 变更 | 自检要点 | 结论 |
|---|---|---|---|
| T-14 | `PermissionCacheMutex` 补放行语义注释；`DistributedLock#failFast` 加使用约束；`AuthArchitectureTest` 新增 ArchUnit 白名单规则 | 规则用 `JavaAnnotation#tryGetExplicitlyDeclaredProperty("failFast")` 只识别**显式**关闭，不误伤默认值；白名单仅缓存互斥；注释说明最坏后果（多一次回源），无并发写坏风险 | 通过 |
| T-17 | `pyproject.toml` 增 `[tool.ruff]`；CI `ai-pytest` job 增 `uvx ruff check .` | 规则集仅 `E9/F63/F7/F82`（一定错误类），不引入风格/导入噪音；mypy 记豁免并写明理由 | 通过（ruff 待首次 CI 执行验证） |
| T-26 | `docs/README.md` 新增「历史文档登记」 | 重复口径与 roadmap 2026-10-22 决议一致；`wave5.35`/`wave5.61` 别名说明与真实文件名逐一核对 | 通过 |
| T-27 | 新增根 `.mailmap` | 仅影响 `shortlog` 展示，不改写历史；两署名同邮箱合并为 1 个身份（323 commits） | 通过 |
| T-07（第一批） | `@bluewhale/http` 10 例、`@bluewhale/whale-auth` 6 例 vitest | 断言覆盖安全关键路径（CSRF/单飞续期/令牌不落盘）；测试不引 jsdom，仅最小 stub；无新增运行依赖 | 通过 |
| T-28 | `lib/common.ps1` 新增统一 `Invoke-Api`，删除 7 处重复实现与 4 处只被其使用的 `Json` | 签名兼容位置参数与显式 headers；`$null` Headers 不写入 splat；`Invoke-Sql/Invoke-Query` 因返回语义不同保留 | 通过 |
| T-18（试点） | `whale-pay` 新增 `PayArchitectureTest`（3 条分包依赖规则） | 规则方向 `controller → service → mapper → entity`；只扫生产类；先核对各包实际 import 再定规则，避免误报 | 通过 |

## 二、验证记录

- 后端：`mvn -o -pl whale-services/whale-service-auth/whale-service-auth-architecture-test -am test -Dtest=AuthArchitectureTest` → **4 用例 0 失败**（3 条分层规则 + 1 条 failFast 白名单规则）。
- 后端：`mvn -o -pl whale-services/whale-pay test -Dtest=PayArchitectureTest` → **3 用例 0 失败，BUILD SUCCESS**。
- 前端：`pnpm --filter @bluewhale/http test` **10/10**；`pnpm --filter @bluewhale/whale-auth test` **6/6**；两包 `eslint src --max-warnings 0` 通过；`pnpm turbo run test` **9 tasks 成功**（含 whale-ui 17 例）；`pnpm install --no-frozen-lockfile` 后 lockfile 仅 +6 行。
- 脚本：`scripts/smoke` 33 个 ps1 以 UTF-8 读取后 `Parser::ParseInput` 语法错误 **0**；全目录 `function Invoke-Api` 仅剩 `lib/common.ps1`。
- 统计：`git shortlog -sne HEAD` = 1 个身份。
- 未执行（环境限制）：ruff 本地无法运行（本机 Python 3.15 在沙箱内启动失败）；smoke 脚本实跑需网关/中间件（B-02）。

## 三、遗留

- T-17：mypy 类型门禁独立批次接入；ruff 首次 CI 运行结果需观察。
- T-07 第二批：页面级组件测试与覆盖率门禁。
- T-18：`whale-pay` 试点已落地；其余单模块服务（doc/form/message/workflow/archive/im 等）按同模式逐服务补规则。
- T-28 剩余：3 处 `Invoke-Sql/Invoke-Query` 保留（mysql 路径与返回语义不同）。
- 环境阻塞项（B-01/B-02/B-03/B-07）与 T-08 E2E 进 CI 未变。
