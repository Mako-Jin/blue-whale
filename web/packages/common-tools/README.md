# common-tools — 公共能力包

与框架无关的通用工具集。**不依赖 React / Vue，不依赖任何运行时第三方库**，因此应用、构建脚本、Node 工具链都能直接用。

当前包含八个模块：

| 模块      | 入口                   | 内容                                                          |
| --------- | ---------------------- | ------------------------------------------------------------- |
| `logger`  | `common-tools/logger`  | 分级日志、命名空间层级、主题配色、同构渲染                    |
| `env`     | `common-tools/env`     | 运行环境探测、构建模式、调试模式与生产域名兜底                |
| `http`    | `common-tools/http`    | 请求内核：发请求、解包统一响应、错误分类、令牌注入与 401 恢复 |
| `session` | `common-tools/session` | 会话存取：令牌存放策略、身份与权限快照、变更订阅              |
| `json`    | `common-tools/json`    | JSON 安全层：把任意值修剪成可序列化形态                       |
| `report`  | `common-tools/report`  | 通用批量上报通道：批量、有界、退避、不抛异常                  |
| `events`  | `common-tools/events`  | 行为事件内核：`track()` 语义化发布 + 内部总线 + 订阅          |
| `micro`   | `common-tools/micro`   | 微前端桥接：子应用侧接住基座下发的 props 与全局状态           |

`json` / `report` / `events` 三个模块是**行为事件管道**的底座（见 `docs/decisions/0011`）。它们被拆成三层而不是合成一个 "analytics" 包，原因是复用面完全不同：`json` 与 `report` 是**通道属性**，日志管道也在用；`events` 才是事件语义本身。

`micro` 的位置值得说明：它是**子应用侧**的代码，本该属于子应用；但两个控制台都要用同一套逻辑，而**子应用之间禁止互相引用**（铁律 5），公共代码只能下沉到 `packages/`。于是它落在这里，并且刻意做成**结构化描述、不依赖 qiankun 类型**——这样本包"不依赖任何运行时第三方库"的约束不被破坏，桥接也不会被绑死在 qiankun 上。

`session` 落在本包的理由与 `micro` 同源：基座与所有子应用都要用**同一份**会话逻辑，而应用之间禁止互相引用。它刻意**不 import qiankun、不碰任何界面**——因此同一份实现既能被基座驱动，也能被子应用单独驱动（子应用单跑时把状态的提供方换成它自己的 store）。归属与分工见 `docs/decisions/0018`。

---

## 一、这个包解决什么问题

大型项目里"公共依赖"其实是两件事，别混在一起：

| 诉求                         | 正确做法                                              | 错误做法                          |
| ---------------------------- | ----------------------------------------------------- | --------------------------------- |
| 第三方依赖的**版本**想统一管 | pnpm 的 **catalog**（写在 `web/pnpm-workspace.yaml`） | 建一个 `common-deps` 包再互相引用 |
| 自研的**公共代码**想复用     | `packages/common-*` 能力包（本包即其一）              | 复制粘贴到各个应用                |

本包承担第二件事。第一件事由 pnpm catalog 负责——版本只写一处，各 `package.json` 用 `catalog:` 引用，既统一了版本，又不会产生幽灵依赖。

---

## 二、目录结构

```
packages/common-tools/
├── src/
│   ├── index.ts              统一出口
│   ├── logger/
│   │   ├── index.ts          模块出口
│   │   ├── types.ts          全部类型定义
│   │   ├── levels.ts         级别、严重度、console 方法映射
│   │   ├── themes.ts         内置主题与主题深合并
│   │   ├── format.ts         时间戳 / CSS / ANSI / 渲染模式探测
│   │   └── logger.ts         Logger 类与全局配置
│   ├── env/
│   │   └── index.ts          运行环境与调试模式
│   ├── http/
│   │   ├── index.ts          模块出口
│   │   ├── types.ts          客户端配置、单次请求选项、统一响应信封、认证钩子
│   │   ├── errors.ts         六类错误 + HttpError 基类 + retryable 判据
│   │   └── client.ts         请求内核（刻意不认识任何业务接口）
│   ├── session/
│   │   ├── index.ts          模块出口
│   │   ├── types.ts          SessionUser / SessionTokens / SessionSnapshot / SessionStore
│   │   └── store.ts          createSessionStore：令牌存取与订阅（存储可注入）
│   ├── json/
│   │   └── index.ts          JsonValue 类型 + sanitize / sanitizeObject
│   ├── report/
│   │   └── index.ts          BatchChannel：与内容无关的批量上报通道
│   ├── events/
│       ├── index.ts          模块出口
│       ├── types.ts          EventEnvelope / 中间件 / 订阅者 / 事件名校验
│       └── tracker.ts        createTracker：发布、总线、上下文
│   └── micro/
│       ├── index.ts          模块出口
│       ├── types.ts          HostProps / HostGlobalState（结构化描述，不 import qiankun）
│       └── bridge.ts         bindHost / unbindHost / getHostGlobalState…
├── tests/                    Vitest 单元测试（168 例）
├── playground/               可视化演示（纯 DOM，可运行）
├── eslint.config.js          引用 common-config，额外放行本包的 console 调用
├── stylelint.config.js       一行 re-export common-config 共享配置
├── vite.config.ts            库构建 + 测试配置
└── vite.playground.config.ts 演示站配置
```

---

## 三、快速开始

### 在应用里使用

先在应用 `package.json` 声明依赖（pnpm workspace 内）：

```json
{
  "dependencies": {
    "common-tools": "workspace:*"
  }
}
```

```ts
import { createLogger } from 'common-tools/logger';

const logger = createLogger('portal');
logger.info('应用启动');

const auth = logger.child('auth'); // 命名空间：portal › auth
auth.warn('token 即将过期', { expiresIn: 60 });

// 带上下文的子 logger，父级不受影响
const scoped = logger.withContext({ traceId: 'a1b2c3' });
scoped.info('请求重试');
```

### 全局配置

