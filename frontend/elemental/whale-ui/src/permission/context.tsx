// 权限上下文 - 由宿主应用（如 admin）在登录后注入当前用户的权限码集合
// 供 AuthButton / usePermission 做「按钮级权限渲染」

import { createContext, useCallback, useContext, type ReactNode } from 'react';

const PermissionContext = createContext<string[]>([]);

export interface PermissionProviderProps {
  /** 当前登录用户拥有的权限码集合（来自 GET /auth/me） */
  permissions: string[];
  children: ReactNode;
}

/** 注入权限码集合；未包裹时默认空集合（全部判无权限） */
export function PermissionProvider({ permissions, children }: PermissionProviderProps) {
  return <PermissionContext.Provider value={permissions}>{children}</PermissionContext.Provider>;
}

export interface PermissionUtils {
  permissions: string[];
  /** 判断是否拥有单个权限码；不传 code 视为通过 */
  has: (code?: string) => boolean;
  /** 判断是否拥有其中任意一个权限码 */
  hasAny: (...codes: string[]) => boolean;
}

/** 读取当前权限上下文 */
// eslint-disable-next-line react-refresh/only-export-components -- Provider 与 hook 同文件导出，保持包入口聚合
export function usePermission(): PermissionUtils {
  const permissions = useContext(PermissionContext);

  const has = useCallback((code?: string) => !code || permissions.includes(code), [permissions]);

  const hasAny = useCallback(
    (...codes: string[]) => codes.some((code) => permissions.includes(code)),
    [permissions],
  );

  return { permissions, has, hasAny };
}
