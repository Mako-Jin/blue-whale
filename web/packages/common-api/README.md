# common-api — 前端契约包

把 `docs/api/openapi.json` 变成**有类型的、可以直接调用的**接口方法。

**这个包里没有一个字段名或类型是手写的。** 手写的只有"哪个方法对应哪条路径"这层接线 —— 而路径字面量受 `keyof paths` 约束，拼错会在编译期报错。

```
docs/api/openapi.json ──openapi-typescript──▶ src/generated/schema.d.ts ──装配──▶ createApiClient()
      （唯一真相源）                                    （生成物，禁止手改）
```

---

## 一、这个包解决什么问题

| 问题             | 没有它会怎样                                                                                    |
| ---------------- | ----------------------------------------------------------------------------------------------- |
| 接口形状从哪来   | 手写一份 SDK → 它是一份**会过期的副本**，后端加字段前端不会自动多出来，表现是运行时 `undefined` |
| 请求怎么发       | 每个应用各写一套请求封装，鉴权头、错误处理、超时策略各不相同                                    |
| 契约漂移怎么发现 | 靠人记得同步。人一定会忘                                                                        |

本包**只做装配**：请求怎么发、错误怎么翻译全在 `common-tools/http`，接口长什么样全在契约生成的类型里。因此：

- **换一个接口** → 永远不用碰传输层；
- **改传输策略**（鉴权、脱敏、重试）→ 不用扫一遍接口定义。

详见 `docs/decisions/0006-接口契约的生成与消费.md` 与 `0007-前端请求分层与错误模型.md`。

---

## 二、目录结构

```
packages/common-api/
├── src/
│   ├── index.ts              统一出口
│   ├── generated/
│   │   └── schema.d.ts       ◀── openapi-typescript 生成，禁止手改
│   ├── types.ts              契约类型工具 + 任意 JSON 值的收紧
│   ├── log.ts                日志域接口（路径常量 + 方法接线）
│   ├── events.ts             行为事件域接口（上报 / 分页 / 详情 / 聚合）
│   ├── system.ts             系统域接口（健康检查）
│   └── client.ts             createApiClient：把 http 与各域接口装配起来
├── tests/
│   ├── log.test.ts           含契约形状的类型断言
│   └── events.test.ts        事件域的路径与方法接线断言
├── eslint.config.js          引用 common-config
└── vite.config.ts            测试配置
```

---

## 三、快速开始

先在应用 `package.json` 声明依赖（pnpm workspace 内）：

```json
{
  "dependencies": {
    "common-api": "workspace:*",
    "common-tools": "workspace:*"
  }
}
```

### 创建客户端

```ts
import { createApiClient } from 'common-api';

/** 当前链路标识，由业务请求的开始处生成并传递 */
let currentTraceId: string | undefined;

export const api = createApiClient({
  // 契约里的路径已经带了 /api 前缀，因此基地址留空（开发期由 vite 代理转发）
  baseUrl: '',
  traceIdProvider: () => currentTraceId,
});
```

### 调用

```ts
// 健康检查（探测类请求：silent 让失败不刷日志）
const health = await api.system.health();

// 分页查询，入参类型直接从契约的 query 参数推导，含继承自 PageQuery 的 page / size
const page = await api.log.page({ level: 'error', keyword: '支付', page: 1, size: 20 });
page.records.forEach((log) => console.log(log.serverTime, log.message));

// 按链路查整条链路（按时间升序）
const records = await api.log.listByTraceId('trace-lq3k-1');
```

行为事件域的用法同构，只是换成了"人"的维度：

```ts
// 批量上报（单批最多 500 条；生产环境不要这么直接调，走 common-tools/report 的批量通道）
const result = await api.event.report({ app: 'portal', events: batch });
result.accepted; // 服务端确认接收的条数
result.rejected; // 服务端拒收的条数（事件名非法等）

// 分页查询与聚合
const page = await api.event.page({ userId: 'u-42', page: 1, size: 20 });
const stats = await api.event.stats({ app: 'portal' }); // 按事件名分组计数

// 聚合接口刻意不接受 eventName 过滤 —— 它本身就是按事件名分组的，
// 再传一个 eventName 去过滤是自相矛盾的（详见后端 ClientEventController 注释）
```

失败时抛的是 `common-tools/http` 的 `HttpError` 子类，按 `kind` 分支处理即可 —— 见该包的 README。

---

## 四、分层与边界

```
common-tools/http        传输内核 —— 刻意不认识任何业务接口
      ▲
common-api               契约装配 —— 只做接线
      ▲
platform/*               应用 —— 只调用 api.log.xxx / api.event.xxx / api.system.xxx
```

| 允许                                     | 禁止                                         |
| ---------------------------------------- | -------------------------------------------- |
| 应用调用 `api.log.page(...)`             | 应用直接 `fetch('/api/logs/client')`         |
| 应用调用 `api.event.report(...)`         | 在应用里手写事件字段名                       |
| 接口方法内部使用契约推导出的类型         | 在 `log.ts` / `events.ts` 里手写字段名或类型 |
| 通过 `api.http` 调**契约之外**的临时接口 | 修改 `src/generated/` 下的任何文件           |

