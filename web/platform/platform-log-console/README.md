# platform-log-console — 日志控制台

把「**前端产生日志 → 批量上报 → 服务端落库 → 查询与链路追踪**」整条链路跑通并可视化的演示应用。

它不是产品功能，而是一个**可运行的验证工具**，同时是 `common-tools/http` 与 `common-api` 的活体示例。

---

## 一、为什么左栏和右栏要并排

左栏是**客户端视角**（我产生了什么、上报了多少），右栏是**服务端视角**（后端实际收到了什么）。

只看其中一边永远无法判断"上报到底成没成"：左栏有、右栏没有，是上报通道断了；右栏有、左栏没有，是查询条件不对。两者对照，一眼见分晓。

---

## 二、跑起来

```bash
# 1. 后端（默认 8080）。默认 profile 是 h2 内存库，无需装数据库
cd server && mvn -DskipTests install
java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar

#    想让数据留在磁盘上（演示「重启后数据还在」），改用 mysql profile
BW_DB_PASSWORD=<密码> java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=mysql

# 2. 前端
cd web && pnpm install
pnpm --filter platform-log-console dev     # http://127.0.0.1:3200
```

开发期 `/api` 由 vite 代理到 `127.0.0.1:8080`，请求与服务端同源 —— 排查日志功能时被跨域问题挡住是最不划算的事。

> 演示页本身不感知数据源：`h2` 与 `mysql` 两种 profile 下行为一致，差别只在**重启后数据还在不在**（h2 会清空）。这正好是演示两种数据源差异的好素材。数据源说明见 `server/README.md` 第六节。

---

## 三、它同时是一个 qiankun 子应用

本页既可以**单独打开**（`http://127.0.0.1:3200`），也可以被主应用 `platform-portal` **挂载**（路由 `/log-console/*`）。两条路都必须能走通。

| 关注点     | 做法                                                                                              |
| ---------- | ------------------------------------------------------------------------------------------------- |
| 入口       | `src/main.tsx` 用 `renderWithQiankun({ bootstrap, mount, unmount, update })` 导出生命周期         |
| 独立运行   | 入口内判断 `qiankunWindow.__POWERED_BY_QIANKUN__`，为假时直接 `createRoot().render()`             |
| 挂载点     | 从基座传入的 `container` 里取 `#root`，**不是** `document.getElementById`（否则会抢到基座的节点） |
| 与基座通信 | `bindHost(props, APP_NAME)` / `unbindHost()`，来自 `common-tools/micro`                           |
| 应用名     | `vite.config.ts` 里声明的名字必须与基座注册表里的**逐字一致**（`platform-log-console`）           |
| `base`     | dev 用 `/`，build 用 `/log-console/` —— dev 带前缀会让热更新静默失效，见 `docs/decisions/0012`    |
| 跨域与端口 | `server.cors: true` + `strictPort: true`：基座要按固定端口来取，端口漂移会表现为"一直加载中"      |

> **应用名是隐式契约。** 基座 `MICRO_APPS` 里的 `name` 与这里 `vite-plugin-qiankun` 的声明名不一致时**不会报错**，只会卡在"加载中"——插件按名字在 `window` 上挂生命周期壳，名字对不上就永远等不到回调。改名时两处必须同改。

> **调试时优先单独跑本页。** 被基座加载时，子应用的 React 组件级热更新不生效（样式热更新正常）；这是当前已知限制（`docs/decisions/0012`），改本页组件时单独 `pnpm --filter platform-log-console dev` 更快。

> **认证接线（ADR-0026）**：查询接口需要 `log:client:read`，因此本页必须带令牌。会话不由本页拥有 ——
> 基座内运行时用宿主下发的认证桥（`createMicroAppProps` 恒带 `auth`），独立运行时由 `createConsoleAuth`
> 自建一套（开发账号**只在 `import.meta.env.DEV` 为真时才构造**，生产构建下那段字面量会被打包器
> 摇掉、口令不进产物；不传账号即不自动登录。见 `docs/decisions/0033`）。
> 装配在 `src/console.ts`：`createApiClient({ accessTokenProvider, onUnauthorized })` 两个 provider 指向
> `consoleAuth.port`；`src/main.tsx` 在独立运行分支里先 `await consoleAuth.ensureSession()` 再渲染。
> **注意上报（`POST /api/logs/client`）刻意保持公开** —— 登录页出错的日志也必须发得出来。

