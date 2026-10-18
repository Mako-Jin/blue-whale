# Wave 5.14 · 前端日志 SDK `whale-log-sdk`（Wave B，REQ-SEC-006）

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-SEC-006（多通道日志采集，通道③前端开发者日志 / ④前端用户行为）、REQ-QA-002、REQ-DOC-002、REQ-ARCH-004 |
| 任务映射 | Wave 5.10 spec 的 **Wave B**（前端 `whale-log-sdk`）；接口契约已由 Wave 5.10 后端冻结 |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [Wave 5.10 spec](../2026-10-18-wave5.10-multichannel-log/spec.md) §3.2/§3.3 + tasks 待做项；[审计日志服务多通道完善方案](../../../.trae/documents/审计日志服务多通道完善方案.md)；[naming-conventions.md](../../naming-conventions.md) §八（`@bluewhale/whale-log-sdk` 已登记）；`whale-common-tools`（包模板） |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 自检留报告（REQ-QA-002）→ 构建验证 → 提交推送 |

> 本波在 Wave 5.12 后端补强之后执行。SDK 消费 Wave 5.10 已冻结的上报契约（`POST /audit/log/frontend/batch`、`POST /audit/log/behavior/batch`）。

---

## 一、需求项

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-SEC-006 通道③ | **前端开发者日志**：劫持 `console.debug/info/warn/error`（保留原行为），批量上报 `frontend_log` | console 调用被采集且原输出不变 |
| REQ-SEC-006 通道④ | **前端用户行为**：自动 `PAGE_VIEW`/`CLICK`/`SUBMIT` + 手动 `track()`，上报 `behavior_log` | 行为可被采集与查询 |
| REQ-SEC-006 | **批量/采样/去重/退避**：条数或间隔触发 flush；`sampleRate` 采样；连续重复去重；失败指数退避重试 | 满批/定时即上报；超限自动分片（≤50/请求） |
| REQ-SEC-006 | **隐私掩码**：上报前递归掩码敏感键（密码/令牌/手机号/身份证/银行卡）+ 页面 URL query 掩码 | 上报体无明文敏感值 |
| REQ-ARCH-004 | 包名 `@bluewhale/whale-log-sdk`，落 `frontend/shared-packages/` | 随 pnpm workspace 自动纳入构建 |
| REQ-QA-002 | 构建验证 + 冒烟脚本 + 检查报告 | 构建 SUCCESS；冒烟断言全过；报告对应清单编号 |

---

## 二、接口契约（消费 Wave 5.10 冻结契约，勿改）

### 2.1 上报接口

| 方法 | 路径 | 鉴权 | 体 |
|---|---|---|---|
| POST | `/audit/log/frontend/batch` | `permitAll` | `LogBatchRequest` |
| POST | `/audit/log/behavior/batch` | `permitAll` | `LogBatchRequest` |

`LogBatchRequest{ traceId, userId, clientId, pageUrl, items: LogItem[] }`，`items` 上限 **50**（超出后端 `PARAM_ERROR`，SDK 侧须自动分片）。`Content-Type: application/json`。

`LogItem{ logLevel, message, stack, browser, eventType, eventName, target, payload, timestamp }`：
- 通道③用：`logLevel`(`DEBUG/INFO/WARN/ERROR`) / `message` / `stack`(ERROR) / `browser` / `timestamp`；
- 通道④用：`eventType`(`PAGE_VIEW/CLICK/SUBMIT/CUSTOM`) / `eventName` / `target` / `payload`(JSON 字符串) / `timestamp`。

- `traceId`：优先取会话内既有值（`sessionStorage`，与后端/网关 `X-Trace-Id` 对齐），缺失则生成；
- `clientId`：一次浏览器会话唯一（`sessionStorage` 持久）；
- `userId`：从本地登录态读取（**不可信**，仅作后端 `X-User-Id` 缺失兜底）；
- `pageUrl`：当前 `location.href`（query 已掩码）。

### 2.2 请求头

- `X-Trace-Id`：随 `LogBatchRequest.traceId` 一并置头（便于网关串联）；
- **回环防护**：SDK 自身不得为上报请求再产生接口日志/行为埋点（上报路径已在后端 `exclude-paths` 排除；SDK 内部日志使用**原始 console**，禁止递归）。

---

## 三、包与组件布局

新建 `frontend/shared-packages/whale-log-sdk/`，镜像 `whale-common-tools` 包模板（pnpm workspace + `tsup` + `tsconfig` 继承 `tsconfig.base.json`）：

