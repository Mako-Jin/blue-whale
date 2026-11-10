/**
 * 审计日志统一接入。
 *
 * <p>把两条前端事实接入后端审计落库：</p>
 * <ul>
 *   <li>结构化 Logger 记录（{@code onRecord}）→ 通道③ {@code frontend_log}；</li>
 *   <li>全局事件总线（{@link eventBus}）中的关键业务事件 → 通道④ {@code behavior_log}。</li>
 * </ul>
 *
 * <p>接入后业务侧只需要 `logger.info(...)` 与 `eventBus.emit('auth:login', ...)`，
 * 无需再关心上报、脱敏、批量与上下文补齐。</p>
 */

import {
  configureLogger,
  eventBus,
  getLoggerConfig,
  setLoggerSink,
  type LogLevel as LoggerLevel,
  type LogRecord,
  type LogSink,
} from '@bluewhale/whale-common-tools';

import { getOriginalConsole, type NativeConsoleMethod } from './console';
import { initLogSdk } from './sdk';
import type { AuditTracker } from './tracker';
import type { LogLevel, LogSdk, LogSdkConfig } from './types';

/**
 * 默认纳入审计的全局事件。
 *
 * <p>只收录“安全 / 合规上必须留痕”的事件；页面切换、通知刷新这类高频事件
 * 会淹没审计表，只由行为埋点通道采集。</p>
 */
export const DEFAULT_AUDIT_EVENTS: readonly string[] = [
  'auth:login',
  'auth:logout',
  'auth:token:refresh',
  'auth:permission:change',
  'sub-app:error',
];

/** 审计接入配置。 */
export interface AuditLoggingOptions extends LogSdkConfig {
  /** 需要落审计的事件名单，默认 {@link DEFAULT_AUDIT_EVENTS} */
  auditEvents?: readonly string[];
  /**
   * 事件名映射：`auth:login` → `auth.login`。
   *
   * <p>返回 {@code null} 表示该事件不落库（可在此做二次过滤）。</p>
   */
  mapEventName?: (type: string, data: unknown) => string | null;
  /** 是否接管结构化 Logger（默认 true） */
  logger?: boolean;
}

/** 审计接入句柄。 */
export interface AuditLoggingHandle {
  /** SDK 实例（含 {@code tracker}） */
  readonly sdk: LogSdk;
  /** 审计事件追踪器 */
  readonly tracker: AuditTracker;
  /** 取消事件订阅并还原 Logger 配置 */
  dispose: () => void;
}

/** Logger 级别 → 后端日志级别（{@code frontend_log.log_level}）。 */
const LEVEL_MAP: Record<LoggerLevel, LogLevel> = {
  trace: 'DEBUG',
  debug: 'DEBUG',
  perf: 'INFO',
  info: 'INFO',
  success: 'INFO',
  warn: 'WARN',
  error: 'ERROR',
};

/** 默认事件名映射：冒号转点号，与后端事件命名风格一致。 */
function defaultMapEventName(type: string): string {
  return type.replace(/:/g, '.');
}

/** 安全序列化（循环引用、函数等返回空串，交由 SDK 兜底）。 */
function safeStringify(value: unknown): string {
  try {
    const text = JSON.stringify(value);
    return text === undefined ? '' : text;
  } catch {
    return '';
  }
}

/** 把一条结构化记录折叠成可落库的文本。 */
function formatRecord(record: LogRecord): string {
  const parts: string[] = [];
  if (record.namespace) {
    parts.push(`[${record.namespace}]`);
  }
  parts.push(record.message);

  if (record.data.length > 0) {
    const dataText = record.data
      .map((item) => {
        if (item instanceof Error) {
          return `${item.name}: ${item.message}`;
        }
        return typeof item === 'string' ? item : safeStringify(item);
      })
      .filter((text) => text.length > 0)
      .join(' ');
    if (dataText) parts.push(dataText);
  }

  if (record.context && Object.keys(record.context).length > 0) {
    const contextText = safeStringify(record.context);
    if (contextText) parts.push(contextText);
  }

  return parts.join(' ');
}

/** 输出通道：始终写到劫持前的原生 console，避免被自身再次采集。 */
function createNativeSink(): LogSink {
  const native = getOriginalConsole();
  return (method, args) => {
    const fn = native[method as NativeConsoleMethod] ?? native.log;
    fn(...args);
  };
}

/** 当前生效的接入句柄（同一构建副本内幂等）。 */
let active: AuditLoggingHandle | undefined;

/**
 * 初始化审计日志接入。
 *
 * <p>幂等：重复调用返回同一句柄，避免 Logger 被反复接管、事件被重复订阅。</p>
 *
 * @param options 审计接入配置（兼容全部 {@link LogSdkConfig} 字段）
 * @returns 接入句柄
 */
export function initAuditLogging(options: AuditLoggingOptions = {}): AuditLoggingHandle {
  if (active) {
    return active;
  }

  const sdk = initLogSdk(options);
  const { tracker } = sdk;

  const previousLogger = getLoggerConfig();
  let disposed = false;

  if (options.logger !== false) {
    configureLogger({
      onRecord: (record: LogRecord) => {
        if (disposed) return;
        sdk.log(LEVEL_MAP[record.level], formatRecord(record));
      },
      sink: createNativeSink(),
    });
  }

  const auditEvents = new Set(options.auditEvents ?? DEFAULT_AUDIT_EVENTS);
  const mapEventName = options.mapEventName ?? defaultMapEventName;

  const unsubscribeEvents = eventBus.subscribeAll((type, data, meta) => {
    if (disposed) return;
    // 跨标签页副本跳过：同一事件应在产生它的标签页落库一次
    if (meta.isCrossTab) return;
    if (meta.audit === false) return;
    if (meta.audit !== true && !auditEvents.has(type)) return;

    let eventName: string | null;
    try {
      eventName = mapEventName(type, data);
    } catch {
      eventName = defaultMapEventName(type);
    }
    if (!eventName) return;

    tracker.track(
      eventName,
      {
        data,
        source: meta.source,
      },
      { eventType: 'CUSTOM' },
    );
  });

  const handle: AuditLoggingHandle = {
    sdk,
    tracker,
    dispose: () => {
      if (disposed) return;
      disposed = true;
      unsubscribeEvents();
      // 还原 Logger：onRecord 复位为上一版（无则空操作），sink 复位为上一版
      configureLogger({ onRecord: previousLogger.onRecord ?? (() => undefined) });
      setLoggerSink(previousLogger.sink);
      active = undefined;
    },
  };

  active = handle;
  return handle;
}

/** 读取当前生效的接入句柄（未接入时返回 undefined）。 */
export function getAuditLogging(): AuditLoggingHandle | undefined {
  return active;
}
