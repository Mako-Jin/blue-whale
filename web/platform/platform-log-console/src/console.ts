import { createApiClient } from 'common-api';
import type { ClientLogItem, JsonObject, JsonValue } from 'common-api';
import type { LogRecord } from 'common-tools';

import { LogSource } from './log-source';
import { LogReporter } from './reporter';
import type { ReporterState } from './reporter';

/** 上报方应用标识，会随每批日志落库，用于区分来源 */
export const APP_ID = 'platform-log-console';

/** 应用版本 */
export const APP_RELEASE = '0.1.0';

/** 本次会话标识 */
export const SESSION_ID = `s-${Math.random().toString(36).slice(2, 10)}`;

/**
 * 当前链路标识。
 *
 * <p>真实项目里它由请求层在每次业务请求开始时生成（并回填响应头里的值）。
 * 演示页显式维护一个变量，是为了让"这里产生的日志"与"服务端那次请求"共用同一个 id ——
 * 这正是 `createApiClient` 的 `traceIdProvider` 要解决的问题：
 * 若每次调用各生成一个 id，前后端日志就永远串不起来。
 */
let currentTraceId: string | undefined;

export function setCurrentTraceId(traceId: string | undefined): void {
  currentTraceId = traceId;
}

export function getCurrentTraceId(): string | undefined {
  return currentTraceId;
}

export const api = createApiClient({
  // 契约里的路径已经带了 /api 前缀，因此基地址留空（开发期由 vite 代理转发）
  traceIdProvider: () => currentTraceId,
});

/* ---------------------------------------------------------------- 上报器 */

const reporterListeners = new Set<(state: ReporterState) => void>();

export const reporter = new LogReporter({
  app: APP_ID,
  release: APP_RELEASE,
  sessionId: SESSION_ID,
  send: (body) => api.log.report(body),
  batchSize: 20,
  flushIntervalMs: 3000,
  maxBuffer: 500,
  onChange: (state) => {
    for (const listener of reporterListeners) listener(state);
  },
});

/**
 * 订阅上报状态。
 *
 * <p>订阅时**立即回调一次当前状态**：只在变化时通知的话，页面首帧会显示全 0，
 * 而那时缓冲里可能已经有数据了 —— 首屏数字与实际不符，是最容易被当成 bug 的现象。
 */
export function onReporterChange(listener: (state: ReporterState) => void): () => void {
  reporterListeners.add(listener);
  listener(reporter.getState());
  return () => {
    reporterListeners.delete(listener);
  };
}

/* ------------------------------------------------------------- 日志产生 */

const MAX_LOCAL_RECORDS = 100;

const localRecords: LogRecord[] = [];

const localListeners = new Set<() => void>();

export const logSource = new LogSource({
  sessionId: SESSION_ID,
  release: APP_RELEASE,
  onRecord: (record) => {
    localRecords.unshift(record);
    if (localRecords.length > MAX_LOCAL_RECORDS) localRecords.length = MAX_LOCAL_RECORDS;

    reporter.push(toReportItem(record));

    for (const listener of localListeners) listener();
  },
});

export function getLocalRecords(): readonly LogRecord[] {
  return localRecords;
}

export function clearLocalRecords(): void {
  localRecords.length = 0;
  for (const listener of localListeners) listener();
}

export function onLocalRecordsChange(listener: () => void): () => void {
  localListeners.add(listener);
  return () => {
    localListeners.delete(listener);
  };
}

/* ------------------------------------------------------------------ 转换 */

/** LogRecord → 上报条目。字段名与契约一一对应，这里只做透传与必要裁剪 */
function toReportItem(record: LogRecord): ClientLogItem {
  return {
    level: record.level,
    namespace: record.namespace,
    timestamp: record.timestamp,
    message: record.message,
    data: record.data.map((item) => sanitize(item)),
    context: sanitizeObject(record.context),
    traceId: record.traceId,
    sessionId: record.sessionId,
    route: record.route,
    release: record.release,
  };
}

/**
 * 把日志数据裁剪成可 JSON 序列化的形态。
 *
 * <p>日志数据里出现函数、循环引用、DOM 节点是常事，而 `JSON.stringify` 遇到它们会直接抛错，
 * 结果是**整批上报失败** —— 一条日志夹了个不可序列化的值，其余 499 条一起遭殃。
 * 在这里递归转成安全值，让脏数据只影响它自己。
 *
 * <p>返回类型直接声明为 {@link JsonValue} 而不是 `unknown`：脏数据在这里被一次性
 * 洗干净，调用方不再需要任何断言 —— 有断言就有"洗没洗干净"没人管的风险。
 */
function sanitize(value: unknown, depth = 0): JsonValue {
  if (depth > 4) return '[层级过深]';

  // undefined 与 null 都归为 null：JSON 里没有 undefined，如实降级比留个空洞更好
  if (value === null || value === undefined) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return value;
  if (typeof value === 'boolean') return value;
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
  for (const [key, item] of Object.entries(value)) {
    result[key] = sanitize(item, depth + 1);
  }
  return result;
}

/** `context` 在契约里是对象，因此单独收口一次，避免把整体断言成对象 */
function sanitizeObject(value: Record<string, unknown> | undefined): JsonObject | undefined {
  if (!value) return undefined;
  const result: JsonObject = {};
  for (const [key, item] of Object.entries(value)) {
    result[key] = sanitize(item, 1);
  }
  return result;
}
