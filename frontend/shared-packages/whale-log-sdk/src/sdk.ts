/**
 * SDK 装配：{@link initLogSdk} 解析配置、装配两个上报通道与自动采集，并返回 {@link LogSdk}。
 */

import { LogChannel } from './batch';
import { installBehaviorCapture, type BehaviorCaptureHandle } from './behavior';
import { createFetchTransport } from './client';
import {
  installConsoleCapture,
  publishOriginalConsole,
  type ConsoleCaptureHandle,
} from './console';
import { createAuditTracker, type AuditTracker } from './tracker';
import {
  BEHAVIOR_ENDPOINT,
  DEFAULT_BACKOFF_BASE_MS,
  DEFAULT_BATCH_INTERVAL_MS,
  DEFAULT_BATCH_SIZE,
  DEFAULT_CONSOLE_LEVELS,
  DEFAULT_MAX_RETRIES,
  DEFAULT_SAMPLE_RATE,
  DEFAULT_TRACK_ATTR,
  FRONTEND_ENDPOINT,
  STORAGE_KEY_CLIENT_ID,
  STORAGE_KEY_TRACE_ID,
  STORAGE_KEY_USER,
} from './constants';
import { maskSensitive, maskText, maskUrl } from './mask';
import type {
  EventType,
  LogBatchRequest,
  LogItem,
  LogLevel,
  LogSdk,
  LogSdkConfig,
  PrivacyOptions,
  ResolvedLogSdkConfig,
} from './types';

/** ID 生成随机段长度。 */
const ID_RANDOM_LENGTH = 10;

/** ID 生成进制。 */
const ID_RADIX = 36;

/** 生成唯一标识（优先 crypto.randomUUID）。 */
function generateId(): string {
  const cryptoLike = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (cryptoLike && typeof cryptoLike.randomUUID === 'function') {
    return cryptoLike.randomUUID();
  }
  const stamp = Date.now().toString(ID_RADIX);
  const random = Math.random()
    .toString(ID_RADIX)
    .slice(2, 2 + ID_RANDOM_LENGTH);
  return `${stamp}-${random}`;
}

/** 读取会话存储（非浏览器或异常时返回 null）。 */
function readSession(key: string): string | null {
  try {
    if (typeof sessionStorage === 'undefined') {
      return null;
    }
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

/** 写入会话存储（异常静默）。 */
function writeSession(key: string, value: string): void {
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(key, value);
    }
  } catch {
    // 存储不可用（隐私模式等）时降级为内存内 ID，不阻断
  }
}

/** 读取或生成一次浏览器会话内唯一的 ID。 */
function ensureId(storageKey: string): string {
  const existing = readSession(storageKey);
  if (existing) {
    return existing;
  }
  const generated = generateId();
  writeSession(storageKey, generated);
  return generated;
}

/** 默认用户ID解析：从本地各应用的登录态缓存中解析数值型用户ID。 */
function defaultResolveUserId(): number | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  // 各应用登录态缓存键不同（whale_user / whale_admin_user / whale_ops_user / whale_wf_user ...），
  // 统一扫描以 user 结尾的键并兼容 id / userId 两种字段，避免上报记录的 user_id 丢失。
  const keys: string[] = [];
  for (let i = 0; i < localStorage.length; i += 1) {
    const key = localStorage.key(i);
    if (key && (key === STORAGE_KEY_USER || key.toLowerCase().endsWith('user'))) {
      keys.push(key);
    }
  }
  // 宿主约定的键优先，其余按扫描顺序
  keys.sort((a, b) => Number(b === STORAGE_KEY_USER) - Number(a === STORAGE_KEY_USER));
  for (const key of keys) {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) continue;
      const parsed = JSON.parse(raw) as { id?: unknown; userId?: unknown };
      const value = parsed?.userId ?? parsed?.id;
      if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
      }
      if (typeof value === 'string' && /^\d+$/.test(value)) {
        return Number(value);
      }
    } catch {
      // 单个键解析失败不影响其他键
    }
  }
  return null;
}

/** 读取浏览器 UA（非浏览器返回 null）。 */
function readUserAgent(): string | null {
  return typeof navigator !== 'undefined' ? navigator.userAgent : null;
}

/** 读取当前路由（非浏览器返回 undefined）。 */
function readRoute(): string | undefined {
  return typeof location !== 'undefined' ? location.pathname : undefined;
}

/** 全局 SDK 存档键：宿主与 qiankun 子应用共享同一实例。 */
const GLOBAL_SDK_KEY = '__BLUEWHALE_LOG_SDK__';

/** 读取全局 SDK 单例。 */
export function getGlobalLogSdk(): LogSdk | undefined {
  try {
    return (globalThis as Record<string, unknown>)[GLOBAL_SDK_KEY] as LogSdk | undefined;
  } catch {
    return undefined;
  }
}

/** 写入全局 SDK 单例。 */
function storeGlobalLogSdk(sdk: LogSdk): void {
  try {
    (globalThis as Record<string, unknown>)[GLOBAL_SDK_KEY] = sdk;
  } catch {
    // 全局对象不可写时退化为模块内实例，不影响主流程
  }
}