在应用入口处配置一次，影响所有 Logger（**包括已经创建的实例**）：

```ts
import { configureLogger } from 'common-tools/logger';
import { isDebugMode } from 'common-tools/env';

configureLogger({
  level: isDebugMode() ? 'debug' : 'info',
  colorize: import.meta.env.PROD === false,
});
```

### 本地跑演示

```bash
pnpm --filter common-tools playground   # 打开 http://127.0.0.1:3100
```

演示页分两栏：**中栏**把日志的真实控制台输出镜像渲染到页面上（可现场切换主题、阈值、着色与时间戳格式），**右栏**实时显示 `onRecord` 收到的结构化记录 —— 两栏对比着看，就能直观看出 `sink` 与 `onRecord` 拿到的东西有何不同。

---

## 四、Logger 能力

### 级别与过滤

| 级别      | 严重度 | 映射到的 console 方法 | 用途                                 |
| --------- | ------ | --------------------- | ------------------------------------ |
| `trace`   | 10     | `console.debug`       | 最细粒度的追踪                       |
| `debug`   | 20     | `console.debug`       | 开发期诊断                           |
| `info`    | 30     | `console.log`         | 常规信息                             |
| `success` | 30     | `console.log`         | 成功节点（与 info 同级，仅配色不同） |
| `perf`    | 35     | `console.debug`       | 计时与性能数值                       |
| `warn`    | 40     | `console.warn`        | 警告                                 |
| `error`   | 50     | `console.error`       | 错误                                 |

- **阈值**由 `level` 控制，低于阈值的日志**在格式化之前**就被丢弃，不产生字符串拼接开销。
- `level: 'silent'` 关闭全部输出。
- 默认阈值由运行环境推导（调试模式 → `debug`，否则 `info`）。
- 需要在构造昂贵参数前短路：

```ts
if (logger.isEnabled('debug')) logger.debug('快照', buildExpensiveSnapshot());
logger.ifEnabled('debug', () => logger.debug('快照', buildExpensiveSnapshot()));
```

### 命名空间层级

`child()` 逐级拼接，父级配置全量继承，可局部覆盖：

```ts
const app = createLogger('bluewhale');
const portal = app.child('portal'); // bluewhale › portal
const order = portal.child('order', { level: 'debug' }); // 仅本实例放宽阈值

portal.namespace; // 'bluewhale › portal'
```

分隔符可用 `namespaceSeparator` 改写（默认 `' › '`）。

### 主题与颜色

内置两套主题：

| 主题             | 场景                             |
| ---------------- | -------------------------------- |
| `DEFAULT_THEME`  | 深色控制台（Chrome / Edge 默认） |
| `CONTRAST_THEME` | 浅色控制台、投影演示             |

主题**按级别、按字段深合并**，只覆盖想改的部分，不会出现"半个色板"：

```ts
createLogger('t', {
  theme: {
    colors: { info: { fg: '#ff0000' } }, // bg / border 自动沿用基主题
    palette: { accent: '#123456' }, // 时间戳、命名空间等中性色
  },
});
```

`palette` 覆盖时间戳、命名空间、正文、次要文字、强调色、链接与分隔线——这些在旧实现里全部是散落在各方法中的硬编码色值，主题管不到。

### 渲染模式（同构）

Logger 会按运行环境自动选择渲染方式，**同一套代码在浏览器与终端都能正常显示**：

| 模式    | 触发条件                               | 表现                                |
| ------- | -------------------------------------- | ----------------------------------- |
| `css`   | 存在 `document`                        | `%c` 样式串，浏览器控制台的完整配色 |
| `ansi`  | 终端（`stdout.isTTY`）或 `FORCE_COLOR` | 24 位真彩转义序列                   |
| `plain` | 重定向到文件、CI 管道、`NO_COLOR`      | 纯文本，无样式噪声                  |

显式设置 `colorize: false` 会直接走纯文本；显式 `colorize: true` 则强制着色。遵循业界通用的 `NO_COLOR` / `FORCE_COLOR` 约定。

> 旧实现只支持 `%c`，在 Node 里会把样式串当普通文本打印出来，变成一坨 `%c color:#...` 噪音。

### 配置项

解析顺序：**内置默认 → 环境推导 → 全局配置 → 实例配置**，每层只覆盖显式设置过的字段（`undefined` 不参与覆盖）。

| 配置                 | 默认             | 说明                                 |
| -------------------- | ---------------- | ------------------------------------ |
| `level`              | 环境推导         | 过滤阈值，`'silent'` 关闭全部        |
| `showTimestamp`      | `true`           | 是否输出时间戳                       |
| `timestampFormat`    | `'HH:mm:ss.SSS'` | 也可传自定义函数 `(date) => string`  |
| `showNamespace`      | `true`           | 是否输出命名空间                     |
| `showLevel`          | `true`           | 是否输出级别标签                     |
| `colorize`           | 环境推导         | 是否着色                             |
| `theme`              | `DEFAULT_THEME`  | 主题覆盖（深合并）                   |
| `namespaceSeparator` | `' › '`          | 层级分隔符                           |
| `context`            | —                | 结构化上下文，以 `k=v` 追加在行尾    |
| `sink`               | —                | 实例级输出通道，优先于全局 sink      |
| `onRecord`           | —                | 结构化记录钩子（上报 / 落库 / 聚合） |
| `traceId`            | —                | 链路标识，随每条记录发出             |
| `sessionId`          | —                | 会话标识，随每条记录发出             |
| `route`              | —                | 当前路由，随每条记录发出             |
| `release`            | —                | 应用版本 / 构建号，随每条记录发出    |
| `maxTableRows`       | `50`             | 表格最大行数，`0` 表示不限           |
| `enableGrouping`     | `true`           | 是否使用 console 分组                |

时间戳格式：`HH:mm:ss` / `HH:mm:ss.SSS` / `YYYY-MM-DD HH:mm:ss` / `YYYY-MM-DD HH:mm:ss.SSS` / `ISO`。

