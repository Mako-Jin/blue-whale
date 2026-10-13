# platform-event-console · 行为事件控制台

打通「**埋点 → 上报 → 落库 → 分析查询**」的端到端演示，端口 **3300**。

它是 `platform-log-console`（日志，:3200）的**姊妹页**，但不是它的复制：
两条管道的数据性质不同（见 `docs/decisions/0011`），因此这里演示的是**事件**特有的问题 ——
身份缝合、脱敏、屏蔽、事件名契约。

```bash
# 先起后端（默认 h2 即可，无需装数据库）
cd server && mvn -pl :sts-app-server spring-boot:run

# 再起本页
cd web && pnpm dev --filter platform-event-console   # http://127.0.0.1:3300
```

## 页面上能看到什么

左栏是**客户端视角**，右栏是**服务端视角**。之所以并排，是因为只看一边无法判断
"上报到底成没成"：左栏全绿但右栏没数据，说明问题在通道；两边都有，链路才通。

### 左栏（客户端）

| 区块               | 演示什么                                                               |
| ------------------ | ---------------------------------------------------------------------- |
| 身份与会话（模拟） | 点「登录」后 `userId` 出现而 `deviceId` 不变 —— **身份缝合**的现场证据 |
| 触发语义化事件     | 业务侧只调 `track(事件名, 属性)`，其余全在内核里                       |
| 中间件：屏蔽       | 命中名单的事件**在进入缓冲之前**就被丢弃（左栏不出现、服务端收不到）   |
| 上报通道           | 批量 / 有界 / 退避 / 卸载兜底 的实时状态                               |
| 埋点自身异常       | 埋点出错只落在这里，**不会抛给业务代码**                               |
| 本地事件流         | 已产生的事件信封（含 `propsVersion` 与身份）                           |

### 右栏（服务端）

按 `serverTime` 倒序的分页明细，以及按事件名聚合的分布。
过滤只看 `serverTime`：客户端时间不可信（用户会改系统时间）。

### 底栏

当前事件上下文（`sessionId` / `deviceId` / `userId` / `route` / `release`）——
与左栏产生的事件逐条对照，能直接看出"上下文补齐全在内核里，业务没写过一行"。

## 三个刻意设计的演示点

### 1. 脱敏在**离开浏览器之前**发生

点「查看联系方式」，`phone` / `email` 被中间件改写成 `138******00` / `cap******ev`，
而**未在脱敏名单里的字段原样保留** —— 脱敏不该连带毁掉分析用的属性。

放在客户端做而不是服务端：放到服务端就晚了，那时明文已经走完了整条链路
（浏览器内存、网络抓包、服务端访问日志、网关缓冲）。**这是本页最值得看的一处。**

### 2. 屏蔽与上报是两件事

「屏蔽」让事件从未进入通道；「停掉自动上报」只是让数据攒在缓冲里。
区分二者很重要：**屏蔽意味着这条数据在本端根本不存在**，事后无从恢复。

### 3. 登录 / 登出的顺序是反的，而且是有意的

```ts
// 登录：先写上下文，再发事件 —— 否则"登录成功"这条会记在上一个身份名下
tracker.updateContext({ userId });
tracker.track('user.login', { userId });

// 登出：先发事件（此时上下文里还有 userId），再清身份
tracker.track('user.logout', {});
tracker.clearUser();
```

`tests/events.test.ts` 把这两个顺序各钉了一条用例 —— 顺序写反不会有任何报错，
只会让"谁登出了"这个信息永久丢失。

## 它自己不实现什么

这个应用只做**接线**（约 40 行的 `src/events.ts`），机制全在下游：

| 能力                                  | 归属                                               |
| ------------------------------------- | -------------------------------------------------- |
| 事件信封、`track()`、中间件、多订阅者 | `common-tools/events`                              |
| 批量 / 有界 / 退避 / 卸载兜底         | `common-tools/report`                              |
| 任意值修剪成可序列化形态              | `common-tools/json`                                |
| 契约类型与接口装配                    | `common-api`（`api.event.*`，全部由 OpenAPI 生成） |

这样划分是为了让内核**对具体接口零依赖** —— 它可以被构建脚本、Node 工具链直接使用。

## 本地校验

```bash
pnpm --filter platform-event-console typecheck
pnpm --filter platform-event-console lint
pnpm --filter platform-event-console stylelint
pnpm --filter platform-event-console test
pnpm --filter platform-event-console build    # 别跳过这一项
```

