/**
 * 前端日志 SDK 类型定义（Wave 5.14，REQ-SEC-006 通道③④）。
 *
 * <p>上报体字段名严格消费 Wave 5.10 后端冻结契约（{@code LogBatchRequest} / {@code LogItem}），
 * 不得改名；{@code items} 上限 50，SDK 侧自动分片。</p>
 */

import type { AuditTracker } from './tracker';

/** console / 开发者日志级别（与后端 {@code frontend_log.log_level} 取值一致）。 */
export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

/** 用户行为事件类型（与后端 {@code behavior_log.event_type} 取值一致）。 */
export type EventType = 'PAGE_VIEW' | 'CLICK' | 'SUBMIT' | 'CUSTOM';

/** 单条日志条目（通道③用日志字段，通道④用行为字段）。 */
export interface LogItem {
  /** 开发者日志级别（通道③） */
  logLevel?: LogLevel;
  /** 日志文本（通道③） */
  message?: string;
  /** 错误堆栈（通道③，仅 ERROR） */
  stack?: string;
  /** 浏览器 UA（通道③） */
  browser?: string;
  /** 行为事件类型（通道④） */
  eventType?: EventType;
  /** 行为事件名（通道④） */
  eventName?: string;
  /** 行为目标（通道④，选择器或元素定位串） */
  target?: string;
  /** 行为附加数据（通道④，已掩码的 JSON 字符串） */
  payload?: string;
  /** 事件时间戳（毫秒） */
  timestamp: number;
}

/** 批量上报请求体（与后端 {@code LogBatchRequest} 字段名一致）。 */
export interface LogBatchRequest {
  /** 追踪ID（与网关/后端 X-Trace-Id 对齐） */
  traceId: string | null;
  /** 用户ID（后端以网关注入的 X-User-Id 为准，此处仅兜底） */
  userId: number | null;
  /** 浏览器会话ID */
  clientId: string | null;
  /** 当前页面地址（query 已掩码） */
  pageUrl: string | null;
  /** 条目列表（上限 50） */
  items: LogItem[];
}

/** console 劫持配置。 */
export interface ConsoleOptions {
  /** 是否劫持 console（默认 true） */
  enabled?: boolean;
  /** 采集的级别集合（默认全部四级） */
  levels?: LogLevel[];
}

/** 自动行为埋点配置。 */
export interface BehaviorOptions {
  /** 是否自动采集 PAGE_VIEW（默认 true） */
  autoPageView?: boolean;
  /** 是否自动采集 CLICK（默认 true） */
  autoClick?: boolean;
  /** 是否自动采集 SUBMIT（默认 true） */
  autoSubmit?: boolean;
  /** 点击埋点选择器属性名（默认 data-track） */
  trackAttr?: string;
}

/** 批量与重试配置。 */
export interface BatchOptions {
  /** 触发 flush 的队列长度阈值（默认 20） */
  size?: number;
  /** 触发 flush 的时间间隔（毫秒，默认 5000） */
  intervalMs?: number;
  /** 最大额外重试次数（默认 2） */
  maxRetries?: number;
  /** 指数退避基数（毫秒，默认 1000） */
  backoffBaseMs?: number;
}

/** 隐私掩码配置。 */
export interface PrivacyOptions {
  /** 附加敏感键名（与内置规则并集，忽略大小写并去掉 _ / -） */
  sensitiveKeys?: string[];
}

/** 上报传输函数：返回 true 表示投递成功，false/抛异常表示需要重试。 */
export type Transport = (url: string, body: LogBatchRequest) => Promise<boolean>;

/** SDK 初始化配置（全部可选，均有默认值）。 */
export interface LogSdkConfig {
  /** 总开关（默认 true） */
  enabled?: boolean;
  /**
   * 是否复用全局单例（默认 true）。
   *
   * <p>宿主与 qiankun 子应用会各自加载一份 SDK；开启单例可避免 console 被重复劫持、
   * 页面浏览被重复采集。单元测试或需要独立实例时传 false。</p>
   */
  singleton?: boolean;
  /** 应用版本 / 构建号，随审计事件上报 */
  release?: string;
  /** 上报基地址（默认空串，即同源相对路径） */
  baseUrl?: string;
  /** 开发者日志上报端点（默认 /audit/v1/log/frontend/batch） */
  frontendEndpoint?: string;
  /** 用户行为上报端点（默认 /audit/v1/log/behavior/batch） */
  behaviorEndpoint?: string;
  /** console 劫持配置 */
  console?: ConsoleOptions;
  /** 自动行为埋点配置 */
  behavior?: BehaviorOptions;
  /** 批量与重试配置 */
  batch?: BatchOptions;
  /** 采样率（[0,1]，默认 1） */
  sampleRate?: number;
  /** 隐私掩码配置 */
  privacy?: PrivacyOptions;
  /** 用户ID解析钩子（默认从本地登录态读取） */
  resolveUserId?: () => number | null;
  /** 自定义传输（默认 fetch 实现，注入便于测试） */
  transport?: Transport;
  /** SDK 内部异常回调（默认静默，绝不冒泡到业务） */
  onError?: (error: unknown) => void;
}

/** 已解析（补全默认值）的 SDK 配置。 */
export interface ResolvedLogSdkConfig {
  /** 总开关 */
  enabled: boolean;
  /** 是否复用全局单例 */
  singleton: boolean;
  /** 应用版本 / 构建号 */
  release: string | undefined;
  /** 上报基地址 */
  baseUrl: string;
  /** 开发者日志上报端点 */
  frontendEndpoint: string;
  /** 用户行为上报端点 */
  behaviorEndpoint: string;
  /** console 劫持配置（已补全默认值） */
  console: Required<ConsoleOptions>;
  /** 自动行为埋点配置（已补全默认值） */
  behavior: Required<BehaviorOptions>;
  /** 批量与重试配置（已补全默认值） */
  batch: Required<BatchOptions>;
  /** 采样率 */
  sampleRate: number;
  /** 隐私掩码配置 */
  privacy: PrivacyOptions;
  /** 用户ID解析钩子 */
  resolveUserId: () => number | null;
  /** 上报传输 */
  transport: Transport;
  /** 内部异常回调 */
  onError: (error: unknown) => void;
}

/** SDK 实例（{@link initLogSdk} 的返回类型）。 */
export interface LogSdk {
  /** 手动上报用户行为（默认事件类型 CUSTOM） */
  track: (eventName: string, payload?: unknown, eventType?: EventType) => void;
  /** 手动上报开发者日志 */
  log: (level: LogLevel, ...args: unknown[]) => void;
  /** 立即 flush 两个通道队列 */
  flush: () => Promise<void>;
  /** 销毁：还原 console、移除监听、清空定时器与队列 */
  destroy: () => void;
  /** 生效的配置快照 */
  readonly config: ResolvedLogSdkConfig;
  /** 全局审计事件追踪器（通道④，落库 behavior_log） */
  readonly tracker: AuditTracker;
}
