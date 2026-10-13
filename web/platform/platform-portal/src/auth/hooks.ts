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

/** 引用稳定的空集合：会话里没有权限时用它，避免每次渲染都造一个新数组 */
const NO_PERMISSIONS: readonly string[] = [];

/**
 * 当前身份的权限集合。
 *
 * <p><b>空数组的含义是"确实没有权限"，不是"还不知道"</b> ——
 * 本 Hook 的消费点（`BasicLayout` / `RequireMenu` / `Home`）都在 `RequireAuth` 之内，
 * 而后者已经等到会话恢复阶段结束才放行。这个前提要是没了，
 * 空数组就会在刷新的一瞬间把导航栏清空。
 *
 * <p>权限集合由基座单点计算（`session.user.permissions` 来自登录或刷新接口），
 * 子应用不得自调 `/me`，因此这里就是全应用唯一的那一份（ADR-0018 第 3 条）。
 */
export function usePermissions(): readonly string[] {
  const { user } = useSession();
  return user?.permissions ?? NO_PERMISSIONS;
}
