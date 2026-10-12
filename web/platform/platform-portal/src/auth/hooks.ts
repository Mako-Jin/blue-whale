import { useSyncExternalStore } from 'react';
import type { SessionSnapshot } from 'common-tools/session';

import { getRestorePhase, session, subscribeRestorePhase } from './index';
import type { RestorePhase } from './index';

/**
 * 会话订阅的适配层。
 *
 * `useSyncExternalStore` 在 `subscribe` **引用变化时会重新订阅**，所以这个包装必须是
 * 模块级常量：若写成内联箭头函数，每次渲染都会退订再订阅，会话在两次渲染之间变化时
 * 恰好落在空窗里，表现为"令牌刷新了但界面还显示旧用户"。
 *
 * 顺带把 `SessionListener` 的 `(snapshot, previous)` 收成 `useSyncExternalStore` 要的
 * 零参形态 —— 它只关心"变了"，新值由 `getSnapshot` 自己取。
 */
const subscribeSession = (onStoreChange: () => void): (() => void) =>
  session.subscribe(() => onStoreChange());

/** 订阅会话快照。快照引用稳定，可安全用于 `useSyncExternalStore` */
export function useSession(): SessionSnapshot {
  return useSyncExternalStore(subscribeSession, session.getSnapshot, session.getSnapshot);
}

/** 订阅启动时的会话恢复阶段 */
export function useRestorePhase(): RestorePhase {
  return useSyncExternalStore(subscribeRestorePhase, getRestorePhase, getRestorePhase);
}
