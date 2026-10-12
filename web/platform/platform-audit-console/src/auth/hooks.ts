import { useSyncExternalStore } from 'react';

import { getPermissionState, subscribePermissions } from './permissions';
import type { PermissionState } from './permissions';

/**
 * `useSyncExternalStore` 的适配层。
 *
 * <p>`subscribePermissions` 本身就是模块级的稳定引用，因此这里直接传它，
 * 不需要再包一层（基座那边要包一层，是因为它的订阅函数是内联箭头函数，
 * 每次渲染都会退订重订，恰好在两次渲染之间发生的会话变更会被漏掉）。
 */
export function usePermissionState(): PermissionState {
  return useSyncExternalStore(subscribePermissions, getPermissionState, getPermissionState);
}

/**
 * 是否拥有某个权限标识。
 *
 * <p>**未就绪时返回 `false`**：与其默认放行（漏权限）不如默认隐藏 ——
 * 隐藏多等一拍就会被下一次推送纠正，而放行一旦渲染出来就可能被用到。
 *
 * <p>注意本应用**只把这一个判断用在"看不看得到流水"上**，不用在演练区的按钮上：
 * 那边刻意不按权限显隐，因为"越权尝试"本身就是要被记录的事件。见 `audit/DrillPanel.tsx`。
 */
export function usePermission(code: string): boolean {
  const state = usePermissionState();
  return state.ready && state.permissions.includes(code);
}
