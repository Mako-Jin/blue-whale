import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { auth } from '@/auth';
import { usePermissions, useSession } from '@/auth/hooks';
import { MICRO_APPS, setGlobalState } from '@/micro';
import './BasicLayout.less';

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'basic-layout__link basic-layout__link--active' : 'basic-layout__link';
}

export default function BasicLayout() {
  const { pathname } = useLocation();
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const { user } = useSession();
  const permissions = usePermissions();

  /*
   * 导航栏只渲染**当前身份持有菜单码**的那些应用。
   *
   * 与 `RequireMenu` 读的是同一个字段，因此"看不见"与"进不去"永远一致 ——
   * 只过滤导航栏会让地址栏成为后门，那是假开关。
   *
   * 概览（`/`）不参与过滤：它不承载任何数据，且是"被弹回来"时的落点，
   * 必须对所有已登录身份可用。
   */
  const visibleApps = MICRO_APPS.filter((app) => permissions.includes(app.menuPermission));

  // 子应用页面不需要外边距，让它铺满内容区 —— 否则会看到一层不属于子应用的留白，
  // 排查布局问题时容易误判成子应用的样式出错。
  //
  // 这里用**完整**的 `MICRO_APPS` 而不是 `visibleApps`：它回答的是"这条路径属不属于
  // 某个子应用"（布局问题），不是"能不能进去"。没有菜单码的路径根本渲染不到这里 ——
  // `RequireMenu` 已经把它重定向走了。
  const isMicroApp = MICRO_APPS.some((app) => pathname.startsWith(app.routePath));

  const switchTheme = () => {
    const next = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    setGlobalState({ theme: next });
  };

  const signOut = (): void => {
    // 不手动跳转：会话被清空后 `RequireAuth` 会把页面重定向到登录页
    void auth.logout();
  };

  return (
    <div className="basic-layout">
      <header className="basic-layout__header">
        <span className="basic-layout__brand">BlueWhale</span>
        <span className="basic-layout__subtitle">平台门户</span>

        <nav className="basic-layout__nav" data-testid="portal-nav">
          <NavLink to="/" end className={navClass}>
            概览
          </NavLink>
          {visibleApps.map((app) => (
            <NavLink
              key={app.name}
              to={app.routePath}
              className={navClass}
              data-testid={`nav-${app.name}`}
            >
              {app.title}
            </NavLink>
          ))}
        </nav>

        {/*
          身份由真实会话驱动（`session.user`），不再是本地 useState 的调试值。
          `userId` / `roles` / `permissions` 的下发在 `src/micro/session-sync.ts` 里 ——
          那里是模块级订阅，登录页（不渲染本布局）期间同样在同步。
        */}
        <div className="basic-layout__actions">
          <button type="button" className="basic-layout__action" onClick={switchTheme}>
            全局主题：{theme === 'light' ? '浅色' : '深色'}
          </button>
          <span className="basic-layout__user">
            {user?.displayName || user?.username || '（未知身份）'}
          </span>
          <button type="button" className="basic-layout__action" onClick={signOut}>
            登出
          </button>
        </div>
      </header>

      <main
        className={
          isMicroApp ? 'basic-layout__main basic-layout__main--flush' : 'basic-layout__main'
        }
      >
        <Outlet />
      </main>
    </div>
  );
}
