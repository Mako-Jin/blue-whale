import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { usePermissions } from '@/auth/hooks';
import type { MenuPermission } from 'common-api';

/**
 * 菜单守卫 —— 拦住**直接输 URL** 进一个被过滤掉的应用。
 *
 * <h3>为什么只过滤导航栏是不够的</h3>
 *
 * 导航栏过滤之后，看不见的入口仍然可以被敲地址栏进去 ——
 * 那样"看不见"只是"没摆在明面上"，是一个**假开关**。
 * 本仓明确不交付假开关：要么这个入口对该身份真的不可达，
 * 要么它就该出现在导航栏上（那时说明菜单码配错了）。
 * 因此导航栏与本组件**读同一个字段**（`MicroAppMeta.menuPermission`），
 * 两者不可能各说各话。
 *
 * <h3>为什么重定向到概览而不是渲染一个 403 页</h3>
 *
 * 概览**不纳管权限**（任何已登录身份都看得到），因此它永远是一个可用的落点；
 * 而"403 页"要多一个页面、并且它在概念上是错位的 —— 这里并没有发生一次拒绝，
 * 只是这个入口对该身份不存在。带上 `denied` 状态让概览页能解释一句
 * "为什么我被弹回来了"，避免表现成"点了没反应"。
 *
 * <h3>它不是安全边界</h3>
 *
 * 子应用里的接口仍然由服务端的 `@RequiresPermission` 判定 ——
 * 这里少拦一次不会漏权限（最坏是看到一个加载完就报错的页面），
 * 但会让人误以为"看不见"等于"做不到"。两者的层次见 `common-api` 的 `menus.ts`。
 */
export default function RequireMenu({
  permission,
  children,
}: {
  permission: MenuPermission;
  children: ReactNode;
}) {
  const permissions = usePermissions();
  const location = useLocation();

  if (permissions.includes(permission)) {
    return <>{children}</>;
  }

  return <Navigate to="/" replace state={{ denied: { path: location.pathname, permission } }} />;
}
