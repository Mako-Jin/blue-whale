/**
 * `@bluewhale/whale-log-sdk` barrel（Wave 5.14，REQ-SEC-006 通道③④）。
 *
 * <p>公开 API：{@link initLogSdk} 与 {@link LogSdk}；同时导出纯函数工具
 * （掩码 / 分块 / 采样 / 队列）供高级用法与无框架冒烟脚本断言。</p>
 */

export { initLogSdk } from './sdk';
export { LogChannel, chunkItems, itemKey, shouldSample } from './batch';
export type { ChannelConfig } from './batch';
export { maskSensitive, maskText, maskUrl } from './mask';
export type { MaskOptions } from './mask';
export { installConsoleCapture, originalConsole } from './console';
export type { ConsoleCaptureHandle, ConsoleCaptureOptions } from './console';
export { installBehaviorCapture } from './behavior';
export type { BehaviorCaptureHandle, BehaviorCaptureOptions, BehaviorEmit } from './behavior';
export { createFetchTransport } from './client';
export * from './constants';
export type {
  BatchOptions,
  BehaviorOptions,
  ConsoleOptions,
  EventType,
  LogBatchRequest,
  LogItem,
  LogLevel,
  LogSdk,
  LogSdkConfig,
  PrivacyOptions,
  ResolvedLogSdkConfig,
  Transport,
} from './types';