实例可热更新，无需重建：

```ts
logger.configure({ level: 'debug', showTimestamp: false });
```

### 输出通道：sink 与 onRecord 的分工

两个钩子消费的对象不同，**不要混用**：

| 通道       | 消费对象                                   | 用途                               |
| ---------- | ------------------------------------------ | ---------------------------------- |
| `sink`     | **渲染后的样子**（浏览器下含 `%c` 样式串） | 控制台输出、单元测试捕获、页面镜像 |
| `onRecord` | **结构化事实**（`LogRecord`）              | 上报、落库、聚合                   |

#### sink —— 消费「样子」

sink 本质是一个 console 代理，可替换成任意实现：

```ts
import { setLoggerSink } from 'common-tools/logger';

setLoggerSink((method, args) => {
  (console[method] as (...a: unknown[]) => void)(...args);
});
```

`playground/` 正是用这个机制把控制台输出镜像到页面。

#### onRecord —— 消费「事实」

**上报必须走这里。** sink 拿不到结构化的级别与元数据（`info` / `success` 都被压成 `'log'`，命名空间与时间戳被拼进字符串），拿它做上报只能反向解析自己的格式：

```ts
import { configureLogger } from 'common-tools/logger';

configureLogger({
  onRecord: (record) => {
    // record.level 保真：info 与 success 分得开
    // record.data 保持原始形态，对象不被 stringify
    queue.push(record);
  },
  traceId: currentTraceId,
  release: __APP_VERSION__,
});
```

派发时机固定在**级别过滤之后、格式化之前**：被压制的日志不产生任何开销，且即便渲染环节抛错，记录也已经交出去了。

钩子内部的异常会被**静默吞掉**——上报失败若再产生一条日志、又触发一次上报，就会形成雪崩。日志系统的故障不允许反噬业务。

> 演示页右栏即实时渲染 `onRecord` 收到的记录，并带一个固定容量的缓冲（模拟上报链路的环形缓冲）。

> 采集之外的部分（批量、采样、脱敏、`sendBeacon` 传输）本期**未实现**，边界与理由见 `docs/decisions/0004-前端日志上报架构.md`。

### 展示型输出

```ts
logger.section('运行环境'); // 小标题
logger.divider('-', 40); // 分隔线
logger.keyValues({ mode: 'dev', port: 3000 }); // 多行键值对
logger.table(rows, ['layer', 'technology']); // 表格（超限自动截断并提示）
logger.group('启动流程', () => {
  /* ... */
}); // 折叠分组
logger.banner({ title: 'BlueWhale', version: '0.1.0', asciiArt });
logger.credits([{ label: '文档', url: 'https://example.com' }]);

const stop = logger.time('首屏渲染'); // 返回停止函数
stop(); // 打印耗时并回传毫秒数
logger.perf('首屏渲染', 128.4); // 显式给值
logger.error(new Error('远程服务不可用')); // 自动带上错误对象以保留堆栈
```

---

## 五、HTTP 请求内核

**这个模块刻意不认识任何业务接口。** 它只回答两个问题：怎么把一次请求发出去、怎么把响应翻译成结果或错误。接口路径与出入参类型由契约生成（见 `web/packages/common-api`）。

> **边界规则**：不认识业务接口的传输能力（发请求、解包、错误分类、重试判据）在本包；一旦某段代码知道"`/api/logs/client` 是什么"，它就必须待在上面那层，不能进本包。

### 快速开始

```ts
import { createHttpClient, isHttpError } from 'common-tools/http';

const http = createHttpClient({
  // 契约里的路径自带 `/api` 前缀，所以基地址留空；
  // 写成 baseUrl: '/api' 会拼出 `/api/api/logs/client`
  baseUrl: '',
  timeout: 10_000,
  traceIdProvider: () => currentTraceId, // 注入 X-Trace-Id，前后端日志可串
  onError: (error) => reportToMonitor(error),
});

const page = await http.get<ClientLogPage>('/api/logs/client', {
  query: { level: 'error', page: 1, size: 20 },
});
```

### 一次性收口的三件事

调用方拿到的直接是 `data`，不必人人写一遍 `res.code === 0 ? res.data : ...`：

| 收口内容                               | 为什么必须在传输层做                                                               |
| -------------------------------------- | ---------------------------------------------------------------------------------- |
| 解开统一响应 `R<T>`                    | 每个调用点各写一遍，迟早在某一处写漏                                               |
| `code !== 0` 抛异常                    | 用返回值表达失败必然有人漏判，漏判的表现是"页面显示 undefined"，比抛异常难排查得多 |
| 超时与取消合并成一个 `AbortController` | 两套中断机制并存时，总有一边忘了清定时器                                           |

### 六类错误

| `kind`     | 含义                                                       | `retryable`       |
| ---------- | ---------------------------------------------------------- | ----------------- |
| `business` | HTTP 200 但 `code !== 0`：请求完全正常，是业务规则拒绝了它 | ❌                |
| `http`     | 状态码非 2xx                                               | 仅 `429` 与 `5xx` |
| `network`  | 请求没发出去或没收到响应（DNS、连接被拒、CORS 被拦）       | ✅                |
| `timeout`  | 超过约定时间                                               | ✅                |
| `abort`    | 调用方主动取消                                             | ❌                |
| `parse`    | 响应不是合法 JSON 或不符合统一响应结构                     | ❌                |

```ts
try {
  await api.log.page({ page: 1 });
} catch (error) {
  if (!isHttpError(error)) throw error;
  switch (error.kind) {
    case 'business':
      toast(error.message);
      break; // 提示用户，改输入
    case 'abort':
      break; // 静默忽略
    default:
      if (error.retryable) scheduleRetry();
      break;
  }
}
```

**判断必须用结构化字段（`kind` / `status` / `code`），不要匹配 `message` 文案** —— 文案改一个字，判断就静默失效。

### 内核不做自动重试

