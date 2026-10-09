# BlueWhale — 项目长期备忘

## 项目定位
大型全栈项目，前后端分离。工作空间：`D:\workspace\BlueWhale`。

## 参考项目（舰长指定的结构范本）
- `D:\workspace\platform-terminator` — 纯前端 monorepo：pnpm workspace、`common-*` 公共包、按业务域分组、qiankun 微前端、React
- `D:\workspace\SystemTerminator` — 纯后端 Maven 多模块：`sts-*` 体系，包名 `com.yaocode.sts`
> 新需求优先参考这两个项目的既有做法，不要另起结构。

## 顶层划分
`web/`（前端·pnpm）· `server/`（后端·Maven，包名 `com.bluewhale.sts`）· `docs/ scripts/ infra/`（横向层）

## Git 与文档约定
- 仓库根 `D:\workspace\BlueWhale`，分支 `main`
- Husky 钩子目录 `web/.husky`，用 Git 原生 `core.hooksPath` 挂载；沙箱下 husky 自动安装会因 `spawn git EBUSY` 失败 → 手动挂钩；钩子脚本内先 `cd "$(git rev-parse --show-toplevel)/web"`
- `.gitattributes` 强制 LF（husky shell 脚本变 CRLF 会在 Linux 上执行失败）
- `blender_mcp/` 已忽略（第三方独立仓库，舰长决定不纳入主仓）
- 提交信息走 Conventional Commits（`commit-msg` 钩子拦截）
- **舰长要求：每一步都完善文档**；对照表在 `docs/README.md`，ADR 在 `docs/decisions/`，开发与 AI 约定在 `CONTRIBUTING.md` / `AGENTS.md`
- 铁律：改代码必须同步改文档，同一次提交完成

## 技术栈
- 前端：React 19 + Vite 8 + TS **6.0.3**（7.x 不被 typescript-eslint 支持）+ React Router 7
- 微前端 qiankun 2.x；包管理 pnpm workspace + Turborepo；样式 Less
- 规范：ESLint 10（flat config）+ Prettier 3 + Stylelint 17 + commitlint/Husky/lint-staged + Vitest 5
- 后端：Java 21 + Spring Boot 3.3.5 + Spring Cloud 2023.0.3 + MyBatis-Plus 3.5.9（Maven 多模块）
- 契约：OpenAPI 3（`docs/api/`）

## 前端结构
- `packages/` 公共包（`common-*`）、`platform/` 应用、`elemental/` 预留
- 实际存在：`common-config`（共享工程配置）、`common-tools`（公共能力包）、`platform-portal`（qiankun 主应用）、根 `web/eslint.config.js`
- 规划的 `common-{theme,components,api}`、`platform-{user,admin}` **按需再建**，不空建
- 规范只维护一份在 `common-config`：子项目 `stylelint.config.js` 一行 re-export；`eslint.config.js` 须调用 `createEslintConfig()`

## 公共依赖与公共包治理（2026-10-09 定 · ADR-0002）
- **依赖版本统一**：`web/pnpm-workspace.yaml` 的 `catalog:` 段是唯一来源，各 `package.json` 写 `"catalog:"`
- **不建"存放依赖的包"** —— 会产生幽灵依赖（库可被引用却没声明，运行时解析不到）。新增依赖：先写 catalog 再引用
- **公共代码**按能力建包 `packages/common-<能力>`；建包前先问"是否只有一个应用会用"
- 新建公共包的 `eslint.config.js` 需 `...createEslintConfig()` 再追加自定义规则（如日志包关 `no-console`）

## logger（common-tools）关键决策
- 7 级严重度（trace/debug/info/success/perf/warn/error）+ 阈值过滤；**过滤前置于格式化**
- 级别映射到 `console.debug/log/warn/error`（参考实现全走 log，导致筛选与堆栈定位失效）
- 渲染三态自适应：`css`（浏览器 `%c`）/ `ansi`（终端真彩）/ `plain`（管道、`NO_COLOR`）
- 命名空间层级 `child()`、`withContext()`、主题按级别按字段深合并、`LogSink` 可替换输出通道
- 消息里的 `%o`/`%c` 必须转义（用零宽空格隔断，否则会吞掉后续参数）
- 参考实现共修复 16 项问题，清单见 `web/packages/common-tools/README.md`
- 演示：`pnpm --filter common-tools playground`（:3100），把控制台输出镜像渲染到页面

