# 0035. 独立运行时入口脚本必须等 preamble 装好

- **状态**：已采纳
- **日期**：2026-10-13
- **决策者**：舰长

## 背景

`0012` 第 10 条解决了一个真问题：`@vitejs/plugin-react` 开发期往 `index.html` 的 `<head>`
塞一段**内联模块脚本**，而 qiankun 用 `import-html-entry` 取脚本**文本** `eval` 执行 ——
`eval` 出来的是普通脚本，静态 `import` 直接抛
`SyntaxError: Cannot use import statement outside a module`。

当时的做法是把静态 `import` 换成 `await import()`，整体包进 async IIFE，去掉 `type="module"`。
**语义没变，但顺序保证丢了**：

> 模块脚本按文档顺序**求值**是规范保证的；`(async () => {…})()` 不是 ——
> 它一上来就 `await`，把控制权交出去。

于是 `<body>` 里那段入口脚本（`vite-plugin-qiankun` 注入的
`import(… + '/src/main.tsx')`）会与 `import('/@react-refresh')` **抢跑**。
谁先完成取决于响应快慢，而 `/@react-refresh` 在**冷启动的开发服务器**上要先做依赖预构建，
明显更慢。抢输的后果是组件模块执行时 `$RefreshReg$` 还没装好：

```
Error: @vitejs/plugin-react can't detect preamble. Something is wrong.
unhandledrejection: @vitejs/plugin-react can't detect preamble. Something is wrong.
```

整个应用**白屏**。刷新一次就好 —— 因为那时服务器已经热了。

### 它是怎么被发现的：一次对自己的误判

做 `0034`（认证接线下沉）之后跑 `scripts/verify-standalone-dev-login.mjs`，
**第一次跑就红了两条**，报的正是上面这个错。第一反应是"改动弄坏了什么" ——
毕竟这是同一次会话里刚改过的两个应用，而报错也确实是"应用没起来"。

处置办法是**别猜，量**：只重启**一个** dev 服务器（事件台），另外三个保持热的，再跑一次 ——

| 服务器状态 | 结果 |
| --- | --- |
| 四个都冷（刚全部启动） | 2 个失败（`platform-event-console` / `platform-audit-console`） |
| 只重启事件台，其余保持热 | **只有事件台失败** |
| 四个都热（第二次跑） | 全部通过 |

只冷的那一个失败、而且是稳定复现 —— 和代码无关，和"服务器热不热"有关。
再读 `common-config/src/vite.js` 就一目了然：那段 async IIFE 是**我们自己**写出来的。

> 这与 `0029` / `0032` 的"遗留清单里的判断要先量一次"是同一类动作，
> 只是这次被量的是**自己刚做的改动**。附带一个结论：**"看起来像回归"的东西，
> 也要先做冷热/前后对照再下判断** —— 否则会在一个不存在的问题上改代码。

### 为什么只有独立运行时会遇到

跑在基座里时，子应用的 HTML 要先被基座 `fetch` 回来、再逐段 `eval`，
入口脚本与 preamble 之间隔了几跳，`@react-refresh` 反而先到。
这解释了为什么"四个控制台一直好好的"，却在我们最早自己打开的那个地址上白屏。

## 决策

### 1. preamble 把自己的 Promise 挂到全局

```html
<script>window.__bwDevPreambleReady = (async () => {…})()</script>
```

### 2. 入口脚本先等它、再 `import` 应用

```html
<script>(window.__bwDevPreambleReady ?? Promise.resolve()).then(() => {import(…'/src/main.tsx')…})</script>
```

两段都是**普通脚本**，按文档顺序执行 ⇒ 入口读到的一定是 preamble 已经**发起**的那次导入。
`?? Promise.resolve()` 是兜底：万一找不到那段脚本（没被 qiankun 插件改写、或写法不认识），
行为退回改写前，**不会更糟**。

### 3. 只在真的改写了 preamble 时才动入口

`toDynamicImports()` 认不出语法时会返回 `null`、preamble 原样保留（模块脚本，顺序由规范保证）。
那种情况下入口**一个字都不改** —— 不动才是对的。

### 4. 两处字符串各只写一遍

全局键抽成常量 `PREAMBLE_READY`：写它的人和读它的人必须用同一个键，
两处各写一个字面量是最容易改一处忘一处的地方。

入口脚本的正则用 `(?!<\/script>)` 逐字符排除闭合标签，**不用** `[\s\S]*?` ——
后者允许跨过 `</script>`，会从前面那段 preamble 一路匹配到入口，把**两段一起**包住，
产出"看着合法但空转"的 HTML。这个坑是本仓在 nginx 那边反复记过的同一类。

## 后果

- **`pnpm dev` 之后第一次打开子应用不再白屏**。这不是边角场景 ——
  它是开发者每天遇到的那一次，也是本轮误判的起点。
- 基座那条路不变：`scripts/verify-micro-frontend.mjs` 全过（挂载、全局状态下发、
  三维应用、HMR、无浏览器报错、无 4xx/5xx）。
- `apply: 'serve'` 未改 ⇒ **生产构建完全不受影响**（这段改写只发生在 dev server）。
- 验证方式就是"制造缺陷的现场"：**把四个子应用全部冷启动**，再跑
  `scripts/verify-standalone-dev-login.mjs` —— 一次通过；环境台（WebGL、单跑）
  另用 `scripts/verify-scene-switching.mjs` 在冷启动下过一遍。

### 顺带修掉三个老脚本的收尾

同一轮里还发现 `verify-menu-permissions.mjs` / `verify-portal-auth.mjs` /
`verify-audit-pipeline.mjs` **结论打完进程不结束**（`finally` 里只 `chrome.kill()`，
CDP 那条长连接把事件循环挂住）—— 在流水线里表现为**超时**，而不是那行结论。
新脚本（`0031` / `0033` 那两轮写的）早就有 `ws.close()` + 兜底 `process.exit`，这次回灌给老脚本：
三个脚本现在 3~5 秒内自行退出、退出码即结论。

## 本决策未覆盖

- **React 组件级热更新在 qiankun 下仍不生效**（`0012` 记的已知限制，根因未定位）。
  本次改动**没有**改变它：`verify-micro-frontend.mjs` 第 2b 步仍然报"未更新"，
  且脚本刻意**不断言**这一步（它是既有状态，不是本轮引入的）。把它当回归是不对的 ——
  但也不该一直这样，值得单独一轮。
- **没有加自动化测试**。这条缺陷只在"**冷启动 + 真实浏览器**"下出现：
  jsdom 里既没有 Vite 的 HTML 改写，也没有真实的模块求值顺序。守它的因此是
  **真机脚本 + "先冷启动"这个前提**，而不是某条单测。这也是它此前一直没被发现的原因。

## 与 `0012` 的关系

`0012` 第 10 条记录的决策（改写 preamble 让 qiankun 能 `eval`）**不回改** —— 它仍然成立，
而且必须先有它才有本条要处理的问题。本条是它的**补充**：
同一处代码的**第二件事**（把改写丢掉的顺序保证显式补回来），以及它引入的代价的处置。
