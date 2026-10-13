# 0034. 子应用认证接线统一到 createConsoleAuth

- **状态**：已采纳
- **日期**：2026-10-13
- **决策者**：舰长

## 背景

`0026` 新增了 `common-api` 的 `createConsoleAuth`（子应用"从哪取令牌"的统一答案），
并**明确留下一件事不做**：

> **`platform-admin` / `platform-audit-console` 各自那份 `src/auth/` 未动**：
> 下沉会连带改动它们的既有测试与 `scripts/verify-audit-pipeline.mjs`，
> 属于独立的一次重构。这是**已知的、有意的**技术债。

触发条件写在 `web/README.md`："出现第三个管理型子应用"。日志台与事件台是第三、第四个消费方 ——
也就是说，**触发条件早就满足了**，缺的只是"什么时候做"。

### 一句话的理由：那份重复已经**产生过**它预言的缺陷

上一轮做"子应用单跑"的真机验证时，两个控制台与这两个应用在同一个分支上**写法不同**：

```ts
// platform-log-console / platform-event-console（改前）
void consoleAuth.ensureSession().finally(() => render());

// platform-admin / platform-audit-console
try { await ensureStandaloneSession(); } catch (cause) { logger.error(...); }
```

前者把 `finally` 返回的新 Promise 丢给了 `void`，而 `channel.login` 失败时**会抛** ——
后端没起时浏览器里各得一条 `unhandledrejection: HTTP 502 Bad Gateway`。
四处副本、两种写法、两个写错。**这不是"可能会出问题"，是已经在日志里出现过的问题。**
所以本轮的目标不是"少几行代码"，而是**消掉这一类缺陷的落点**。

### 顺带纠正一句话：下沉的影响面被写大了

`0026` / `0033` / `web/README.md` 都说下沉要"连带改动 `scripts/verify-audit-pipeline.mjs`"。
实测（`grep` 全部验收脚本）：

```
initializeAuth / getAuthPort / ensureStandaloneSession / getStandaloneSession
  → scripts/ 下 0 命中
```

端到端脚本**不 import 应用代码**，它只驱动真实浏览器、靠 `data-testid` 定位元素。
真正的代价只有一处：**四个测试文件里的会话注入**。
（这与 `0029` 的"4 个子应用都泄漏"、`0032` 的"超大链路才会遇到"是同一种情形 ——
遗留清单里的判断都是**估算**，动手前值得先量一次，量的办法本身常常就是交付物。）

## 决策

### 1. 两个应用改为调用 `createConsoleAuth`，`src/auth/index.ts` 只剩一份参数

删掉的是**整套自建实现**：`AuthPort` 类型、`resolvePort()`、自有的
`getStandaloneSession()`、`ensureStandaloneSession()`，以及下面第 2 条那两个函数。

保留下来的只有应用自己的东西：应用名（决定日志前缀）与开发账号。
`platform-admin` 的 `auth:admin` / 审计台的 `auth:audit` 前缀因此不变 ——
日志里的归属不因为这次重构而改变，排查时不会有断层。

### 2. 两段式初始化取消 → `api()` 那层延迟创建也一起取消

旧实现要**两步**：`initializeAuth()` 先定端口，`getAuthPort()` 再取，
于是每个调用点都得保证"先初始化、再使用"，`api.ts` 也只好写成延迟创建的函数。
`createConsoleAuth` 的端口是**构造即就绪**的 —— 令牌在**每次请求时**才去问
"基座在不在"。那层延迟的理由（"端口要等 `mount` 之后才能确定"）随之消失。

**所以 `api()` 改成 `export const api = createApiClient({...})`，调用点同步改成 `api.xxx`**
（两个应用共 55 处，纯机械替换、由 `tsc` 兜住）。
留一个"理由已经消失的延迟包装"是这类缺陷最喜欢的藏身处 ——
它把"顺序对不对"变成一个只在运行时才回答的问题。

### 3. 权限那一侧连适配器都不需要了

权限 store（`common-tools/micro` 的 `createPermissionStore`）从 ADR-0031 起就接的是
`StandaloneIdentitySource`。`consoleAuth` **本身就是**它：被基座加载时
`readStandaloneIdentity()` 返回 `null`，store 于是走基座分支，
而不是去读一份**根本没被创建**的会话（那正是"两套会话并存"要避免的事）。

