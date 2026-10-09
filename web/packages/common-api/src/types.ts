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
 * <p><b>为什么需要它：</b>契约里"原样透传的日志数据"这类字段，后端声明是 `Object`，
 * springdoc 只能推到 `{"type": "object"}` 这一步 —— 而 `openapi-typescript`
 * 会把这种"光秃秃的 object"渲染成 `Record<string, never>`，语义变成
 * "一个不允许有任何字段的对象"。结果是 `{ orderId: 'SO-2026-0001' }`
 * 这种最普通的日志数据在编译期就被拒了。
 *
 * <p>这不是调用方姿势不对，而是生成器对"任意值"的表达能力缺口：
 * 日志的 `data` 里可能是对象、字符串、数字、数组，OpenAPI 的单层 schema
 * 表达不了，只能用空 schema 表示"any"。
 *
 * <p>因此在这里补一次类型声明，把这类字段修正为真正的任意值。
 * 这是**唯一**需要人工介入的位置，且只影响确实声明为 `Object` 的字段，
 * 其余字段继续完全由契约生成。
 */
export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

/** 任意 JSON 值的数组，对应契约里的 `data` */
export type JsonArray = JsonValue[];

/** 任意 JSON 值的对象，对应契约里的 `context` */
export type JsonObject = { [key: string]: JsonValue };
