import type { ReactNode } from 'react';

import { usePermission } from '../auth/hooks';

/**
 * 按权限显隐的按钮 —— 本应用要演示的那件事。
 *
 * <h3>为什么是"不渲染"而不是"渲染成禁用"</h3>
 *
 * 禁用态能点开 DevTools 就知道这个功能存在，而权限的语义是"这个入口与你无关"；
 * 更重要的是：**显隐是二值的，可以被断言**。禁用态下"按钮在但不可点"与
 * "按钮该在却因为前端判错而变灰"在界面上几乎没有区别，
 * 而两种身份的差异正是这一块要验证的东西。
 *
 * <h3>未就绪时也不渲染</h3>
 *
 * `usePermission` 在权限集合可信之前一律返回 `false`（见 `auth/hooks.ts`）。
 * 于是"还没拿到权限"与"确实没有权限"表现一致：都不显示。
 * 这比"先显示再消失"好 —— 后者会闪一下，而且那一瞬间的按钮是可以点到的。
 *
 * <h3>它不是安全边界</h3>
 *
 * 服务端每个接口都带 `@RequiresPermission` 独立判定，前端的显隐只是**体验**。
 * 这一点必须写清楚：把按钮藏起来当权限控制，是这套东西最常见的误用。
 */
export function PermissionButton({
  permission,
  children,
  onClick,
  variant = 'plain',
  className,
  title,
}: {
  permission: string;
  children: ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'danger' | 'plain';
  className?: string;
  title?: string;
}) {
  const allowed = usePermission(permission);
  if (!allowed) return null;

  return (
    <button
      type="button"
      // 把判定依据写在 DOM 上：验证脚本据此断言"两种身份下按钮的差异"，
      // 也省得读代码时去猜这个按钮是靠哪个标识控制显隐的
      data-permission={permission}
      className={['btn', `btn--${variant}`, className].filter(Boolean).join(' ')}
      title={title}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
