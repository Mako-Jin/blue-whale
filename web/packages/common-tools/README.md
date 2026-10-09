# common-tools — 公共能力包

与框架无关的通用工具集。**不依赖 React / Vue，不依赖任何运行时第三方库**，因此应用、构建脚本、Node 工具链都能直接用。

当前包含两个模块：

| 模块     | 入口                  | 内容                                           |
| -------- | --------------------- | ---------------------------------------------- |
| `logger` | `common-tools/logger` | 分级日志、命名空间层级、主题配色、同构渲染     |
| `env`    | `common-tools/env`    | 运行环境探测、构建模式、调试模式与生产域名兜底 |

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
│   └── env/
│       └── index.ts          运行环境与调试模式
├── tests/                    Vitest 单元测试（76 例）
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

演示页会把日志**真实控制台输出**镜像渲染到页面上，可现场切换主题、阈值、着色与时间戳格式。

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

| 配置                 | 默认             | 说明                                |
| -------------------- | ---------------- | ----------------------------------- |
| `level`              | 环境推导         | 过滤阈值，`'silent'` 关闭全部       |
| `showTimestamp`      | `true`           | 是否输出时间戳                      |
| `timestampFormat`    | `'HH:mm:ss.SSS'` | 也可传自定义函数 `(date) => string` |
| `showNamespace`      | `true`           | 是否输出命名空间                    |
| `showLevel`          | `true`           | 是否输出级别标签                    |
| `colorize`           | 环境推导         | 是否着色                            |
| `theme`              | `DEFAULT_THEME`  | 主题覆盖（深合并）                  |
| `namespaceSeparator` | `' › '`          | 层级分隔符                          |
| `context`            | —                | 结构化上下文，以 `k=v` 追加在行尾   |
| `sink`               | —                | 实例级输出通道，优先于全局 sink     |
| `maxTableRows`       | `50`             | 表格最大行数，`0` 表示不限          |
| `enableGrouping`     | `true`           | 是否使用 console 分组               |

时间戳格式：`HH:mm:ss` / `HH:mm:ss.SSS` / `YYYY-MM-DD HH:mm:ss` / `YYYY-MM-DD HH:mm:ss.SSS` / `ISO`。

实例可热更新，无需重建：

```ts
logger.configure({ level: 'debug', showTimestamp: false });
```

### 输出通道（sink）

sink 本质是一个 console 代理，可替换成任意实现：

```ts
import { setLoggerSink, type ConsoleMethod } from 'common-tools/logger';

setLoggerSink((method, args) => {
  // 1. 转发给真实 console
  (console[method] as (...a: unknown[]) => void)(...args);
  // 2. 同时上报或落库
  report({ method, args });
});
```

用途：单元测试断言、页面内可视化镜像（`playground/` 即用此机制）、远端上报。

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

## 五、相对参考实现的修复

本包的结构参照 `platform-terminator/packages/common-tools`，logger 在其基础上重写。以下是**原实现中真实存在的问题**，均已修复并有测试覆盖：

| #   | 原实现                                             | 后果                                                            | 现在                                            |
| --- | -------------------------------------------------- | --------------------------------------------------------------- | ----------------------------------------------- |
| 1   | 所有级别都走 `console.log`                         | DevTools 的级别筛选失效，错误日志拿不到堆栈定位                 | 按级别映射到 `debug` / `log` / `warn` / `error` |
| 2   | `timestampFormat` 的分支被整段注释掉               | 配置形同虚设，永远输出全量日期                                  | 五种格式真正生效，并支持自定义函数              |
| 3   | 只有 `debugMode` 一个布尔开关                      | 无法压制 info，也无法"只看 warn 以上"                           | 引入 7 级严重度 + 阈值过滤                      |
| 4   | 主题是浅合并                                       | 只传 `{ info: { fg } }` 会让该级别的 bg / border 变成 undefined | 按级别、按字段深合并                            |
| 5   | 时间戳、命名空间等色值硬编码在各方法里             | 换主题换不掉                                                    | 统一收敛到 `palette`                            |
| 6   | 只用 `%c` 渲染                                     | Node / CI 里打印出一堆 `%c color:#...` 噪音                     | 按环境自动降级到 ANSI / 纯文本                  |
| 7   | 全局配置只在构造时读取                             | `configure()` 之后再创建的实例与已有单例行为不一致              | 惰性解析 + 版本失效，配置可热更新               |
| 8   | 消息直接拼进格式串                                 | 消息里的 `%o`、`%c` 会吞掉后续参数                              | 针对性转义说明符，普通 `%` 不受影响             |
| 9   | `singleton` 挂在 `(LoggerFactory as any)` 上       | 类型不安全，且无法重置                                          | 显式 `logger` 根实例 + `resetLogger()`          |
| 10  | `showBanner` 默认 `console.clear()`                | 清掉控制台里别人的输出                                          | 默认不清屏，需要时传 `clear: true`              |
| 11  | `showBanner` 内部 `fetch` ASCII 文件               | 日志组件发网络请求，且是假异步                                  | 改为由调用方传入字符串                          |
| 12  | `debugger.ts` 里的默认域名规则被未闭合注释整块吞掉 | `DEFAULT_PRODUCTION_PATTERNS` 恒为 `[]`，这段配置从未生效       | 显式声明规则，默认空数组                        |
| 13  | `isDebugMode` 直接访问 `window`                    | Node / SSR / 构建脚本里调用即抛错                               | 全部走能力探测，任何环境可安全调用              |
| 14  | `ColorScheme` 接口在 types.ts 里重复声明两次       | 无害但说明无人维护                                              | 单一来源                                        |
| 15  | 无输出通道抽象                                     | 测试只能 spy 全局 console，页面无法镜像                         | 引入 `LogSink`，可捕获、可上报、可镜像          |
| 16  | 无命名空间层级                                     | 只能靠字符串手写 `'a › b'`                                      | `child()` 逐级派生并继承配置                    |

---

## 六、设计取舍

- **不引入渐变。** 原实现的级别标签用了 `linear-gradient`，浏览器里好看，但 ANSI 无法表达，会导致两种环境观感割裂。改用纯色，换来三态一致。
- **不做内部 `fetch`。** 需要 ASCII 资源时由调用方读取后传入字符串，日志组件保持纯粹。
- **等级标签统一 7 字符宽**（`SUCCESS` 的长度），让多行日志的列严格对齐。
- **`time()` 返回停止函数**，而不是 `console.time` / `timeEnd` 的全局标签池——后者在并发或同名嵌套时会互相覆盖，且未匹配时静默失败。

---

## 七、待补充

- `common-tools` 目前只有 `logger` 与 `env`；HTTP 封装、存储、事件总线需要时再建**独立包**，不要往本包堆
- 尚未接入远端日志上报（sink 已预留接口）
