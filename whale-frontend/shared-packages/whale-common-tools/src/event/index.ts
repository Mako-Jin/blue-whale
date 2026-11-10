/**
 * 全局事件总线。
 *
 * <p>提供应用内事件通信、跨标签页广播、优先级、拦截器与全局订阅；
 * 本身不依赖任何后端。审计落库通过 `subscribeAll` 在接入层挂载，
 * 由 `@bluewhale/whale-log-sdk` 的 `initAuditLogging` 完成。</p>
 */

export { AppEvents, EventPriority } from './types';
export type {
  BroadcastMessage,
  EmitOptions,
  EventHandler,
  EventInterceptor,
  EventMeta,
  GlobalEventListener,
} from './types';

export { UnifiedEventBus, eventBus } from './emitter';
export { eventBus as default } from './emitter';