`retryable` 只给出判据，**是否重试由调用方决定**。原因是**幂等边界是业务语义**：日志上报的 `POST` 重试是安全的，而"下单""支付"的 `POST` 重试就是重复扣款 —— 传输层无法从 `POST /orders` 这个路径推断出它是否幂等。

需要重试的调用点必须**同时**满足两条：`error.retryable === true`，且该操作确认幂等。

### 超时必须先于取消分类

超时也是通过 `abort()` 实现的，两者抛出的都是 `AbortError`。因此判断顺序是 `timedOut → AbortError → NetworkError`。若先按 `AbortError` 归类，超时会被报成"被主动取消"，排查时会去找"谁取消了这个请求"，而真正的原因（服务端太慢）被完全掩盖。

### 令牌注入与 401 恢复（内核只留注入位）

内核**不认识"刷新令牌接口"**（那是业务契约），**也不认识登录页**（那是界面）。它只提供三个注入位，由应用层决定怎么救：

```ts
const http = createHttpClient({
  accessTokenProvider: () => session.getAccessToken(), // 非空则注入 Authorization: Bearer
  onUnauthorized: () => channel.reauthenticate(), // 401 时问一句"还能救吗"
  onAuthLost: () => gotoLogin(), // 救不回来时通知界面
});
```

| 规则                                         | 说明                                                                                                                                             |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **401 才救，403 不救**                       | 403 是"登录了但没这个权限"，换多少令牌也换不来权限                                                                                               |
| **只重试一次**                               | 401 反复出现说明问题在配置或权限侧，重试只会把问题放大成一串请求                                                                                 |
| **重试前重新取令牌**                         | 刷新成功后令牌已换，沿用第一次取到的值等于带着作废令牌再撞一次 401                                                                               |
| **未带令牌的 401 不触发恢复**                | 请求本来就没令牌（公开接口），刷新毫无意义，白跑一次而已                                                                                         |
| **并发 401 合并成一次恢复**（single-flight） | **正确性要求**：刷新令牌是轮换的，并发的第二次刷新持的是刚作废的旧令牌，服务端会判成重放攻击并撤销该用户**全部**会话（见 `docs/decisions/0018`） |
| **恢复钩子抛错按"未恢复"处理**               | 抛出的是原始 401，而不是"刷新接口报错"——调用方关心的是自己这次请求失败                                                                           |

> **这里的去重是"按客户端"的。** 它只能合并**同一个** `HttpClient` 上的并发 401；当同一个会话被多处使用
> （基座自己的请求 + 若干子应用经 `HostAuthBridge` 委托过来的请求）时，各客户端彼此看不见彼此，
> 这时"合并"必须由会话层（`common-api` 的 `AuthChannel.reauthenticate()`）再做一次 ——
> 刷新是**会话级**动作，合并点落在会话层才算完整。两层合并都存在，缺一层都会在多客户端场景退回并发刷新。

`RequestOptions` 上有两个开关：

| 选项            | 用途                                                                                                                            |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `skipAuth`      | 本次不注入令牌（公开接口：登录、刷新）                                                                                          |
| `skipAuthRetry` | 本次不参与 401 自动恢复。**刷新接口自身必须置位** —— 否则刷新失败 → 401 → 触发刷新 → 再失败，递归下去刷新接口被自己打出一片 401 |

> 令牌存哪、刷新成功后谁来存、会话失效后跳到哪，都不在这个内核里：
> 存取在 `common-tools/session`，接线在 `common-api` 的 `createAuthChannel`。
> 归属与分工见 `docs/decisions/0018-权限在微前端中的归属.md`。

### 日志与静默开关

- `business` 失败记 **warn**，其余记 **error** —— 业务拒绝是预期行为，若也记 error，真正的故障告警会被正常业务拒绝淹没；
- `silent: true` 用于已知会失败、由调用方自行处理的探测类请求（如健康检查），避免每 10 秒往错误日志里写一条；
- `logger: null` 可整体关闭本客户端的日志。

### 配置项

| 配置                                   | 默认                   | 说明                                               |
| -------------------------------------- | ---------------------- | -------------------------------------------------- |
| `baseUrl`                              | `''`                   | 基地址；路径以 `http(s)://` 开头时原样使用         |
| `timeout`                              | `15000`                | 毫秒，`0` 表示不超时                               |
| `successCode`                          | `0`                    | 视为业务成功的 `code`                              |
| `headers`                              | `{}`                   | 默认请求头                                         |
| `unwrap`                               | `true`                 | 是否默认解开统一响应包装                           |
| `traceIdProvider`                      | —                      | 返回非空值时注入 `X-Trace-Id`                      |
| `accessTokenProvider`                  | —                      | 返回非空值时注入 `Authorization: Bearer`           |
| `onUnauthorized`                       | —                      | 401 时的恢复钩子，返回"是否已恢复"                 |
| `onAuthLost`                           | —                      | 带着令牌仍 401 且救不回来时调用（清会话/跳登录页） |
| `fetchImpl`                            | 全局 `fetch`           | 测试注入用                                         |
| `logger`                               | `logger.child('http')` | 传 `null` 关闭                                     |
| `onRequest` / `onResponse` / `onError` | —                      | 钩子，用于补签名头、埋点、上报                     |

单次请求可用 `RequestOptions` 覆盖 `timeout` / `unwrap` / `silent` / `headers` / `signal` / `skipAuth` / `skipAuthRetry`。

---

## 六、会话存取（session）

只管"令牌与身份放在哪、变了通知谁"。**不认识任何接口、不碰任何界面、不 import qiankun** —— 因此同一份实现既能被基座驱动，也能被子应用单独驱动（子应用单跑时提供方换成它自己的 store，其余逻辑一行不改）。

```ts
import { createSessionStore } from 'common-tools/session';

const session = createSessionStore(); // 默认用 localStorage，不可用时退化为内存

session.set({
  user: { userId: 1, username: 'admin', roles: ['ADMIN'], permissions: ['auth:user:create'] },
  tokens: { accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1_800_000 },
});

session.hasPermission('auth:user:create'); // true
const unsubscribe = session.subscribe((next, previous) => {
  /* 界面据此重渲染 */
});
```