/** 清理全局 SDK 单例（仅当仍是当前实例时）。 */
function clearGlobalLogSdk(sdk: LogSdk): void {
  try {
    const registry = globalThis as Record<string, unknown>;
    if (registry[GLOBAL_SDK_KEY] === sdk) {
      delete registry[GLOBAL_SDK_KEY];
    }
  } catch {
    // 忽略
  }
}

/** 读取当前页面地址并按隐私规则掩码 query（非浏览器返回 null）。 */
function currentPageUrl(privacy: PrivacyOptions): string | null {
  if (typeof location === 'undefined') {
    return null;
  }
  return maskUrl(location.href, privacy);
}

/** 序列化任意值为字符串并掩码（失败时退化为 String）。 */
function safeStringifyMasked(value: unknown, privacy: PrivacyOptions): string {
  try {
    return JSON.stringify(maskSensitive(value, privacy));
  } catch {
    return maskText(String(value));
  }
}

/** 格式化单条 console 参数。 */
function formatArg(arg: unknown): string {
  if (arg instanceof Error) {
    return `${arg.name}: ${maskText(arg.message)}`;
  }
  if (typeof arg === 'string') {
    return maskText(arg);
  }
  return safeStringifyMasked(arg, {});
}

/** 拼接完整上报地址。 */
function joinUrl(baseUrl: string, endpoint: string): string {
  if (!baseUrl) {
    return endpoint;
  }
  return `${baseUrl.replace(/\/+$/, '')}${endpoint}`;
}

/** 补全配置默认值。 */
function resolveConfig(config: LogSdkConfig): ResolvedLogSdkConfig {
  return {
    enabled: config.enabled ?? true,
    singleton: config.singleton ?? true,
    release: config.release,
    baseUrl: config.baseUrl ?? '',
    frontendEndpoint: config.frontendEndpoint ?? FRONTEND_ENDPOINT,
    behaviorEndpoint: config.behaviorEndpoint ?? BEHAVIOR_ENDPOINT,
    console: {
      enabled: config.console?.enabled ?? true,
      levels: [...(config.console?.levels ?? DEFAULT_CONSOLE_LEVELS)],
    },
    behavior: {
      autoPageView: config.behavior?.autoPageView ?? true,
      autoClick: config.behavior?.autoClick ?? true,
      autoSubmit: config.behavior?.autoSubmit ?? true,
      trackAttr: config.behavior?.trackAttr ?? DEFAULT_TRACK_ATTR,
    },
    batch: {
      size: config.batch?.size ?? DEFAULT_BATCH_SIZE,
      intervalMs: config.batch?.intervalMs ?? DEFAULT_BATCH_INTERVAL_MS,
      maxRetries: config.batch?.maxRetries ?? DEFAULT_MAX_RETRIES,
      backoffBaseMs: config.batch?.backoffBaseMs ?? DEFAULT_BACKOFF_BASE_MS,
    },
    sampleRate: config.sampleRate ?? DEFAULT_SAMPLE_RATE,
    privacy: config.privacy ?? {},
    resolveUserId: config.resolveUserId ?? defaultResolveUserId,
    transport: config.transport ?? createFetchTransport(),
    onError: config.onError ?? (() => undefined),
  };
}

/** 空操作追踪器（禁用态使用）。 */
function createNoopTracker(): AuditTracker {
  return {
    track: () => null,
    subscribe: () => () => undefined,
    use: () => () => undefined,
    updateContext: () => undefined,
    getContext: () => ({ sessionId: '' }),
    clearUser: () => undefined,
  };
}

/** 构造禁用态（或降级）SDK：所有方法为空操作，近乎零开销。 */
function createNoopSdk(config: ResolvedLogSdkConfig): LogSdk {
  const tracker = createNoopTracker();
  return {
    track: () => undefined,
    log: () => undefined,
    flush: async () => undefined,
    destroy: () => undefined,
    config,
    tracker,
  };
}

/**
 * 初始化前端日志 SDK。
 *
 * <p>采集通道：console 劫持（通道③开发者日志）与自动行为埋点（通道④用户行为）；
 * 上报前统一过 {@code mask.ts} 掩码。任何内部异常均被吞掉，不影响业务。</p>
 *
 * @param config SDK 配置（可选，均有默认值）
 * @returns SDK 实例
 */