> `api.http` 是逃生口：临时对接一个还没进契约的接口时用它，但**一旦该接口稳定下来就必须补进后端并重新生成契约**，否则它就成了一份"没人管的第二真相源"。

---

## 五、契约里"任意值"的处理

日志的 `data` / `context` 与事件的 `props` 在后端声明为 `Object`，OpenAPI 的单层 schema 表达不了"任意 JSON"，生成器只能给出保守形态——而且两代生成链的形态**都不理想**：

| 生成链                                 | 渲染结果                | 问题                                                                                                    |
| -------------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------- |
| springdoc 2.x（OpenAPI 3.0）           | `Record<string, never>` | "不允许有任何字段的对象"，`{ orderId: '…' }` 编译期就被拒                                               |
| springdoc 3.x（OpenAPI 3.1，**当前**） | `unknown`               | 过宽：函数、Symbol 也能通过编译，而它们正是 `JSON.stringify` 会抛错的东西——一条脏数据就能让整批上报失败 |

因此需要一次**收紧**。收口位置有两处，分工不同：

**① 类型本体在 `common-tools/json`**（`JsonValue` / `JsonArray` / `JsonObject`），本包只做转出：

```ts
export type { JsonArray, JsonObject, JsonValue } from 'common-tools/json';
```

放在下游而不是这里，是因为"把任意值修剪成可序列化形态"这个**动作**（`sanitize`）也必须与类型共用一份 —— 日志与事件两条管道都要用它。类型住在这里、函数住在那里，两边迟早在某个字段上分歧。

**② 字段替换工具在本包**（`types.ts`），按字段名分两个：

```ts
// 日志：结构化载荷放在 data + context 两个字段里
export type WithJsonPayload<T> = Omit<T, 'data' | 'context'> & {
  data?: JsonArray;
  context?: JsonObject;
};

// 事件：结构化载荷放在 props 一个字段里
export type WithJsonProps<T> = Omit<T, 'props'> & {
  props?: JsonObject;
};
```

`Omit` 之后其余字段仍完全来自契约——**这是全链路唯一需要人工介入类型的位置**。

> **一个曾经被掩盖的裂缝**：契约生成的 `PageResult<ClientLogVO>` 里 `records` 的元素，
> 与导出的 `ClientLogVO` 不是同一个类型（前者未经收紧）。OpenAPI 3.0 时代
> `Record<string, never>` 恰好可赋值给 `JsonValue`，错误被掩盖；升到 3.1 后 `unknown`
> 不再兼容，立刻变成编译错误。分页结果已用 `WithJsonRecords` 同样收紧，
> 且 `tests/log.test.ts` 有断言钉住——从分页里取出的记录必须能直接当 `ClientLogVO` 用。
>
> **事件域落进了同一个坑**：`PageResult<ClientEventVO>.records` 的元素同样未经收紧，
> 上报请求里的 `events` 数组元素也是。两处都已用同构的 `WithJsonEventRecords` /
> `WithJsonEventItems` 修正（见 `src/events.ts`）。**新增任何带"任意 JSON 字段"的域时，
> 这三处（本体、分页元素、请求体数组元素）都要一并收紧。**

---

## 六、契约怎么更新

**后端接口有任何增删改，都必须走完这三步，并与代码在同一次提交里完成。**

```bash
# 1. 构建后端
cd server && mvn -DskipTests install

# 2. 从运行中的应用导出契约（默认端口 18080，自动等待启动）
cd .. && bash scripts/export-openapi.sh

# 3. 重新生成前端类型
cd web && pnpm --filter common-api gen:api
```

### 类型守卫

`tests/log.test.ts` 里对**易退化的字段**做类型断言：

```ts
expectTypeOf<ClientLogQuery['startTime']>().toEqualTypeOf<number | undefined>();
```

这不是多余的：springdoc 曾把 `Long startTime` 静默推平为 `string`（查询参数位置带 `@Schema` 时以注解为准重建 schema，`type` 默认空串）。断言让这类退化在**测试阶段**失败，而不是在页面上显示成 `"1759999999999"`。

---

## 七、已知问题

| 问题                                                                         | 状态                                                                                      |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `openapi-typescript@7.13.0` 的 peer 约束是 `typescript@^5.x`，本项目用 6.0.3 | `pnpm install` 会打一条 peer 警告，**功能正常**（生成结果已验证）。升级生成器时需重新确认 |
| 契约尚未按域拆分                                                             | 目前单文件。接口量变大后考虑 `openapi/` 目录多文件方案                                    |

---

## 八、待补充

- 各域接口目前有 `log`、`events` 与 `system`；新增业务域时新建 `src/<域>.ts`，在 `client.ts` 里装配，并在 `tests/` 下补一份接线断言
- 鉴权尚未落地。接入后在 `createApiClient` 里通过 `onRequest` 钩子补 token 头，**不要改各域接口**
- 事件域的字段名与路径已由契约锁死，但**事件名本身**（`page.view` / `order.submit` 这类字符串）不在契约里 —— 它是前后端共用的一条正则，尚未收成一份可枚举的注册表；理由与代价见 `docs/decisions/0011`
