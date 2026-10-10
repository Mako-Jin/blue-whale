/**
 * JSON 安全层。
 *
 * <p><b>为什么单独一层：</b>日志与行为事件两条管道都要把"用户随手塞进来的任意值"
 * 修剪成可 JSON 序列化的形态，需求完全一致 —— 各写一份的结果一定是两份行为不同的实现，
 * 而其中一份会先被遗忘。
 *
 * <p>这一层与框架无关，不依赖任何运行时第三方库。
 */

/**
 * 任意 JSON 值。
 *
 * <p><b>为什么需要它：</b>契约里"原样透传的日志数据 / 事件属性"这类字段，
 * 后端声明是 `Object`。OpenAPI 的单层 schema 表达不了"任意 JSON"，只能用空 schema；
 * 而对空 schema 的保守处理就是"不声明形状"，等价于"不知道"。
 *
 * <p>这个"不知道"落到 TypeScript 上是什么，两代生成链给出了两种都不理想的答案：
 *   - springdoc 2.x（OpenAPI 3.0）：`Record<string, never>` —— 语义变成
 *     "一个不允许有任何字段的对象"，于是 `{ orderId: 'SO-2026-0001' }`
 *     这种最普通的附加数据在编译期就被拒了；
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

/** 任意 JSON 值的数组，对应日志的 `data` */
export type JsonArray = JsonValue[];

/** 任意 JSON 值的对象，对应日志的 `context` 与事件的 `props` */
export type JsonObject = { [key: string]: JsonValue };

/**
 * 递归深度上限。
 *
 * <p>取 4 而不是更大：日志与事件里出现超过 4 层嵌套，通常意味着把整个响应体或
 * 一个框架内部对象直接塞了进来 —— 那种数据即使完整上报也没人会读，
 * 而它带来的序列化开销与存储膨胀是确定的。
 */
const MAX_DEPTH = 4;

/**
 * 把任意值修剪成可 JSON 序列化的形态。
 *
 * <p><b>为什么必须存在：</b>`JSON.stringify` 遇到函数、Symbol、循环引用、BigInt
 * 会**直接抛错**。一条数据夹了个不可序列化的值，结果是**整批上报失败** ——
 * 另外 499 条正常数据一起遭殃。在这里递归转成安全值，让脏数据只影响它自己。
 *
 * <p>返回类型直接声明为 {@link JsonValue} 而不是 `unknown`：脏数据在这里被一次性
 * 洗干净，调用方不再需要任何断言 —— 有断言就有"洗没洗干净"没人管的风险。
 */
export function sanitize(value: unknown, depth = 0): JsonValue {
  if (depth > MAX_DEPTH) return '[层级过深]';

  // undefined 与 null 都归为 null：JSON 里没有 undefined，如实降级比留个空洞更好
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number') {
    // NaN / Infinity 序列化成 null 会静默失真，转成字符串至少保留"这里是个非有限数"这个事实
    return Number.isFinite(value) ? value : String(value);
  }
  if (typeof value === 'boolean') return value;
  // BigInt 无法直接序列化（JSON.stringify 会抛错），转字符串是唯一无损的选择
  if (typeof value === 'bigint') return value.toString();
  // 函数与 Symbol 无法序列化，转成可读标记，而不是让它们炸掉整批上报
  if (typeof value === 'function' || typeof value === 'symbol') return `[${typeof value}]`;

  if (value instanceof Error) {
    return { name: value.name, message: value.message, stack: value.stack ?? '' };
  }

  if (Array.isArray(value)) {
    return value.map((item) => sanitize(item, depth + 1));
  }

  const result: JsonObject = {};
  for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
    result[key] = sanitize(item, depth + 1);
  }
  return result;
}

/**
 * 把 `Record<string, unknown>` 收口成 {@link JsonObject}。
 *
 * <p>单独提供一个函数而不是让调用方 `as JsonObject`：事件与日志的属性在契约里就是对象，
 * 需要的是一个**确定的**对象结果（空入参得到空对象，而不是 undefined），
 * 这个语义写在类型断言里表达不出来。
 */
export function sanitizeObject(value: Record<string, unknown> | undefined): JsonObject | undefined {
  if (!value) return undefined;
  const result: JsonObject = {};
  for (const [key, item] of Object.entries(value)) {
    result[key] = sanitize(item, 1);
  }
  return result;
}
