import { createLogger } from '../logger';
import {
  EventPriority,
  type BroadcastMessage,
  type EmitOptions,
  type EventHandler,
  type EventInterceptor,
  type EventMeta,
  type GlobalEventListener,
} from './types';

/** 模块级 logger；命名空间区分事件总线自身 */
const logger = createLogger('EventBus');

/** BroadcastChannel 默认频道名 */
const DEFAULT_CHANNEL_NAME = 'bluewhale-platform-events';

/** 单实例标识：跨标签页广播时用于过滤自回环 */
function createInstanceId(): string {
  const random = Math.random().toString(36).slice(2, 10);
  return `bus-${Date.now().toString(36)}-${random}`;
}

/** 当前 origin（非浏览器环境回落 unknown）。 */
function currentOrigin(): string {
  const location = (globalThis as { location?: { origin?: string } }).location;
  return location?.origin ?? 'unknown';
}

/** 带优先级的事件处理器。 */
interface PrioritizedHandler {
  handler: EventHandler;
  priority: EventPriority;
}

const PRIORITY_ORDER: Record<EventPriority, number> = {
  high: 0,
  normal: 1,
  low: 2,
};

/**
 * 全局事件总线。
 *
 * <p>职责边界：本类只负责“事件在应用内流转与跨标签页广播”，
 * 不负责“事件如何落库”。审计落库由 {@code initAuditLogging} 通过
 * {@link UnifiedEventBus.subscribeAll} 挂载，从而保持总线对具体后端零依赖。</p>
 *
 * <p>异常策略：监听器、拦截器、广播链路上的异常一律被吞掉并记录，
 * 绝不把埋点故障抛回业务代码。</p>
 */
export class UnifiedEventBus {
  private static instance: UnifiedEventBus | undefined;

  private readonly instanceId = createInstanceId();

  private readonly eventMap = new Map<string, Set<PrioritizedHandler>>();

  private readonly globalListeners = new Set<GlobalEventListener>();

  private readonly interceptors: EventInterceptor[] = [];

  private broadcastChannel: BroadcastChannel | null = null;

  private destroyed = false;

  private readonly channelName: string;

  private constructor(channelName: string) {
    this.channelName = channelName;
  }

  /** 获取全局单例。 */
  static getInstance(channelName: string = DEFAULT_CHANNEL_NAME): UnifiedEventBus {
    if (!UnifiedEventBus.instance) {
      UnifiedEventBus.instance = new UnifiedEventBus(channelName);
    }
    return UnifiedEventBus.instance;
  }

  // ==================== 发送 ====================

  /**
   * 发布事件。
   *
   * @param type 事件名，支持 `namespace:event` 或旧格式 `event.namespace`
   * @param data 事件载荷
   * @param options 跨标签页 / 来源 / 审计开关
   */
  emit<T = unknown>(type: string, data?: T, options: EmitOptions = {}): void {
    if (this.destroyed) {
      logger.warn(`事件总线已销毁，忽略事件：${type}`);
      return;
    }

    const meta: EventMeta = {
      source: options.source ?? currentOrigin(),
      timestamp: Date.now(),
      isCrossTab: false,
      audit: options.audit,
      instanceId: this.instanceId,
    };

    this.triggerLocal(type, data, meta);

    if (options.crossTab) {
      this.ensureBroadcastChannel();
      this.broadcastChannel?.postMessage({
        type: this.normalizeEventName(type),
        data,
        source: meta.source,
        timestamp: meta.timestamp,
        audit: meta.audit,
        instanceId: this.instanceId,
      } satisfies BroadcastMessage);
    }
  }

  // ==================== 订阅 ====================

  /** 注册事件处理器（默认优先级）。返回取消订阅函数。 */
  on<T = unknown>(type: string, handler: EventHandler<T>): () => void {
    return this.onWithPriority(type, handler as EventHandler, EventPriority.NORMAL);
  }

  /** 注册带优先级的处理器。 */
  onWithPriority(
    type: string,
    handler: EventHandler,
    priority: EventPriority = EventPriority.NORMAL,
  ): () => void {
    if (this.destroyed) {
      logger.warn(`事件总线已销毁，忽略监听注册：${type}`);
      return () => undefined;
    }

    const normalized = this.normalizeEventName(type);
    const set = this.eventMap.get(normalized) ?? new Set<PrioritizedHandler>();
    const entry: PrioritizedHandler = { handler, priority };
    set.add(entry);
    this.eventMap.set(normalized, set);

    return () => {
      set.delete(entry);
      if (set.size === 0) this.eventMap.delete(normalized);
    };
  }

  /** 一次性监听。 */
  once<T = unknown>(type: string, handler: EventHandler<T>): () => void {
    let unsubscribe: () => void = () => undefined;
    const wrapper: EventHandler<T> = (data, meta) => {
      unsubscribe();
      handler(data, meta);
    };
    unsubscribe = this.on(type, wrapper);
    return unsubscribe;
  }

  /** 移除指定处理器。 */
  off(type: string, handler: EventHandler): void {
    const normalized = this.normalizeEventName(type);
    const set = this.eventMap.get(normalized);
    if (!set) return;
    for (const entry of Array.from(set)) {
      if (entry.handler === handler) set.delete(entry);
    }
    if (set.size === 0) this.eventMap.delete(normalized);
  }

  /**
   * 全局订阅：不关心事件名，接收所有本地事件。
   *
   * <p>审计落库使用该入口；跨标签页副本会带 `isCrossTab=true`，
   * 接入方应跳过以避免同一事件在多个标签页重复落库。</p>
   */
  subscribeAll(listener: GlobalEventListener): () => void {
    this.globalListeners.add(listener);
    return () => {
      this.globalListeners.delete(listener);
    };
  }

