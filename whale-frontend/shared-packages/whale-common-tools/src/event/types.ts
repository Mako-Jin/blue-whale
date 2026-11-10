/** 事件优先级。数字越小越先执行。 */
export const EventPriority = {
  HIGH: 'high',
  NORMAL: 'normal',
  LOW: 'low',
} as const;

export type EventPriority = (typeof EventPriority)[keyof typeof EventPriority];

/** 事件元数据。 */
export interface EventMeta {
  /** 事件来源（默认当前 origin） */
  source: string;
  /** 毫秒时间戳 */
  timestamp: number;
  /** 是否来自跨标签页广播 */
  isCrossTab?: boolean;
  /**
   * 是否写入审计日志。
   *
   * <p>`true` 强制写入、`false` 强制不写、`undefined` 交给接入方按事件名单决定。</p>
   */
  audit?: boolean;
  /** 发布方实例标识，用于过滤 BroadcastChannel 自回环 */
  instanceId?: string;
}

/** 事件处理器。 */
export type EventHandler<T = unknown> = (data: T, meta: EventMeta) => void;

/** 全局订阅者：不关心事件名，用于审计落库等横切消费。 */
export type GlobalEventListener = (type: string, data: unknown, meta: EventMeta) => void;

/** 发送选项。 */
export interface EmitOptions {
  /** 是否同步广播到其他标签页（默认 false） */
  crossTab?: boolean;
  /** 事件来源标识 */
  source?: string;
  /** 是否写入审计日志；不传则由接入方的名单决定 */
  audit?: boolean;
}

/** 事件拦截器。 */
export interface EventInterceptor {
  before?: (type: string, data: unknown, meta: EventMeta) => boolean | void;
  after?: (type: string, data: unknown, meta: EventMeta) => void;
}

/**
 * 应用内预定义事件。
 *
 * <p>保持 `namespace:event` 命名风格，便于跨应用（qiankun 子应用）统一识别。</p>
 */
export const AppEvents = {
  AUTH_LOGIN: 'auth:login',
  AUTH_LOGOUT: 'auth:logout',
  AUTH_TOKEN_REFRESH: 'auth:token:refresh',
  AUTH_PERMISSION_CHANGE: 'auth:permission:change',
  USER_INFO_UPDATE: 'user:info:update',
  USER_AVATAR_UPDATE: 'user:avatar:update',
  SYSTEM_THEME_CHANGE: 'system:theme:change',
  SYSTEM_LOCALE_CHANGE: 'system:locale:change',
  ROUTE_CHANGE: 'route:change',
  ROUTE_BEFORE_CHANGE: 'route:before:change',
  NOTIFICATION_SHOW: 'notification:show',
  NOTIFICATION_COUNT_UPDATE: 'notification:count:update',
  SUB_APP_LOADED: 'sub-app:loaded',
  SUB_APP_UNLOADED: 'sub-app:unloaded',
  SUB_APP_ERROR: 'sub-app:error',
} as const;

/** 跨标签页广播消息。 */
export interface BroadcastMessage {
  type: string;
  data: unknown;
  source: string;
  timestamp: number;
  audit?: boolean;
  instanceId: string;
}
