# Checklist：Wave 5.14 前端日志 SDK `whale-log-sdk`（Wave B）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含通道③④需求、契约、验收口径

## 二、包骨架与类型

- [x] 2.1 `frontend/shared-packages/whale-log-sdk/` 存在，`package.json` 名 `@bluewhale/whale-log-sdk`，`main/module/types` 指向 `dist`
- [x] 2.2 `tsconfig.json` 继承 `../../tsconfig.base.json`；`tsup.config.ts` 配置 `esm+cjs` + `dts`
- [x] 2.3 `src/constants.ts` 集中端点/上限 50/默认参数/掩码键；`src/types.ts` 定义 `LogSdkConfig`/`LogItem`/`LogBatchRequest`

## 三、核心能力

- [x] 3.1 `mask.ts`：递归敏感键掩码 + URL query 掩码（密码/令牌/手机号/身份证/银行卡）
- [x] 3.2 `console.ts`：劫持 `debug/info/warn/error`，保留原输出、`destroy()` 可还原、内部用原始 console（防递归）
- [x] 3.3 `behavior.ts`：`PAGE_VIEW`（history pushState/replaceState + popstate）、`CLICK`（`[data-track]`）、`SUBMIT`
- [x] 3.4 `batch.ts`：采样 / 连续重复去重 / 满批或定时 flush / **分片 ≤50** / 指数退避重试
- [x] 3.5 `client.ts`：`fetch` 上报 + `X-Trace-Id` 头 + 上报不触发自身埋点（回环防护）
- [x] 3.6 `sdk.ts`/`index.ts`：`initLogSdk` 与 `LogSdk{track/log/flush/destroy}` 导出；`destroy` 清理监听/定时器/队列

## 四、构建与验证

- [x] 4.1 `pnpm --filter @bluewhale/whale-log-sdk build` 产出 `dist/index.js`/`index.mjs`/`index.d.ts`，无 TS 错误
- [x] 4.2 `node scripts/smoke.mjs` 断言全过（掩码 / 去重 / 分片 ≤50 / 退避 / URL 掩码）
- [x] 4.3 `whale-platform` 已接入 `initLogSdk`，`pnpm -r build`（或 turbo build）整体通过
- [x] 4.4 自检 D6（前端魔法值常量化）/ E12（前端格式）无违规

## 五、留痕与销账

- [x] 5.1 检查报告 `docs/reviews/2026-10-18-wave5.14-whale-log-sdk.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号（含 D6/E12）
- [x] 5.2 devlog 追加本波条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 5.3 台账销账：`register.md`（REQ-SEC-006 Wave B）、`next-steps.md`
- [x] 5.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 六、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：**通过**
