import { useSyncExternalStore } from 'react';

import { getPermissionState, subscribePermissions } from './permissions';
import type { PermissionState } from './permissions';

/**
 * `useSyncExternalStore` 的适配层。
 *
 * <p>`subscribePermissions` 本身就是模块级的稳定引用，因此这里直接传它，
 * 不需要再包一层（对比基座 `auth/hooks.ts` 里必须包一层的情形：
 * 那是因为订阅函数是内联箭头函数，每次渲染都会退订重订，
 * 恰好在两次渲染之间发生的会话变更会被漏掉）。
 */
export function usePermissionState(): PermissionState {
  return useSyncExternalStore(subscribePermissions, getPermissionState, getPermissionState);
}

/**
 * 是否拥有某个权限标识。
 *
 * <p>**未就绪时返回 `false`**：与其默认放行（漏权限）不如默认隐藏 ——
 * 隐藏多等一拍就会被下一次推送纠正，而放行一旦渲染出来就可能被点到。
 * "未就绪"与"确实没有"在界面上因此表现一致（都不显示），这是刻意的：
 * 二者的区别只对开发有意义的场景不需要暴露给用户。
 */
export function usePermission(code: string): boolean {
  const state = usePermissionState();
  return state.ready && state.permissions.includes(code);
}

/** 拥有其中之一即可（例如"任何写权限"） */
export function useAnyPermission(codes: readonly string[]): boolean {
  const state = usePermissionState();
  return state.ready && codes.some((code) => state.permissions.includes(code));
}