### 令牌为什么分开存

| 令牌     | 存放                         | 理由                                           |
| -------- | ---------------------------- | ---------------------------------------------- |
| 访问令牌 | **只在内存**                 | 载荷里有用户与租户标识，落盘只是白白扩大暴露面 |
| 刷新令牌 | **持久化**（`localStorage`） | 换取"刷新页面后静默续期"                       |

这个权衡的兜底在后端：刷新令牌是**轮换 + 重放检测**的，一旦泄露并被使用，旧令牌的再次出现即撤销该用户全部会话。取舍的完整论证见 `docs/decisions/0018` 第 6 条。

### 几条硬规则

| 规则                                 | 原因                                                                                                                                                                           |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **身份与权限不持久化**               | 它们是会变的（后台改权限、换角色）。刷新页面后靠刷新令牌重新换取，而不是相信一份可能过期的本地副本                                                                             |
| **快照引用稳定**                     | `useSyncExternalStore` 每次渲染都会调用 `getSnapshot`，返回新对象会被判成"一直在变"并陷入无限重渲染                                                                            |
| **只比引用、不做字段级比较**         | 手写字段清单漏了新增字段，更新会被静默丢弃（表现为"权限改了但界面不变"）；代价只是一次多余重渲染                                                                               |
| **持久化载荷带版本号，读不懂就丢弃** | 会话是可以重建的缓存，为一个过期格式把应用拦在白屏里毫无收益                                                                                                                   |
| **存储读写全部包 try**               | `localStorage` 本身就可能抛错（无痕模式抛 `SecurityError`、写满抛 `QuotaExceededError`）。读失败按"没有会话"处理，写失败只影响"刷新页面要重新登录"，绝不让一次成功登录变成失败 |
| **监听器抛错被吞掉并记 warn**        | 某个监听器自己的问题不该影响其他监听器，更不该影响会话本身                                                                                                                     |

> 订阅不需要手动取消时可以不接返回值；组件里请务必在卸载时调用返回的取消函数，否则会持有失效的闭包。
>
> 消费方式见 `common-api` 的 `createAuthChannel`（把契约、本模块与 `http` 的钩子接在一起）。

---

## 七、JSON 安全层

日志的 `data` / `context` 与事件的 `props` 都是"调用方随手塞进来的任意值"。这一层负责把它们修剪成**一定可以 `JSON.stringify`** 的形态。

```ts
import { sanitize, sanitizeObject } from 'common-tools/json';

sanitize({ orderId: 'SO-1', at: () => Date.now() }); // { orderId: 'SO-1', at: '[function]' }
```

**为什么必须有：** `JSON.stringify` 遇到函数、Symbol、循环引用、`BigInt` 会**直接抛错**。一条脏数据夹在里面，结果不是丢这一条，而是**整批上报失败** —— 另外 499 条正常数据一起遭殃。在这里递归转成安全值，让脏数据只影响它自己。

转换规则：

| 输入               | 输出                                 | 理由                                                             |
| ------------------ | ------------------------------------ | ---------------------------------------------------------------- |
| 函数 / Symbol      | `'[function]'` / `'[symbol]'` 字符串 | 保留"这里有过东西"的事实，比直接抹掉更利于排查                   |
| `BigInt`           | 字符串（不转 number）                | 转 number 会静默丢精度                                           |
| `NaN` / `Infinity` | 字符串                               | 它们是非法 JSON；静默变 `null` 会让"数据异常"和"字段不存在"混淆  |
| `Error`            | `{ name, message, stack? }`          | 直接 stringify 一个 Error 会得到 `{}`                            |
| `Date`             | ISO 字符串                           |                                                                  |
| 超深嵌套（> 4 层） | `'[depth]'`                          | 4 层以上通常意味着把整个响应体塞了进来，没人会读，开销却是确定的 |
| 循环引用           | 由深度上限兜住                       | 单独检测环会拖慢正常路径                                         |

> 这一层同时提供 `JsonValue` / `JsonArray` / `JsonObject` 三个类型。契约里"原样透传"的字段用它们收口 —— 理由见 `web/packages/common-api/README.md` 第五节。

---

## 八、批量上报通道

**这个模块不认识日志，也不认识事件。** 它只做一件事：把"随时零星产生的条目"攒成"有界、可退避、失败不抛出的一批批请求"。

```ts
import { BatchChannel } from 'common-tools/report';

const channel = new BatchChannel({
  buildBody: (items) => ({ items }),
  send: (body) => http.post('/api/events/client', body),
  resolveCounts: (result) => ({ accepted: result.accepted, rejected: result.rejected }),
  sendOnUnload: (body) => sendWithKeepalive(body), // 见下方「卸载兜底」
  onChange: (state) => render(state),
});

channel.push(event);
channel.flush();
```

**四条约束全部来自真实故障，不是理论：**

| 约束                   | 不这么做的后果                                                      |
| ---------------------- | ------------------------------------------------------------------- |
| **批量**               | 一条一个请求，请求量 = 条目数 × 用户数，后端被自己的客户端打挂      |
| **有界**（丢最旧）     | 上报通道因自身积压把页面拖垮，比丢几条数据严重得多                  |
| **指数退避**           | 后端已倒下时按固定间隔重试，等于持续冲击一个正在恢复的服务          |
| **不抛异常、不自记录** | 上报失败写进日志 / 事件通道 → 再次触发上报 → 客户端与服务端之间雪崩 |

默认值集中在 `BATCH_DEFAULTS`：批量 20 / 间隔 3 s / 缓冲 500 / 退避封顶 30 s。

`BatchChannelState` 只暴露事实（`pending` / `sent` / `rejected` / `failedBatches` / `dropped` / `lastError` / `backoffMs`），**通道自己不做任何呈现** —— 调用方据此渲染"上报健康度"。

### 卸载兜底：`sendOnUnload` vs `sendBeacon`

