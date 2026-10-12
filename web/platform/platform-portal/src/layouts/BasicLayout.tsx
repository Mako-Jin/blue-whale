import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { auth } from '@/auth';
import { useSession } from '@/auth/hooks';
import { MICRO_APPS, setGlobalState } from '@/micro';
import './BasicLayout.less';

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'basic-layout__link basic-layout__link--active' : 'basic-layout__link';
}

export default function BasicLayout() {
  const { pathname } = useLocation();
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const { user } = useSession();

  // 子应用页面不需要外边距，让它铺满内容区 —— 否则会看到一层不属于子应用的留白，
  // 排查布局问题时容易误判成子应用的样式出错。
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

        <nav className="basic-layout__nav">
          <NavLink to="/" end className={navClass}>
            概览
          </NavLink>
          {MICRO_APPS.map((app) => (
            <NavLink key={app.name} to={app.routePath} className={navClass}>
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