原来两个应用 `permissions.ts` 里那段"把应用自己的会话掰成两个动作"的适配器因此删掉，
`createPermissionStore(consoleAuth)` 一行取代。

### 4. 顺序约束没有消失，它换了个位置 —— 已写进注释

旧的硬规则是"`bindHost` 必须在 `initializeAuth` 之前"。现在改成：

> **`bindHost` 必须在 `connectPermissionSource()` 之前**。

理由是同一条：store 在 `connect` 时**读一次**身份，而"用宿主下发的身份还是自己的会话"
这个判据是"**有没有宿主桥**"（不是"有没有会话"）。排反了会把"被基座加载"读成
"独立运行"，于是自建起第二份会话 —— 两份会话并存，刷新各自失效。
两个应用的 `main.tsx` 都把这条写在了注册 qiankun 生命周期的那段上方。

独立运行分支（**先接权限来源、再备会话、最后渲染**）同理：`connect` 会订阅会话，
而独立运行时"登录完成"正是通过那次订阅推出来的；反了的症状是
"登录好了但界面不知道"（六个标签页一个不出现），刷新一下就正常。

### 5. 测试里的会话注入改为显式断言

`getStandaloneSession()` 的返回类型是 `SessionStore | null`（被基座加载时是 `null`）。
四个测试文件各自加一个局部辅助：

```ts
function standaloneSession(): SessionStore {
  const session = consoleAuth.getStandaloneSession();
  if (!session) throw new Error('测试环境不应存在宿主认证桥：独立运行分支必然成立');
  return session;
}
```

显式抛而不是到处写 `!`：顺带把"**测试必须在'无宿主桥'的前提下跑**"这条约束
写在明面上。同时删掉每个测试 `beforeEach` 里的 `initializeAuth()` ——
那本来是一次**测试专用的仪式**（生产代码里它由 `main.tsx` 承担），
忘了调用会静默落到"端口未初始化"，而现在不可能忘。

## 后果

- **四个子应用的认证接线至此逐字同形**：`consoleAuth` → `port` → api 客户端；
  `consoleAuth` → 权限 store。再出现写法分歧时，"只有一处是对的"这件事才看得出。
- 两个应用的 `src/auth/` 从 **145 + 147 行**降到 **约 45 + 50 行**（多数是注释）。
- `platform-admin` **29 例**、`platform-audit-console` **11 例**全绿；两包
  `typecheck` + ESLint + Stylelint 零错；两包 `tsc --noEmit && vite build` 通过。
- **产物自检重跑仍全绿**（6 个目录 / 18 个文件）：开发账号字面量这次是搬到了
  模块级的 `createConsoleAuth({ devAccount: import.meta.env.DEV ? {…} : undefined })`
  里 —— 仍然只在开发构建里构造，生产构建下条件折叠成 `undefined`、对象字面量进死分支被摇掉。
  这正是 `scripts/verify-bundle-secrets.mjs` 存在的意义：**改动了凭据所在的位置，
  就用机器判定一次，而不是靠读代码推演**。
- 契约、权限码、审计动作、错误码**一个字都没动**；对已挂载运行的基座而言行为不变。

## 本决策未覆盖

- **两个应用的 `auth/hooks.ts` 仍是各自一份**（三行 `useSyncExternalStore` 适配）。
  这是**刻意不合并**：合并要把 React 引进 `common-tools` —— 一个与框架无关的内核，
  为了三行代码引入框架依赖，与收敛方向相反。而且每个应用接的是**自己的** store 实例，
  这三行本来就该在各应用里。
- **`platform-portal` 的登录页侧不在这条链路上**：它持有会话本身，不做"桥优先"的选择。
- `platform-user` 仍未创建（`0031` 的产品缺口另计，与本条无关）。

## 与 `0026` / `0033` 的关系

- 本条是 `0026`「本决策未覆盖」第 3 条的**收尾**，并**纠正了那条里的影响面估算**
  （端到端脚本其实一个字节都不用改）。按本仓惯例**不回改 `0026` / `0033` 原文**
  （`0033` 的「未覆盖」里同样写着这条遗留），只在这里与各 README 建立指向。
- 上一轮（单跑真机验证）发现的"两个控制台漏接拒绝"那个缺陷，是本次下沉的**直接动因**；
  那一轮修掉的是**两个控制台**，本轮消掉的是**产生它的条件**。
