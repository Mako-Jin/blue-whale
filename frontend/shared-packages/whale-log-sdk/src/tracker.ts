/**
 * 全局审计事件追踪器（通道④）。
 *
 * <p>语义化 API + 内部总线：业务代码只调用 {@link AuditTracker.track} 表达“发生了什么动作”，
 * 补齐上下文、脱敏、中间件、多订阅者与批量上报都是内核实现。</p>
 *
 * <p>异常策略：埋点失败不得打断业务，所有异常都送到 {@code onError}。</p>
 */

import { maskSensitive, type MaskOptions } from './mask';
import type { EventType } from './types';

/** 单条审计事件（已脱敏、可直接序列化）。 */
export interface AuditEvent {
  /** 事件唯一 id（幂等键） */
  eventId: string;
  /** 事件名，如 `auth.login`；后端 behavior_log.event_name 上限 128 字符 */
  eventName: string;
  /** 行为类型；审计事件默认 CUSTOM */
  eventType: EventType;
  /** 毫秒时间戳 */
  timestamp: number;
  /** 会话标识 */
  sessionId: string;
  /** 登录用户标识 */
  userId?: string | number | null;
  /** 当前路由 */
  route?: string;
  /** 应用版本 */
  release?: string;
  /** 触发目标（可选） */
  target?: string;
  /** 事件载荷（已脱敏、JSON 安全） */
  payload?: unknown;
}

/** 随每个事件自动附带的上下文。 */
export interface AuditContext {
  sessionId: string;
  userId?: string | number | null;
  route?: string;
  release?: string;
}

/** 单次 track 的可选项。 */
export interface AuditTrackOptions {
  /** 行为类型，默认 CUSTOM */
  eventType?: EventType;
  /** 触发目标（元素定位串等） */
  target?: string | null;
}

/** 事件出口：通常绑定到行为通道。 */
export type AuditSink = (event: AuditEvent) => void;

/** 事件中间件；返回 null 表示丢弃。 */
export type AuditMiddleware = (
  event: AuditEvent,
  next: (event: AuditEvent) => AuditEvent | null,
) => AuditEvent | null;

/** 事件订阅者。 */
export type AuditEventListener = (event: AuditEvent) => void;

/** 追踪器选项。 */
export interface AuditTrackerOptions {
  /** 初始上下文 */
  context: AuditContext;
  /** 事件出口 */
  sink: AuditSink;
  /** 隐私掩码附加键 */
  privacy?: MaskOptions;
  /** 上下文提供者，每次发布时求值（登录 / 路由变化后自动生效） */
  contextProvider?: () => Partial<AuditContext>;
  /** 事件名长度上限，默认 128（与后端列宽一致） */
  maxEventNameLength?: number;
  /** 异常出口 */
  onError?: (error: unknown, stage: string) => void;
}

/** 追踪器。 */
export interface AuditTracker {
  /** 发布一个事件；被中间件丢弃或异常时返回 null */
  track(eventName: string, payload?: unknown, options?: AuditTrackOptions): AuditEvent | null;
  /** 订阅事件，返回取消订阅函数 */
  subscribe(listener: AuditEventListener): () => void;
  /** 追加中间件，返回移除函数 */
  use(middleware: AuditMiddleware): () => void;
  /** 合并式更新上下文 */
  updateContext(patch: Partial<AuditContext>): void;
  /** 读取上下文快照 */
  getContext(): Readonly<AuditContext>;
  /** 清除登录身份，保留会话标识 */
  clearUser(): void;
}

/** 事件名长度上限默认值。 */
const DEFAULT_MAX_EVENT_NAME = 128;

/** 载荷递归深度上限。 */
const MAX_PAYLOAD_DEPTH = 8;

/** 进程中单调序号，保证同毫秒内 id 不撞。 */
let sequence = 0;

/** 生成事件 id。 */
function createEventId(): string {
  sequence += 1;
  const random = Math.random().toString(36).slice(2, 8);
  return `audit-${Date.now().toString(36)}-${sequence.toString(36)}-${random}`;
}

/**
 * 把任意值修剪为 JSON 安全形态。
 *
 * <p>函数、symbol、循环引用、DOM 节点在业务载荷里很常见；不修剪会让
 * `JSON.stringify` 抛错，一条脏数据可能拖垮整批上报。</p>
 */