> **`build` 这一项别跳过。** 本页与 `platform-log-console` 共用一套版式，复制过程中
> 有一个类名引用了**后者才有的 LESS 令牌**（`@level-error`）。dev server 不会报错
> （按需编译，没人访问那条样式分支就不编译），单元测试也不会报错（不碰样式），
> 只有生产构建会失败：`variable @level-error is undefined`。
> 结论：**复制版式之后，必须实际跑一次构建**；本包的语义令牌只有
> `@event-accent` / `@event-mask` / `@event-dropped` / `@event-ok` / `@event-error` 五个。

> **为什么坚持走 vite 代理（同源）而不是让浏览器直连后端：**
> 卸载兜底用的是 `fetch(..., { keepalive: true })`；跨域时它会带 CORS 预检，
> 而预检在页面卸载阶段不可能完成 —— 结果是"页面关闭时那一批数据静默丢失"，
> 丢在最需要它的时刻。同源完全没有这个问题。
>
> 顺带一提：**这里刻意没用 `navigator.sendBeacon`**。它发带自定义
> `Content-Type: application/json` 的 `Blob` 时同样会触发预检，且**静默失败**、
> 无法判定是否交出去了；`keepalive` 有明确返回值，代价是 64 KB 上限 ——
> 因此 `src/events.ts` 里按体积裁剪，且优先保住最近的批次。

## 尚未实现（别误以为已有）

- **采样**（事件采样必须"按会话整体采"，否则漏斗断裂）
- **服务端二次脱敏**（服务端只做长度截断）
- **同意管理与"按用户删除"**
- **Kafka / ClickHouse 落地**（当前 Sink 是关系库）

完整清单见 `docs/decisions/0011` 的「尚未完成」表。

## 它也是一个 qiankun 子应用

本页可单独打开（`:3300`），也可被主应用 `platform-portal` 挂载（路由 `/event-console/*`），
与 `platform-log-console` 同构。入口用 `renderWithQiankun()` 导出生命周期，
`vite.config.ts` 里声明的应用名 `platform-event-console` 必须与基座注册表**逐字一致** ——
写错不会报错，只会一直卡在"加载中"。

两点与本页既有约束直接相关：

- **独自跑时也会点「登录」**，那只是页面上的模拟身份；被基座挂载时，基座的「登录」按钮会通过
  **全局状态**把 `userId` 下发下来，右下角状态标记随之变化。两套身份互不干扰，别混淆。
- **构建产物的 `base` 是 `/event-console/`（仅 build）**，dev 下仍为 `/`。改这个值会让热更新静默失效。

> 被基座加载时，本页的 React 组件级热更新不生效（样式正常）。改组件时单独
> `pnpm --filter platform-event-console dev` 更省时间，原因见 `docs/decisions/0012`。

> **认证接线（ADR-0026）**：查询接口需要 `event:client:read`，因此本页必须带令牌 ——
> 会话不由本页拥有：基座内用宿主下发的认证桥，独立运行由 `createConsoleAuth` 自建
> （开发账号只在 `import.meta.env.DEV` 为真时才构造 —— 生产构建下整段凭据被摇掉、口令不进产物，
> 见 `docs/decisions/0033`）。装配在 `src/events.ts`
> （`createApiClient({ accessTokenProvider, onUnauthorized })`），`src/main.tsx` 独立运行分支
> 先 `await consoleAuth.ensureSession()` 再渲染。
> **事件查询结果只含当前登录租户**，且**事件体里自带的 `userId` 不是归属依据**（客户端可伪造）——
> 归属取服务端会话。上报（`POST /api/events/client`）同样**刻意保持公开**。

> **两栏按菜单码过滤（`docs/decisions/0031`）**：左栏（埋点演示）与右栏（服务端查询）各自有一条菜单码
> （`ui:event_track:view` / `ui:event_query:view`），由基座连同读写权限一起下发。两道门是**独立**的：
> 菜单码决定这一栏出不出现，读写权限决定里面的请求能不能成功。
> 右栏不可见时查询副作用**提前 return**（那时必然拿到 `40301`，白留一屏错误）。
> **两栏里只剩一栏可见时布局会收成单列** —— 否则那一栏会被按 5:7 挤在左半边，
> 右边一片空白，看起来像"另一栏加载失败"。
> 演示区里的按钮**不按权限显隐**：这一栏的价值就在于亲手造一条事件、再看它落库。

## 相关

- `docs/decisions/0011-行为事件管道与前端埋点内核.md` —— 决策与取舍
- `docs/decisions/0012-微前端接入与子应用改造.md` —— 子应用改造与已知限制
- `docs/decisions/0026-日志与事件的鉴权与租户隔离.md` —— 查询鉴权与租户归属
- `web/packages/common-tools/README.md` —— 事件内核 / 批量通道 / JSON 安全层 / 微前端桥接
- `server/README.md` —— 后端 `sts-server-track` 模块与表结构
