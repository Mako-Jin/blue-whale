// AuthButton - 按钮级权限渲染：无权限时渲染 fallback（默认不渲染）

import { Button, type ButtonProps } from 'antd';
import type { ReactNode } from 'react';
import { usePermission } from './context';

export interface AuthButtonProps extends ButtonProps {
  /** 所需权限码，缺失则不渲染 */
  perm?: string;
  /** 满足任意一个权限码即可渲染 */
  anyOf?: string[];
  /** 无权限时的替代渲染，默认 null（隐藏） */
  fallback?: ReactNode;
}

export function AuthButton({ perm, anyOf, fallback = null, ...rest }: AuthButtonProps) {
  const { has, hasAny } = usePermission();

  const allowed = (perm ? has(perm) : true) && (anyOf && anyOf.length > 0 ? hasAny(...anyOf) : true);

  if (!allowed) {
    return <>{fallback}</>;
  }
  return <Button {...rest} />;
}
