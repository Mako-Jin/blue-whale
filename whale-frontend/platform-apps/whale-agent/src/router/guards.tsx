// 路由守卫 - 登录态校验

import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { getToken } from '../api/token';

// 未登录访问受保护页面时重定向到 /login
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();

  if (!getToken()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

// 已登录访问 /login 时重定向到首页
export function GuestOnly({ children }: { children: ReactNode }) {
  if (getToken()) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
