import type { components, paths } from './generated/schema';
import type { JsonArray, JsonObject } from 'common-tools/json';

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
 * 任意 JSON 值：{@link JsonValue} / {@link JsonArray} / {@link JsonObject}。
 *
 * <p><b>为什么定义在下游的 common-tools、这里只做转出：</b>它们不是契约生成物，
 * 而是**人工收紧**的产物 —— 契约里"原样透传"的字段声明为 `Object`，生成链给出的
 * `Record<string, never>`（OpenAPI 3.0）或 `unknown`（3.1）都不合适。
 * 日志的 `data`/`context` 与事件的 `props` 都要用它，而"把任意值修剪成可序列化形态"
 * 这个动作（`sanitize`）也必须共用一份，因此类型与函数一起放在 common-tools 侧。
 *
 * <p>完整的取舍过程见 `common-tools/src/json/index.ts` 的注释。
 */
export type { JsonArray, JsonObject, JsonValue } from 'common-tools/json';

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

/**
 * 把模型中的 `props` 字段替换为真正的 JSON 值类型。
 *
 * <p>与 {@link WithJsonPayload} 同理，只是字段名不同：行为事件把结构化载荷放在
 * `props` 里（与日志的 `data` + `context` 对应）。
 */
export type WithJsonProps<T> = Omit<T, 'props'> & {
  props?: JsonObject;
};