## 输出通道两个钩子，分工不可混（2026-10-09 定 · ADR-0004）
- `LogSink` 消费**渲染后的样子**（含 `%c` 样式串）→ 控制台、测试捕获、页面镜像
- `onRecord` 消费**结构化事实** `LogRecord` → 上报、落库、聚合
- **上报必须走 `onRecord`**：sink 级别有损（`info`/`success` 都压成 `'log'`），元数据被拼进字符串，只能反向解析
- `onRecord` 派发点在**级别过滤之后、格式化之前**；内部异常**静默吞掉**（防上报失败递归成雪崩）
- `LogRecord` 含 `level/namespace/timestamp/message/data/context` + `traceId/sessionId/route/release`
- 服务端聚合与查询**已实现**（`sts-server-log`）；**采样与脱敏未实现**，等有真实生产流量再做；生产可复用成熟 APM（Sentry/RUM）

## 配置分域（2026-10-09 定 · ADR-0003）
- 三层：**密钥级**（Vault/KMS）· **部署级**（Nacos/Apollo）· **业务运行期**（自研，走 OpenAPI 契约）
- **自研范围只有业务运行期配置**；下两层自研是浪费，密钥层写错就是安全事故
- 客户端配置五条底线：首屏零阻塞、兜底默认值在客户端、版本化+缓存+变更通知、配置项有 schema 与类型、环境物理隔离
- 禁止：万能配置接口（长成上帝对象）、命名风格并存、无变更审计、两端各自解释语义

## 前端踩坑备忘
- stylelint 的 `extends` 必须 `require.resolve` 转绝对路径（pnpm 隔离）
- TS 6 起 `baseUrl` 弃用
- ESLint 10 要求 flat config **默认导出数组**，不能导出函数
- ESLint 只从**当前工作目录**向上找配置（stylelint 按文件位置）→ 工作区根也要有 `eslint.config.js`
- typescript-eslint 必须显式 `tsconfigRootDir`；`projectService: false` 实测无效
- **沙箱限制**：Node `spawn git` 报 `EBUSY`；`turbo run` 报 `os error 231`（管道句柄耗尽）。均为环境限制，逐包直跑脚本均通过

## 后端分层（依赖只能自上而下）
`sts-app` → `sts-server` → `sts-components` → `sts-common` → `sts-parent`；`sts-parent` 零代码，版本号只出现一次

### 已落地模块（2026-10-09 · ADR-0005）
```
server/  pom.xml(聚合) → sts-parent
  → sts-common/{basic, web, db}
  → sts-server/sts-server-log（日志域）
  → sts-app/sts-app-server（唯一可启动进程，H2 内存库）
```
- groupId **按层分级**（`com.bluewhale.sts.common.basic`…），`<version>` 只在 `sts-parent` 出现
- `sts-common-basic`：`R<T>` · `ResultCode`（5 位 = HTTP 3 位 + 业务 2 位）· `BusinessException`；**刻意零依赖**，唯一例外是 `jackson-annotations`（provided，仅为 `@JsonIgnore`）
- `sts-common-web`：`GlobalExceptionHandler` + CORS + 自动配置（`AutoConfiguration.imports`），依赖即生效
- `sts-common-db`：MyBatis-Plus 3.5.9 拦截器；**分页/防全表更新需另引 `mybatis-plus-jsqlparser`**，漏引运行时才报错
- 模块内按**特性（feature）分包**（`clientlog/` 下 Controller/Service/Mapper/VO），**不用** `controller/service/repository` 顶层分包
- `sts-components/` **尚未创建**，按需再建，不预置空壳