> **两栏按菜单码过滤（`docs/decisions/0031`）**：左栏与右栏各自有一条菜单码
> （`ui:log_reporter:view` / `ui:log_query:view`），由基座连同读写权限一起下发。
> 两道门是**独立**的 —— 菜单码决定"这一栏出不出来"，读写权限决定"里面的请求能不能成功"，
> 因此"只给演示、不给查询"是一处配置，而不是一句只能靠约定维持的话。
> 右栏不可见时查询副作用会**提前 return**：那时必然拿到 `40301`，除了白留一屏错误，
> 还会在服务端留下几条"其实没人看"的查询。详情抽屉跟着**右栏**走 ——
> 没右栏就点不开它，单独给它一个码等于造一个进不去的入口。
> 权限来源在 `common-tools/micro` 的 `createPermissionStore`，本页只做三行 React 适配。

---

## 四、能演示什么

| 操作                      | 验证了什么                                                             |
| ------------------------- | ---------------------------------------------------------------------- |
| 点级别按钮（TRACE…ERROR） | 日志走真实的 `common-tools` logger：级别过滤、命名空间层级都生效       |
| 随机 10 / 60 条           | 批量上报：攒够 20 条或 3 秒即发；缓冲上限 500，超出丢最旧              |
| 抛异常（带堆栈）          | `Error` 对象在记录里完整保留（`name` / `message` / `stack`）           |
| 模拟成功 / 失败链路       | 同一个 `traceId` 串起多条日志；点表格里的链路 id 可回看整条链路        |
| 关掉自动上报              | 日志只留在本地缓冲，`待上报` 计数上涨而 `已落库` 不动                  |
| 右栏点级别柱子 / 筛选框   | 服务端查询：级别、命名空间（精确）、关键词（模糊）、分页               |
| 点时间查看详情            | 完整字段、`data` / `context` 的 JSON、整条链路                         |
| 后端停掉再刷新            | 顶部横幅给出生动指引；上报失败自动指数退避（最多 30s），恢复后自动清零 |

左栏底部五个计数（待上报 / 已落库 / 被拒 / 失败批次 / 丢弃）就是上报通道的完整状态 —— 上报是"尽力而为"的通道，它的健康状况必须可见，否则出问题时无从判断。

---

## 五、目录结构

```
src/
├── main.tsx / App.tsx        入口（含 qiankun 生命周期）与主容器（订阅、轮询、查询、交互）
├── console.ts                单例装配：api + reporter + logSource，及 sanitize
├── reporter.ts               LogReporter：批量、有界、指数退避、不抛异常
├── log-source.ts             LogSource：按级别产生真实日志、模拟链路
├── levels.ts                 级别元数据（新增级别时这里会编译失败，防止漏显示）
├── format.ts                 时间与 JSON 格式化
├── styles/                   设计令牌（variables.less 由 vite 注入）与全局样式
├── App.less                  页面样式（按组件块组织）
└── components/               GeneratorPanel · ServerLogPanel · StatsBar · LogDetailDrawer · LevelTag
tests/                        Vitest（含渲染测试）
```

---

## 六、设计取舍

- **上报通道有四条硬约束**：批量、有界、指数退避、不抛异常不自我记录。全部来自真实故障 —— 违反任何一条，一次后端故障就会演变成客户端与服务端之间的雪崩。见 `src/reporter.ts` 的类注释与 `docs/decisions/0004`。
- **上报必须走 `onRecord` 而不是 `LogSink`**：sink 拿到的是渲染后的字符串（级别信息被压扁、元数据被拼进去），只能反向解析；`onRecord` 拿到的是结构化事实。
- **脏数据在 `sanitize()` 一次性洗干净**：日志里夹带函数、循环引用、DOM 节点是常事，`JSON.stringify` 遇到会抛错，结果是整批 500 条一起失败。递归转安全值，让脏数据只影响它自己。
- **`perf` 级别单独分派**：它的签名是 `(label, value, unit)`，与其余级别的 `(message, ...data)` 不是一回事，按级别名统一调用会把数值写进消息位置。
- **客户端时钟不可信**：`timestamp` 仅作展示，排序与统计一律用服务端接收时间。
