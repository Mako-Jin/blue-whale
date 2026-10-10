import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { MICRO_APPS, setGlobalState } from '@/micro';
import './BasicLayout.less';

/** 演示用的身份，登录/登出都切这一个值。 */
const DEMO_USER_ID = 'u-cap-42';

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'basic-layout__link basic-layout__link--active' : 'basic-layout__link';
}

export default function BasicLayout() {
  const { pathname } = useLocation();
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const [userId, setUserId] = useState<string | null>(null);

  // 子应用页面不需要外边距，让它铺满内容区 —— 否则会看到一层不属于子应用的留白，
  // 排查布局问题时容易误判成子应用的样式出错。
  const isMicroApp = MICRO_APPS.some((app) => pathname.startsWith(app.routePath));

  const switchTheme = () => {
    const next = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    setGlobalState({ theme: next });
  };

  const toggleUser = () => {
    const next = userId ? null : DEMO_USER_ID;
    setUserId(next);
    setGlobalState({ userId: next });
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
          这两个按钮是**全局状态下发的调试开关**，不是产品功能：
          基座自己还没有主题体系与登录态，它们存在的意义是让子应用侧的
          "收到全局状态并响应"这段代码可被触发、可被观察。
        */}
        <div className="basic-layout__actions">
          <button type="button" className="basic-layout__action" onClick={switchTheme}>
            全局主题：{theme === 'light' ? '浅色' : '深色'}
          </button>
          <button type="button" className="basic-layout__action" onClick={toggleUser}>
            {userId ? `登出（${userId}）` : '登录'}
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