页面关闭是这条通道最大的数据损失来源：定时器还没到点，缓冲里那一批随页面一起消失，而"用户离开"往往正是最值得记录的行为。

`sendOnUnload` 只能**同步**发出（卸载阶段没有 `await` 的机会），因此传输方式交给调用方决定。两个可选实现差异很大：

| 方式                              | 结果                                                                                                              |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `navigator.sendBeacon`            | 发 `Blob` 且带自定义 `Content-Type: application/json` 时**会触发 CORS 预检**，而预检在卸载阶段无法完成 → 静默失败 |
| `fetch(url, { keepalive: true })` | 明确返回 Promise，可以判定是否交出去了；代价是请求体有 **64 KB 上限**                                             |

所以实际用法是 `keepalive` + 按体积裁剪，且**优先保住最近的批次**（旧数据价值低于新数据）。

`bindUnload(target)` 同时监听 `pagehide` 与 `visibilitychange`：**不能只依赖 `beforeunload`** —— 移动端浏览器切后台时可能直接冻结页面，`beforeunload` 根本不触发，而"切后台"比"关页签"频繁得多。

> 本模块在 `platform-log-console` 与 `platform-event-console` 中被两条管道共用。之所以要抽出来：那四条约束是**通道本身的属性**，与上报的内容无关；各写一份的结果是两份实现立刻开始漂移 —— 一份修了并发缺陷，另一份没修。

---

## 九、行为事件内核

业务代码只需要认识一个方法：`track()`。

```ts
import { createTracker } from 'common-tools/events';

const tracker = createTracker({
  context: { sessionId, deviceId },
  middlewares: [blockMiddleware, maskMiddleware],
  onError: (error, stage) => logger.warn('埋点异常', { stage, error }),
});

tracker.track('order.submit', { orderId: 'SO-1', amount: 199 });
```

### 为什么是「语义化 track」而不是通用全局事件总线

通用总线（`emit('any-string', any)`）在这个场景下是错的：**事件名一旦上报过就是永久契约**。改名不会报错，只会让历史数据的含义在某一刻断裂 —— 而这种断裂要到月底看报表时才被发现。因此内核把约束前移：

- 事件名必须匹配 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（小写、点分、至少两段），形如 `order.submit`；
- 非法名字**直接丢弃**，并**只报一次** —— 同一个拼错的名字会在每次交互里重复出现，每次都报会把真正的问题淹掉；
- 所以内核对外只暴露 `track(name, props, options)` 这一个入口，不暴露 `emit`。

> 这条正则与后端 `ClientEventService` 的校验规则**必须完全一致**。两边各写一套的后果是客户端放行、服务端逐条拒绝，表现为"接口返回成功但一条也没落库"。

### 订阅与中间件都在内核内部

业务只发布；**补齐上下文、屏蔽、脱敏、交给谁、怎么发**全是 SDK 内部实现，业务看不到也不需要知道：

| 能力                   | 做法                                                                                                                                                                    |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `subscribe(listener)`  | 多订阅者，逐个 `try/catch`，一个订阅者抛错不影响其余                                                                                                                    |
| `use(middleware)`      | 中间件拿到 `(event, next)`，可改写，也可返回 `null` 终止整条链（屏蔽 / 脱敏靠它实现）                                                                                   |
| `updateContext(patch)` | 合并式更新（登录、跳路由时调用）                                                                                                                                        |
| `clearUser()`          | 单独开一个方法而不是让调用方 `updateContext({ userId: undefined })` —— 登出是最容易漏的一步，漏了的后果是"登出后的事件仍记在上一个人名下"，在共享设备场景下就是数据事故 |

`contextProvider` 做成**每次求值**而不是启动时快照：用户登录、路由跳转都发生在运行期，快照会让登录后的事件仍带着登录前的身份，而且不会有任何报错。

### 与日志管道的关系：复用传输层，但不共用通道

`EventEnvelope` 与 `LogRecord` 都在"前端产生 → 服务端落库"，但数据性质完全不同：

|            | 技术日志     | 行为事件                           |
| ---------- | ------------ | ---------------------------------- |
| 回答的问题 | 系统在干什么 | 用户做了什么                       |
| 保留期     | 短           | 长（用于同比 / 留存）              |
| 采样       | 可以         | **不可以**（采样后漏斗就对不上了） |
| 契约稳定性 | 可自由加字段 | 改一个字段名就永久断代             |
| 消费方     | 运维 / 开发  | 分析 / 数仓                        |

因此是**两条独立管道、独立表**，只在传输层（`json` + `report`）复用。详见 `docs/decisions/0011`。

### 身份缝合

`deviceId`（匿名期）与 `userId`（登录期）**同时挂在每条事件上**。缺了 `deviceId`，"匿名期浏览 → 登录 → 下单"这条转化漏斗的漏斗底永远对不上漏斗口。

### 刻意没有 `traceId`

日志管道有它（排查一次失败请求的完整链路），但事件的关联维度是**人**（漏斗、留存、分群），靠 `userId` / `deviceId` / `sessionId` 就够。更关键的是：**契约没有这一列**，加了它就是一条"发出去也没人接"的假字段 —— 而字段一旦写进契约就是永久的，宁可不加。

---

## 十、微前端桥接（micro）

子应用侧接住基座的最小 API。**用法只有三行**：

```ts
import { bindHost, unbindHost, getHostGlobalState, isMicroApp } from 'common-tools/micro';

// mount 时
bindHost(props, 'platform-log-console'); // props 是 qiankun 传进来的那个对象
// unmount 时
unbindHost();

// 任何地方读全局状态（独立运行时返回 {}，不会抛错）
const { theme, userId } = getHostGlobalState();
```