## 契约链（2026-10-09 定 · ADR-0006）
- 代码 → springdoc → `scripts/export-openapi.sh`（端口 18080）→ `docs/api/openapi.json` → `openapi-typescript` → `common-api/src/generated/schema.d.ts`
- 生成物**入库**：前端构建不依赖后端可运行；契约变化在 review 里显式可见
- 路径常量 `satisfies keyof paths`；`startTime` 等字段用 `expectTypeOf` 钉住（springdoc 会把 query 位带 `@Schema` 的 `Long` 推平成 string）
- **openapi-typescript 的坑**：`Object` 字段渲染成 `Record<string, never>` → `common-api/types.ts` 的 `JsonValue` 只对 `data`/`context`/`logs` 修正
- openapi-typescript 7.13 peer 要 TS ^5.x，项目是 6.0.3 → 有警告但功能正常

## 前端请求分层（2026-10-09 定 · ADR-0007）
- `common-tools/http` = 传输内核（不认识业务接口）；`common-api` = 契约装配（只做接线）
- 三件收口：解包 `R<T>`、`code!==0` 抛异常、超时+取消合并一个 AbortController
- 六类错误 `business/http/network/timeout/abort/parse` + `retryable` 判据；**内核不做自动重试**（幂等边界属业务语义）
- **超时必须先于取消分类**（两者都是 abort 实现）；`traceIdProvider` 注入 X-Trace-Id，不自动生成
- 日志上报通道 `LogReporter`：批量 20 / 3s / 缓冲 500 / 退避封顶 30s / 不抛异常不自我记录

## 日志控制台 demo（platform-log-console，:3200）
- 左栏客户端视角 / 右栏服务端视角并排 —— 只看一边无法判断上报成没成
- `emit` 不能写 `logger[level](...)`：`perf` 签名是 `(label,value,unit)`，须显式 switch 分派
- 已用真实浏览器（Chrome headless + CDP）验证全链路：点击 → 上报 → 落库 → 表格刷新

## 环境坑（已查清，勿再排查）
- 后端启动报 Port 56491：沙箱注入 `SERVER__PORT` 环境变量 → 命令行 `--server.port=8080` 覆盖
- `curl localhost` 报 upstream connect failed：环境 HTTP 代理 → 加 `--noproxy '*'`
- 本机 `JAVA_HOME` 默认 JDK 17 → 后端构建须 `JAVA_HOME=/d/tools/installer/jdk/java21`
- 约定：**业务失败也返回 HTTP 200**，结果只由 `code` 表达（前端判 `code === 0`）；`R.isSuccess()` 必须 `@JsonIgnore` 否则泄漏 `success` 字段
- 构建：PATH 上是 JDK 17，**必须** `JAVA_HOME="D:/tools/installer/jdk/java21" mvn …`

## 日志双管道：技术日志 ≠ 审计日志（2026-10-09 定）
- **技术日志**（前端上报）：排查故障 · 短保留期 · 采样 · 前端驱动
- **审计日志**：合规追溯 · 长保留期 · 全量不可改 · **后端拦截器自动产生**（前端不知道最终是否落库成功，且易被篡改；前端上报只作"业务意图"补充）
- 推进顺序：**S1 后端骨架（已完成）→ S2 OpenAPI 契约 → S3 日志服务垂直切片**
- S1 刻意不上 Nacos/网关/Feign/Sentinel，等真需要跨服务调用再引

## 六条铁律
1. 两端不共用构建体系（pnpm / Maven 互不侵入）
2. 跨端只认 OpenAPI 契约；前端 SDK 禁止手写
3. 跨端共享物只放 `docs/`
4. `packages/*` 不得反向依赖 `platform/*`
5. `platform-user` 与 `platform-admin` 互不引用，复用下沉到 `packages/`
6. 后端模块依赖禁止反向、禁止跨层跳跃

## 沟通偏好
- 舰长偏好可视化方案与可运行演示，讲方案配图优于纯文字
- 给方案时先说结论和理由，再给细节
