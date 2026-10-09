import type { components, paths } from './generated/schema';

/**
 * 契约类型工具。
 *
 * <p>这一层的存在意义是：**接口形状只从契约里取，不从人脑里来**。
 * 每个工具都是从一个"接口定义"上做条件推导，因此契约一变（字段增删、类型调整），
 * 所有引用处的类型立刻跟着变 —— 不需要任何人记得去同步。
 *
 * <p>推导的对象是 `paths[...]['get' | 'post' | ...]` 这个"操作对象"，
 * 而不是手写的接口类型。
 */

/** 契约中的全部接口路径 */
export type ApiPaths = paths;

/** 契约中的全部数据模型 */
export type ApiSchemas = components['schemas'];

/** 从操作定义中取出查询参数类型 */
export type QueryOf<T> = T extends { parameters: { query?: infer Q } } ? Q : never;

/** 从操作定义中取出路径参数类型 */
export type PathOf<T> = T extends { parameters: { path: infer P } } ? P : never;

/** 从操作定义中取出请求体类型 */
export type BodyOf<T> = T extends { requestBody: { content: { 'application/json': infer B } } }
  ? B
  : never;

/**
 * 从操作定义中取出成功响应的信封类型（即后端的 `R<T>`）。
 *
 * <p>响应的 content-type 被 springdoc 生成为通配形式（控制器未显式声明 produces），
 * 因此这里按"任意键"取值，而不是写死 `application/json` —— 写死会让类型静默退化成 never。
 */
export type EnvelopeOf<T> = T extends { responses: { 200: { content: infer C } } }
  ? C extends Record<string, infer V>
    ? V
    : never
  : never;

/**
 * 解开统一响应包装，得到 `data` 的类型。
 *
 * <p>契约里记录的是完整的信封（`code` / `message` / `data` / `timestamp`），
 * 而业务代码关心的是解包之后的 `data`。请求客户端在运行时会做同样的解包，
 * 这个类型让编译期与运行时保持一致。
 */
export type DataOf<T> = EnvelopeOf<T> extends { data?: infer D } ? D : EnvelopeOf<T>;

/* ---------------------------------------------------------------- 任意 JSON 值 */

/**
 * 任意 JSON 值。
 *
 * <p><b>为什么需要它：</b>契约里"原样透传的日志数据"这类字段，后端声明是 `Object`。
 * OpenAPI 的单层 schema 表达不了"任意 JSON"，只能用空 schema；
 * 而对空 schema 的保守处理就是"不声明形状"，等价于"不知道"。
 *
 * <p>这个"不知道"落到 TypeScript 上是什么，两代生成链给出了两种都不理想的答案：
 *   - springdoc 2.x（OpenAPI 3.0）：`Record<string, never>` —— 语义变成
 *     "一个不允许有任何字段的对象"，于是 `{ orderId: 'SO-2026-0001' }`
 *     这种最普通的日志数据在编译期就被拒了；
 *   - springdoc 3.x（OpenAPI 3.1）：`unknown` —— 又过宽了：函数、Symbol、
 *     DOM 节点都能通过编译，而这些恰恰是 `JSON.stringify` 会抛错的东西，
 *     一条脏数据就能让整批上报失败（500 条一起丢）。
 *
 * <p>两种都不合适，所以在这里做一次**收紧**：既容纳任意 JSON 结构，
 * 又挡住不可序列化的值。这是**唯一**需要人工介入的类型位置，
 * 且只作用于确实声明为 `Object` 的字段，其余字段继续完全由契约生成。
 */
export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

/** 任意 JSON 值的数组，对应契约里的 `data` */
export type JsonArray = JsonValue[];

/** 任意 JSON 值的对象，对应契约里的 `context` */
export type JsonObject = { [key: string]: JsonValue };

/**
 * 把模型中的 `data` / `context` 两个字段替换为真正的 JSON 值类型。
 *
 * <p>其余字段原样取自契约 —— 因此"契约里还有哪些字段、哪些必填"依然由生成物决定，
 * 手写的只有这两个字段的形态。
 */
export type WithJsonPayload<T> = Omit<T, 'data' | 'context'> & {
  data?: JsonArray;
  context?: JsonObject;
};
