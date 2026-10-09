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
│   ├── types.ts              契约类型工具 + JsonValue 修正
│   ├── log.ts                日志域接口（路径常量 + 方法接线）
│   ├── system.ts             系统域接口（健康检查）
│   └── client.ts             createApiClient：把 http 与各域接口装配起来
├── tests/
│   └── log.test.ts           含契约形状的类型断言
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

失败时抛的是 `common-tools/http` 的 `HttpError` 子类，按 `kind` 分支处理即可 —— 见该包的 README。

---

## 四、分层与边界

```
common-tools/http        传输内核 —— 刻意不认识任何业务接口
      ▲
common-api               契约装配 —— 只做接线
      ▲
platform/*               应用 —— 只调用 api.log.xxx / api.system.xxx
```

| 允许                                     | 禁止                                 |
| ---------------------------------------- | ------------------------------------ |
| 应用调用 `api.log.page(...)`             | 应用直接 `fetch('/api/logs/client')` |
| 接口方法内部使用契约推导出的类型         | 在 `log.ts` 里手写字段名或类型       |
| 通过 `api.http` 调**契约之外**的临时接口 | 修改 `src/generated/` 下的任何文件   |

> `api.http` 是逃生口：临时对接一个还没进契约的接口时用它，但**一旦该接口稳定下来就必须补进后端并重新生成契约**，否则它就成了一份"没人管的第二真相源"。

---

## 五、契约里"任意值"的处理

日志的 `data` / `context` 在后端声明为 `Object`，springdoc 只能推到 `{"type": "object"}`，而 `openapi-typescript` 会把它渲染成 `Record<string, never>` —— 语义变成"一个不允许有任何字段的对象"，于是 `{ orderId: 'SO-2026-0001' }` 这种最普通的日志数据在编译期就被拒了。

这不是调用方姿势不对，而是生成器对"任意值"的表达能力缺口。因此在 `src/types.ts` 里补一次：

```ts
export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type JsonArray = JsonValue[];
export type JsonObject = { [key: string]: JsonValue };
```

`log.ts` 用它只替换那两个字段：

```ts
type WithJsonPayload<T> = Omit<T, 'data' | 'context'> & {
  data?: JsonArray;
  context?: JsonObject;
};
```

`Omit` 之后其余字段仍完全来自契约 —— **这是全链路唯一需要人工介入类型的位置**。

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

- 各域接口目前只有 `log` 与 `system`；新增业务域时新建 `src/<域>.ts` 并在 `client.ts` 里装配
- 鉴权尚未落地。接入后在 `createApiClient` 里通过 `onRequest` 钩子补 token 头，**不要改各域接口**