| 导出                                 | 作用                                                                    |
| ------------------------------------ | ----------------------------------------------------------------------- |
| `bindHost(props, name)`              | 接管基座 props；挂调试标记；订阅全局状态变化                            |
| `unbindHost()`                       | 断开连接；清调试标记、订阅者与 `window.__MICRO_HOST__`                  |
| `isMicroApp()`                       | 当前是否跑在基座里（`false` = 独立运行）                                |
| `onHostGlobalStateChange(cb, opts?)` | 订阅全局状态，**返回取消订阅函数**；见下面"单槽位"一节                  |
| `getHostProps()`                     | 取原始 props（需要基座新加的字段时用）                                  |
| `getHostGlobalState()`               | 读全局状态；独立运行或基座未提供时返回 `{}`                             |
| `setHostGlobalState(s)`              | 主动回写全局状态（如子应用内登录后补齐 `userId`）；独立运行时是 no-op   |
| `HostAuthBridge`（类型）             | 基座下发的**认证能力**：`getAccessToken()` / `reauthenticate()`，见下节 |

### 子应用要发已鉴权请求：用 `props.auth`，不要拿令牌值

第一个要发已鉴权请求的子应用（`platform-admin`）逼出了这一环：令牌在基座的内存会话里，子应用怎么拿？
基座经 `HostProps.auth` 下发的是**两个动作**，不是令牌本身：

```ts
type HostAuthBridge = {
  getAccessToken(): string | null; // 按需取，永远是基座手里的最新一份
  reauthenticate(): Promise<boolean>; // 请基座刷新一次；并发由基座合并
};
```

- **为什么不下发令牌值**：值会陈旧 —— 基座在后台静默刷新一次之后，子应用手里那份就成了作废的令牌，
  而它自己并不知道（症状是"刚才还好好的，突然全部 401"）。函数是按需取，一定是最新的。
- **为什么刷新也由基座持有**：刷新令牌是轮换的，重复使用会被判成重放攻击并撤销该用户**全部**会话。
  "刷新"在全应用里只能有一个发生点；子应用各自刷新等于把这个坑复制到每个子应用里。
- **为什么没有"清空会话"**：会话失效是全局事实，清空只该有它的持有者一个入口（基座或子应用自己的通道）。

独立运行时 `props.auth` 不存在 —— 子应用应当把"基座桥"与"自建会话"归一成同样的两个动作
（见 `platform-admin/src/auth/index.ts` 的 `AuthPort`），上层业务代码因此完全不区分运行方式。

### 独立运行时这套 API 全部安全

`hostProps` 为 `null` 时，读状态返回 `{}`、写状态不做事、`isMicroApp()` 返回 `false`。**这不是"防御性编程"，是硬需求**：子应用必须能脱离基座单独启动（调试微前端的第一件事就是区分"子应用本身坏了"还是"基座加载链路坏了"），入口代码不能出现 `if (window.__POWERED_BY_QIANKUN__)` 之外的额外分支。

### 为什么不直接 import qiankun 的类型

`HostProps` / `HostGlobalState` 是**结构化描述**（字段都可选），不是从 qiankun 或基座包里引出来的：

- 子应用引用基座是**应用之间互相引用**，违反铁律 5；
- 本包对外的承诺是"不依赖任何运行时第三方库"，引入 qiankun 类型会打破它；
- 基座将来加字段时，子应用不需要跟着改（可选字段，用不到就不管）。

### 两个调试设施（只为"能看见"）

1. **`window.__MICRO_HOST__`** —— props 原件挂在全局。排查"props 到底传到没有"时在 DevTools 里看一眼即可，不用断点。
2. **右下角状态标记** —— 显示 `应用名 · theme=dark · userId=u-xxx · roles=1 · perm=17`。全局状态下发是这套微前端要验证的核心能力之一，只在 console 里打印容易看漏；带上**角色数与权限数**是因为"权限到底传到子应用没有"（ADR-0018 第 3 条）只能靠它一眼确认，列出全部标识又太长。

> **标记挂进基座给的容器，而不是 `document.body`。** 容器卸载时会被 qiankun 清空，标记跟着一起走，不会出现"子应用已卸载但标记还留在页面上"的错觉。

### 全局状态订阅：必须走桥，不要直接调 props（单槽位坑）

qiankun 的 `onGlobalStateChange` 为每个子应用只保存**一个**回调，第二次注册直接覆盖第一个 ——
源码原话：「每个子应用只有一个激活状态的全局监听，新监听覆盖旧监听」，而且**只打一条 `console.warn`，不报错**。

子应用里通常不止一个消费者：调试标记、业务状态、埋点上下文。谁后注册谁生效，先注册的那个**静默失效** ——
这个 bug 在本项目真实出现过，症状是"应用里的徽标变了，右下角的标记不变"。

所以桥做了多路复用：`bindHost` 只注册**一次**原生回调，再分发给所有本地订阅者。

```ts
const unsubscribe = onHostGlobalStateChange((state) => setTheme(state.theme));

// React 里配合 useEffect：
useEffect(() => onHostGlobalStateChange(setState), []);

// 需要立即拿到当前值时显式声明（默认不回灌 —— effect 里同步 setState 是多余的一次渲染）
onHostGlobalStateChange(render, { fireImmediately: true });

unsubscribe();
```

`unbindHost()` 会**清空全部订阅者并注销原生回调**。这条必须做：子应用的模块在多次挂载之间是复用的
（模块级变量不重置），上一轮若漏了解绑，下一轮同一个回调会被执行两次、三次……

测试替身也按真实行为建模（单槽位 + 覆盖计数），`tests/micro.test.ts` 共 13 条用例把"多订阅者都能收到"
"解绑后不重放"这些约束钉住 —— **替身比真实实现宽容，恰恰会放过这类 bug**。

### 一个顺序坑

`unbindHost()` 里**先取父节点、再清 `hostProps`**：

