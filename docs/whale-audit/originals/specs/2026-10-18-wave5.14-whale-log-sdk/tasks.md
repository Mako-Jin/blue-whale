# Tasks：Wave 5.14 前端日志 SDK `whale-log-sdk`（Wave B）

> 关联 spec：[spec.md](./spec.md)
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [x] B1 | 包骨架：`whale-frontend/shared-packages/whale-log-sdk/`（`package.json`/`tsconfig.json`/`tsup.config.ts`，镜像 `whale-common-tools`）+ `src/constants.ts` + `src/types.ts` | 无 | 高 | 可被 pnpm workspace 识别；类型齐备 | ✅ |
| [x] B2 | `src/mask.ts`：递归敏感键掩码 + URL query 掩码（纯函数） | B1 | 高 | 掩码覆盖密码/令牌/手机号/身份证/银行卡 | ✅ |
| [x] B3 | `src/console.ts`：劫持 `console.debug/info/warn/error`，保留原输出、可还原、防递归 | B1、B2 | 高 | console 采集且原行为不变 | ✅ |
| [x] B4 | `src/behavior.ts`：`PAGE_VIEW`（history 补丁 + popstate）、`CLICK`（`[data-track]`）、`SUBMIT` | B1、B2 | 高 | 三类事件可采集 | ✅ |
| [x] B5 | `src/batch.ts` + `src/client.ts`：队列/采样/去重/满批或定时 flush/分片 ≤50/指数退避；`fetch` 上报 + 请求头 | B3、B4 | 高 | 批量与可靠性逻辑正确 | ✅ |
| [x] B6 | `src/sdk.ts` + `src/index.ts`：`initLogSdk(config)` → `LogSdk`{`track`/`log`/`flush`/`destroy`}，barrel 导出 | B5 | 高 | 公开 API 可用 | ✅ |
| [x] B7 | 冒烟：`scripts/smoke.mjs`（无框架，基于构建产物断言）+ `pnpm --filter @bluewhale/whale-log-sdk build` | B6 | 高 | 构建产出三件；冒烟断言全过 | ✅ |
| [x] B8 | 集成：`whale-platform` `main.tsx` 调 `initLogSdk` + 其 `package.json` 增依赖；`pnpm -r build`（或 turbo build） | B7 | 中 | 应用可构建，SDK 初始化不抛错 | ✅ |
| [x] B9 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.14-whale-log-sdk.md` + devlog 追加 + 台账销账（`register.md` REQ-SEC-006 Wave B / `next-steps.md`） | B8 | 中 | 四类信息齐备 | ✅ |
| [x] B10 | 自动 commit + push（Gitee origin/main） | B9 | 中 | 工作区干净且 `main == origin/main` | ✅ |

## 实施注意（给实现者的事实约束）

- **契约不可改**：严格按 spec §二 的 `LogBatchRequest`/`LogItem` 字段名与端点上报；`items` 上限 **50**，SDK 侧自动**分片**（勿依赖后端报错）。
- **包模板**：镜像 `whale-common-tools`（`tsup` + `tsconfig` 继承 `../../tsconfig.base.json`）；**不要**引入 vitest/jest/jsdom 等新测试基建，验证用无依赖 `scripts/smoke.mjs`。
- **回环防护**：SDK 内部日志必须用**原始 console 引用**，禁止经被劫持的 console 输出；上报请求本身不得触发行为埋点。
- **隐私优先**：任何上报前（console message/stack、behavior payload、pageUrl query）先过 `mask.ts`；掩码键与后端 `whale.audit.sensitive.keys` 内置规则对齐。
- **常量外置（D6）**：端点、上限 50、默认批量/采样/退避参数、掩码键名一律 `src/constants.ts` 常量，禁止散落魔法值。
- **可销毁**：`destroy()` 必须还原 console、移除事件监听、清空定时器与队列（避免测试/热更新残留）。
- **不阻断业务**：SDK 任何异常（含上报失败）都不得冒泡到业务代码；`enabled=false` 时近乎零开销。
- **不改后端**：本波纯前端；后端契约与排除路径已由 Wave 5.10/5.12 提供。

## 待做项（本波明确不做，登记备查）

- **MQ 汇聚（AUTH-10）**、**保留期扩多表**（Wave 5.12 处理）——非前端范围。
- **跨应用统一接入**：本波仅接入 `whale-platform` 基座；admin/portal 等应用按需后续接入。
- **前端单测框架**：不引入（用冒烟脚本替代）。