export function initLogSdk(config: LogSdkConfig = {}): LogSdk {
  const resolved = resolveConfig(config);
  if (!resolved.enabled) {
    return createNoopSdk(resolved);
  }

  // 宿主与 qiankun 子应用各自打包一份 SDK，复用全局实例可避免 console 被重复劫持、
  // PAGE_VIEW 被重复采集；子应用配置以宿主为准。
  if (resolved.singleton) {
    const existing = getGlobalLogSdk();
    if (existing) {
      return existing;
    }
  }

  // 把劫持前的原生 console 挂到全局，供 Logger 等场景拿回干净输出通道
  publishOriginalConsole();

  const traceId = ensureId(STORAGE_KEY_TRACE_ID);
  const clientId = ensureId(STORAGE_KEY_CLIENT_ID);

  /** 解析生效用户ID（解析钩子异常时回落 null）。 */
  const resolveUserId = (): number | null => {
    try {
      return resolved.resolveUserId();
    } catch {
      return null;
    }
  };

  /** 构造批量上报体（pageUrl 于发送时实时计算并掩码）。 */
  const buildRequest = (items: LogItem[]): LogBatchRequest => ({
    traceId,
    userId: resolveUserId(),
    clientId,
    pageUrl: currentPageUrl(resolved.privacy),
    items,
  });

  const frontendChannel = new LogChannel({
    endpoint: joinUrl(resolved.baseUrl, resolved.frontendEndpoint),
    size: resolved.batch.size,
    intervalMs: resolved.batch.intervalMs,
    maxRetries: resolved.batch.maxRetries,
    backoffBaseMs: resolved.batch.backoffBaseMs,
    sampleRate: resolved.sampleRate,
    transport: resolved.transport,
    buildRequest,
    onError: resolved.onError,
  });

  const behaviorChannel = new LogChannel({
    endpoint: joinUrl(resolved.baseUrl, resolved.behaviorEndpoint),
    size: resolved.batch.size,
    intervalMs: resolved.batch.intervalMs,
    maxRetries: resolved.batch.maxRetries,
    backoffBaseMs: resolved.batch.backoffBaseMs,
    sampleRate: resolved.sampleRate,
    transport: resolved.transport,
    buildRequest,
    onError: resolved.onError,
  });

  /**
   * 全局审计事件追踪器。
   *
   * <p>业务侧只需要表达“发生了什么”，上下文补齐、脱敏、中间件、JSON 安全化都在内核完成；
   * 出口直接落到行为通道，最终写入 {@code behavior_log}。</p>
   */
  const tracker = createAuditTracker({
    context: {
      sessionId: clientId,
      userId: resolveUserId(),
      route: readRoute(),
      release: resolved.release,
    },
    privacy: resolved.privacy,
    contextProvider: () => ({
      userId: resolveUserId(),
      route: readRoute(),
    }),
    sink: (event) => {
      behaviorChannel.push({
        eventType: event.eventType,
        eventName: event.eventName,
        target: event.target ? event.target.slice(0, 255) : undefined,
        payload:
          event.payload === undefined
            ? undefined
            : safeStringifyMasked(event.payload, resolved.privacy),
        timestamp: event.timestamp,
      });
    },
    onError: (error) => resolved.onError(error),
  });

  /** 提取首个 Error 的堆栈（已掩码）。 */
  const extractStack = (args: unknown[]): string | undefined => {
    for (const arg of args) {
      if (arg instanceof Error && arg.stack) {
        return maskText(arg.stack);
      }
    }
    return undefined;
  };

  /** 采集一条开发者日志（通道③）。 */
  const pushDeveloperLog = (level: LogLevel, args: unknown[]): void => {
    frontendChannel.push({
      logLevel: level,
      message: args.map(formatArg).join(' '),
      stack: extractStack(args),
      browser: readUserAgent() ?? undefined,
      timestamp: Date.now(),
    });
  };

  /** 采集一条用户行为（通道④）。 */
  const pushBehavior = (
    eventType: EventType,
    eventName: string,
    target: string | null,
    payload: unknown,
  ): void => {
    behaviorChannel.push({
      eventType,
      eventName,
      target: target ?? undefined,
      payload:
        payload === undefined || payload === null
          ? undefined
          : safeStringifyMasked(payload, resolved.privacy),
      timestamp: Date.now(),
    });
  };

  let consoleHandle: ConsoleCaptureHandle | null = null;
  if (resolved.console.enabled) {
    consoleHandle = installConsoleCapture({
      levels: resolved.console.levels,
      collect: pushDeveloperLog,
    });
  }

  const behaviorHandle: BehaviorCaptureHandle = installBehaviorCapture({
    autoPageView: resolved.behavior.autoPageView,
    autoClick: resolved.behavior.autoClick,
    autoSubmit: resolved.behavior.autoSubmit,
    trackAttr: resolved.behavior.trackAttr,
    emit: pushBehavior,
  });

  const sdk: LogSdk = {
    track: (eventName: string, payload?: unknown, eventType: EventType = 'CUSTOM') => {
      tracker.track(eventName, payload, { eventType });
    },
    log: (level: LogLevel, ...args: unknown[]) => {
      pushDeveloperLog(level, args);
    },
    flush: async () => {
      await Promise.all([frontendChannel.flush(), behaviorChannel.flush()]);
    },
    destroy: () => {
      consoleHandle?.restore();
      consoleHandle = null;
      behaviorHandle.destroy();
      frontendChannel.destroy();
      behaviorChannel.destroy();
      clearGlobalLogSdk(sdk);
    },
    config: resolved,
    tracker,
  };

  if (resolved.singleton) {
    storeGlobalLogSdk(sdk);
  }

  return sdk;
}