```ts
const parent = getIndicatorParent(); // ← 必须先
hostProps = null; // ← 后清
parent?.querySelector(`#${INDICATOR_ID}`)?.remove();
```

反过来写会退化成 `document.body`，容器里的标记搜不到，留下一个"卸载不干净"的假象 —— 而这恰好是微前端最容易误判的一类问题。`tests/micro.test.ts` 把这条顺序钉住了。

---

## 十一、相对参考实现的修复

本包的结构参照 `platform-terminator/packages/common-tools`，logger 在其基础上重写。以下是**原实现中真实存在的问题**，均已修复并有测试覆盖：

| #   | 原实现                                             | 后果                                                            | 现在                                                                |
| --- | -------------------------------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------- |
| 1   | 所有级别都走 `console.log`                         | DevTools 的级别筛选失效，错误日志拿不到堆栈定位                 | 按级别映射到 `debug` / `log` / `warn` / `error`                     |
| 2   | `timestampFormat` 的分支被整段注释掉               | 配置形同虚设，永远输出全量日期                                  | 五种格式真正生效，并支持自定义函数                                  |
| 3   | 只有 `debugMode` 一个布尔开关                      | 无法压制 info，也无法"只看 warn 以上"                           | 引入 7 级严重度 + 阈值过滤                                          |
| 4   | 主题是浅合并                                       | 只传 `{ info: { fg } }` 会让该级别的 bg / border 变成 undefined | 按级别、按字段深合并                                                |
| 5   | 时间戳、命名空间等色值硬编码在各方法里             | 换主题换不掉                                                    | 统一收敛到 `palette`                                                |
| 6   | 只用 `%c` 渲染                                     | Node / CI 里打印出一堆 `%c color:#...` 噪音                     | 按环境自动降级到 ANSI / 纯文本                                      |
| 7   | 全局配置只在构造时读取                             | `configure()` 之后再创建的实例与已有单例行为不一致              | 惰性解析 + 版本失效，配置可热更新                                   |
| 8   | 消息直接拼进格式串                                 | 消息里的 `%o`、`%c` 会吞掉后续参数                              | 针对性转义说明符，普通 `%` 不受影响                                 |
| 9   | `singleton` 挂在 `(LoggerFactory as any)` 上       | 类型不安全，且无法重置                                          | 显式 `logger` 根实例 + `resetLogger()`                              |
| 10  | `showBanner` 默认 `console.clear()`                | 清掉控制台里别人的输出                                          | 默认不清屏，需要时传 `clear: true`                                  |
| 11  | `showBanner` 内部 `fetch` ASCII 文件               | 日志组件发网络请求，且是假异步                                  | 改为由调用方传入字符串                                              |
| 12  | `debugger.ts` 里的默认域名规则被未闭合注释整块吞掉 | `DEFAULT_PRODUCTION_PATTERNS` 恒为 `[]`，这段配置从未生效       | 显式声明规则，默认空数组                                            |
| 13  | `isDebugMode` 直接访问 `window`                    | Node / SSR / 构建脚本里调用即抛错                               | 全部走能力探测，任何环境可安全调用                                  |
| 14  | `ColorScheme` 接口在 types.ts 里重复声明两次       | 无害但说明无人维护                                              | 单一来源                                                            |
| 15  | 无输出通道抽象                                     | 测试只能 spy 全局 console，页面无法镜像                         | 引入 `LogSink`（渲染侧）与 `onRecord`（结构化侧）两个分工明确的钩子 |
| 16  | 无命名空间层级                                     | 只能靠字符串手写 `'a › b'`                                      | `child()` 逐级派生并继承配置                                        |

---

## 十二、设计取舍

- **不引入渐变。** 原实现的级别标签用了 `linear-gradient`，浏览器里好看，但 ANSI 无法表达，会导致两种环境观感割裂。改用纯色，换来三态一致。
- **不做内部 `fetch`。** 需要 ASCII 资源时由调用方读取后传入字符串，日志组件保持纯粹。
- **等级标签统一 7 字符宽**（`SUCCESS` 的长度），让多行日志的列严格对齐。
- **`time()` 返回停止函数**，而不是 `console.time` / `timeEnd` 的全局标签池——后者在并发或同名嵌套时会互相覆盖，且未匹配时静默失败。
- **HTTP 走自建而非 axios。** 需要的能力（JSON 收发、超时、取消、解包、错误分类）用 `fetch` + 约 250 行封装即可覆盖，且不引入运行时依赖。更要紧的是 axios 自带一套 `AxiosError` 与拦截器生态，引入后项目里会同时存在两套错误判断方式，最终还是绕不开归一到一套分类上。详见 `docs/decisions/0007`。
- **HTTP 内核留在了 `common-tools` 而不是另开包。** 本包的定义是"与框架无关的通用能力"，传输内核符合；`common-*` 包的数量按能力边界增长，而不是按"类别的名字听起来不同"增长。这条边界由一句话守住：**认识业务接口的代码一律不能进本包。**

---

## 十三、待补充

- 日志上报目前只到**采集契约**（`onRecord` + `LogRecord`）与**前端传输**（`BatchChannel`）；服务端的存储与聚合查询已实现（`sts-server-log`），跨端串联靠 `X-Trace-Id`
- 行为事件链路已打通到落库与聚合查询（`sts-server-track` + `platform-event-console`），但**采样、服务端二次脱敏、事件注册表**尚未实现，等有真实流量再做；清单见 `docs/decisions/0011`
- 其余能力（并发去重、请求缓存）需要时再议，仍按同一句话判断该不该进本包：**认识业务接口的代码一律不能进本包**
- `common-tools/micro` 目前只被两个控制台子应用使用，且只在 dev 下验证过；**生产部署形态**（子应用静态目录、网关路由规则）还没定，`prodEntry` 只是占位。桥接本身与部署形态无关，定下来后不必改本模块
- **`session` 已就绪但还没有消费方**：基座的登录页、路由守卫与全局状态下发是下一步（`docs/decisions/0018` 的第②段）
- 会话还差三项，都等有需求再做：**多标签页同步**（靠 `storage` 事件，一个标签页登出后其他标签页跟着登出）、**按 `expiresAt` 主动续期**（现在只在收到 401 时才刷新）、**加密存储**（当前刷新令牌明文落盘，风险由后端轮换与重放检测兜底）
