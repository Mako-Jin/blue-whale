import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { ensureSessionRestored } from '@/auth';
import { useRestorePhase, useSession } from '@/auth/hooks';
import './RequireAuth.less';

/**
 * 登录守卫。
 *
 * <h3>为什么是"声明式重定向"而不是"命令式跳转"</h3>
 *
 * 会话失效有两条触发路径（401 刷新失败、用户主动登出），若让它们各自调 `navigate('/login')`，
 * 就有两处命令式跳转要维护，且都可能与 render 阶段的路由匹配打架。
 * 这里只保留一条：**会话没了 → 本组件重定向**。触发路径只负责清会话。
 *
 * <h3>为什么必须先等恢复阶段结束</h3>
 *
 * 刷新页面时内存里没有令牌，但 localStorage 里可能还有刷新令牌 ——
 * 直接按"未登录"跳到登录页，会让每一次刷新都掉一次登录。所以先等 `restore()` 有结论。
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const phase = useRestorePhase();
  const { authenticated } = useSession();
  const location = useLocation();

  /*
   * 兜底：正常情况下 `main.tsx` 已经启动了恢复。加这一条是为了让"忘了启动"退化成
   * 一次正常的恢复，而不是永远停在"正在恢复会话"。
   */
  useEffect(() => {
    if (phase === 'idle') void ensureSessionRestored();
  }, [phase]);

  if (phase !== 'done') {
    return (
      <div className="require-auth">
        <p className="require-auth__text">正在恢复会话…</p>
      </div>
    );
  }

  if (!authenticated) {
    // 带上来源路径，登录成功后回到用户本来想去的页面
    return (
      <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
    );
  }

  return <>{children}</>;
}