function toJsonSafe(value: unknown, depth = 0, seen = new WeakSet<object>()): unknown {
  if (value === null || value === undefined) return value;
  if (depth > MAX_PAYLOAD_DEPTH) return '[MaxDepth]';

  if (value instanceof Error) {
    return { name: value.name, message: value.message, stack: value.stack };
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === 'function' || typeof value === 'symbol' || typeof value === 'bigint') {
    return String(value);
  }
  if (typeof value !== 'object') return value;

  if (seen.has(value)) return '[Circular]';
  seen.add(value);

  if (Array.isArray(value)) {
    return value.map((item) => toJsonSafe(item, depth + 1, seen));
  }

  const source = value as Record<string, unknown>;
  const result: Record<string, unknown> = {};
  for (const key of Object.keys(source)) {
    result[key] = toJsonSafe(source[key], depth + 1, seen);
  }
  return result;
}

/**
 * 创建审计事件追踪器。
 *
 * @param options 上下文、出口、脱敏规则与异常出口
 * @returns 追踪器实例
 */
export function createAuditTracker(options: AuditTrackerOptions): AuditTracker {
  const listeners = new Set<AuditEventListener>();
  const middlewares: AuditMiddleware[] = [];
  const maxEventNameLength = options.maxEventNameLength ?? DEFAULT_MAX_EVENT_NAME;

  let context: AuditContext = { ...options.context };

  const reportError = (error: unknown, stage: string): void => {
    try {
      options.onError?.(error, stage);
    } catch {
      // 异常出口自身再抛异常就没有出口了，只能咽下
    }
  };

  const resolveContext = (): AuditContext => {
    if (!options.contextProvider) return context;
    try {
      return { ...context, ...options.contextProvider() };
    } catch (error) {
      reportError(error, 'context');
      return context;
    }
  };

  /** 依次执行中间件；同一中间件重复调用 next 时保持幂等。 */
  const runMiddlewares = (event: AuditEvent): AuditEvent | null => {
    const dispatch = (index: number, current: AuditEvent): AuditEvent | null => {
      if (index >= middlewares.length) return current;
      const middleware = middlewares[index] as AuditMiddleware;
      let called = false;
      return middleware(current, (nextEvent) => {
        if (called) return current;
        called = true;
        return dispatch(index + 1, nextEvent ?? current);
      });
    };
    return dispatch(0, event);
  };

  const deliver = (event: AuditEvent): void => {
    for (const listener of listeners) {
      try {
        listener(event);
      } catch (error) {
        reportError(error, 'listener');
      }
    }
    try {
      options.sink(event);
    } catch (error) {
      reportError(error, 'sink');
    }
  };

  return {
    track(eventName: string, payload?: unknown, trackOptions?: AuditTrackOptions): AuditEvent | null {
      try {
        const name = typeof eventName === 'string' ? eventName.trim() : '';
        if (!name || name.length > maxEventNameLength) {
          reportError(
            new Error(`事件名不合法，已丢弃：${String(eventName)}`),
            'validate',
          );
          return null;
        }

        const ctx = resolveContext();
        const event: AuditEvent = {
          eventId: createEventId(),
          eventName: name,
          eventType: trackOptions?.eventType ?? 'CUSTOM',
          timestamp: Date.now(),
          sessionId: ctx.sessionId,
          userId: ctx.userId,
          route: ctx.route,
          release: ctx.release,
          target: trackOptions?.target ?? undefined,
          payload:
            payload === undefined || payload === null
              ? undefined
              : maskSensitive(toJsonSafe(payload), options.privacy),
        };

        const finalEvent = runMiddlewares(event);
        if (!finalEvent) return null;

        deliver(finalEvent);
        return finalEvent;
      } catch (error) {
        reportError(error, 'track');
        return null;
      }
    },

    subscribe(listener: AuditEventListener): () => void {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    use(middleware: AuditMiddleware): () => void {
      middlewares.push(middleware);
      return () => {
        const index = middlewares.indexOf(middleware);
        if (index >= 0) middlewares.splice(index, 1);
      };
    },

    updateContext(patch: Partial<AuditContext>): void {
      context = { ...context, ...patch };
    },

    getContext(): Readonly<AuditContext> {
      return { ...context };
    },

    clearUser(): void {
      const { userId: _dropped, ...rest } = context;
      context = rest;
    },
  };
}