  /** 追加拦截器。返回移除函数。 */
  useInterceptor(interceptor: EventInterceptor): () => void {
    this.interceptors.push(interceptor);
    return () => {
      const index = this.interceptors.indexOf(interceptor);
      if (index >= 0) this.interceptors.splice(index, 1);
    };
  }

  // ==================== 查询与清理 ====================

  /** 某事件的监听器数量。 */
  listenerCount(type: string): number {
    return this.eventMap.get(this.normalizeEventName(type))?.size ?? 0;
  }

  /** 已注册的事件名列表。 */
  getRegisteredEvents(): string[] {
    return Array.from(this.eventMap.keys());
  }

  /** 清空某事件的全部监听器。 */
  clearEvent(type: string): void {
    this.eventMap.delete(this.normalizeEventName(type));
  }

  /** 移除全部监听器；传 type 时只移除该事件。 */
  removeAllListeners(type?: string): void {
    if (type) {
      this.clearEvent(type);
      return;
    }
    this.eventMap.clear();
    this.globalListeners.clear();
  }

  /** 销毁总线：关闭广播、清空监听与拦截器。 */
  destroy(): void {
    this.destroyed = true;
    this.broadcastChannel?.close();
    this.broadcastChannel = null;
    this.eventMap.clear();
    this.globalListeners.clear();
    this.interceptors.length = 0;
  }

  /** 复位单例（测试与热更新场景使用）。 */
  reset(): void {
    this.destroy();
    UnifiedEventBus.instance = new UnifiedEventBus(this.channelName);
  }

  // ==================== 内部实现 ====================

  /**
   * 标准化事件名。
   *
   * <p>`namespace:event` 为推荐格式；旧格式 `event.namespace` 自动反转，
   * 保持与历史代码兼容。</p>
   */
  private normalizeEventName(name: string): string {
    if (name.includes('.') && !name.includes(':')) {
      const [event, namespace] = name.split('.');
      return `${namespace || 'base'}:${event || name}`;
    }
    return name;
  }

  private triggerLocal(type: string, data: unknown, meta: EventMeta): void {
    const normalized = this.normalizeEventName(type);

    if (!this.runInterceptors(normalized, data, meta, 'before')) {
      logger.debug(`事件被拦截器阻止：${normalized}`);
      return;
    }

    const handlers = this.eventMap.get(normalized);
    if (handlers) {
      for (const entry of this.sortHandlers(Array.from(handlers))) {
        try {
          entry.handler(data, meta);
        } catch (error) {
          logger.error(`事件处理器异常：${normalized}`, error);
        }
      }
    }

    for (const listener of this.globalListeners) {
      try {
        listener(normalized, data, meta);
      } catch (error) {
        logger.error(`全局事件订阅者异常：${normalized}`, error);
      }
    }

    this.dispatchDomEvent(normalized, data, meta);
    this.runInterceptors(normalized, data, meta, 'after');
  }

  private runInterceptors(
    type: string,
    data: unknown,
    meta: EventMeta,
    phase: 'before' | 'after',
  ): boolean {
    for (const interceptor of this.interceptors) {
      try {
        if (phase === 'before' && interceptor.before) {
          if (interceptor.before(type, data, meta) === false) return false;
        } else {
          interceptor.after?.(type, data, meta);
        }
      } catch (error) {
        logger.error(`事件拦截器异常：${type}`, error);
      }
    }
    return true;
  }

  private sortHandlers(handlers: PrioritizedHandler[]): PrioritizedHandler[] {
    return handlers.sort(
      (a, b) => PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority],
    );
  }

  /** 同步派发 DOM CustomEvent，便于非本包代码通过原生事件监听。 */
  private dispatchDomEvent(type: string, data: unknown, meta: EventMeta): void {
    const target = globalThis as {
      dispatchEvent?: (event: unknown) => boolean;
      CustomEvent?: new (type: string, init: { detail: unknown; bubbles: boolean; composed: boolean }) => unknown;
    };
    if (typeof target.dispatchEvent !== 'function' || !target.CustomEvent) return;

    try {
      target.dispatchEvent(
        new target.CustomEvent(type, {
          detail: { data, meta },
          bubbles: true,
          composed: true,
        }),
      );
    } catch (error) {
      logger.warn(`派发 DOM 事件失败：${type}`, error);
    }
  }

  private ensureBroadcastChannel(): void {
    if (this.broadcastChannel || this.destroyed) return;
    if (typeof BroadcastChannel === 'undefined') return;

    try {
      this.broadcastChannel = new BroadcastChannel(this.channelName);
      this.broadcastChannel.onmessage = (event: MessageEvent<BroadcastMessage>) => {
        const message = event.data;
        // BroadcastChannel 不会回投给同一实例，但不同构建副本可能共用频道；
        // 用 instanceId 兜住自回环，避免同一事件被重复处理
        if (message.instanceId === this.instanceId) return;
        this.triggerLocal(message.type, message.data, {
          source: message.source,
          timestamp: message.timestamp,
          isCrossTab: true,
          audit: message.audit,
          instanceId: message.instanceId,
        });
      };
    } catch (error) {
      logger.warn('BroadcastChannel 初始化失败', error);
      this.broadcastChannel = null;
    }
  }
}

/** 全局事件总线单例。 */
export const eventBus = UnifiedEventBus.getInstance();

export { AppEvents } from './types';
export type {
  BroadcastMessage,
  EmitOptions,
  EventHandler,
  EventInterceptor,
  EventMeta,
  GlobalEventListener,
} from './types';