| 文件 | 职责 |
|---|---|
| `package.json` | `@bluewhale/whale-log-sdk`（`main/module/types`=dist，`files:[dist]`，scripts：`dev`/`build`/`clean`/`lint`/`smoke`） |
| `tsconfig.json` | 继承 `../../tsconfig.base.json`，`outDir: dist` |
| `tsup.config.ts` | `entry: src/index.ts`，`format: [esm,cjs]`，`dts`，`sourcemap`，`clean` |
| `src/index.ts` | barrel：导出 `initLogSdk`、`LogSdk`、类型与常量 |
| `src/constants.ts` | 端点、上限（50）、默认批量/采样/掩码键等**常量**（D 类魔法值外置） |
| `src/types.ts` | `LogSdkConfig` / `LogItem` / `LogBatchRequest` / `LogLevel` / `EventType` |
| `src/mask.ts` | 递归隐私掩码 + URL query 掩码（纯函数，可冒烟） |
| `src/console.ts` | console 劫持（保存原始引用、还原、防递归） |
| `src/behavior.ts` | 自动埋点：`PAGE_VIEW`（pushState/replaceState/popstate）、`CLICK`（`[data-track]`）、`SUBMIT` |
| `src/batch.ts` | 通道队列：采样 / 去重 / 满批或定时 flush / 分片(≤50) / 指数退避 |
| `src/client.ts` | `fetch` 上报 + 请求头装配（可注入 transport 便于冒烟） |
| `src/sdk.ts` | `initLogSdk(config)` → `LogSdk`{ `track` / `log` / `flush` / `destroy` } |
| `scripts/smoke.mjs` | 无框架 Node 冒烟：对构建产物断言（掩码/去重/分片/退避/URL 掩码） |

### 3.1 配置项（`LogSdkConfig`，全部有默认值）

| 配置 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `baseUrl` | `''`（同源相对路径） | 上报基地址 |
| `frontendEndpoint` / `behaviorEndpoint` | `/audit/log/frontend/batch` / `/audit/log/behavior/batch` | 端点 |
| `console.enabled` / `console.levels` | `true` / `['DEBUG','INFO','WARN','ERROR']` | console 劫持 |
| `behavior.autoPageView` / `behavior.autoClick` / `behavior.autoSubmit` | `true` | 自动埋点开关 |
| `behavior.trackAttr` | `data-track` | 点击埋点选择器属性 |
| `batch.size` / `batch.intervalMs` | `20` / `5000` | 触发 flush 阈值 |
| `batch.maxRetries` / `batch.backoffBaseMs` | `2` / `1000` | 退避重试 |
| `sampleRate` | `1.0` | `[0,1]`，`0` 全不采 |
| `privacy.sensitiveKeys` | 内置规则（与后端 `whale.audit.sensitive.keys` 对齐） | 附加掩码键名（并集） |
| `resolveUserId` | 读取本地登录态 | 用户ID解析钩子 |

---

## 四、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 构建 | `pnpm --filter @bluewhale/whale-log-sdk build`（tsup + dts） | 产出 `dist/index.js`/`index.mjs`/`index.d.ts`，无 TS 错误 |
| 冒烟 | `node scripts/smoke.mjs`（构建产物） | 掩码生效 / 去重生效 / 分片 ≤50 / 退避重试次数正确 / URL query 掩码 |
| 集成 | `whale-platform` `main.tsx` 调用 `initLogSdk` | 应用可构建；SDK 初始化不抛错 |
| 全量前端 | `pnpm -r build`（或 turbo build） | 全部前端包构建成功 |
| 规范 | 自检报告 D6（前端魔法值）/ E12（前端格式） | 常量外置、格式一致 |

---

## 五、风险与残余

| 风险 | 影响 | 处置 |
|---|---|---|
| console 劫持影响调试 | 团队感知 | 保留原始输出、`console.enabled=false` 可关、`destroy()` 还原 |
| 上报回环 | 自生自报放大 | 后端 `exclude-paths` 已排除上报路径；SDK 内部不记录自身上报 |
| 隐私泄露 | 敏感值入日志 | 上报前递归掩码 + URL query 掩码（与后端脱敏键对齐） |
| 前端无测试框架 | 验证手段 | 采用**无依赖 Node 冒烟脚本**（不引入 vitest/jest，避免新增基建） |
| MQ / 保留期 | 不属前端范围 | 后端侧另行处理 |

---

## 六、执行顺序

```
1) 文档先行：本 spec + tasks + checklist
2) 包骨架（package.json/tsconfig/tsup）+ constants/types
3) mask / console / behavior / batch / client / sdk 实现
4) scripts/smoke.mjs + 构建 + 冒烟断言
5) whale-platform 集成（main.tsx 调 initLogSdk）+ 全量前端构建
6) 检查报告 + devlog + 台账 → commit + push
```
