import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { auth, session } from '@/auth';
import { usePermissions, useSession } from '@/auth/hooks';
import { ChangePasswordDialog } from '@/components/ChangePasswordDialog';
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
   * 「待改口令」的引导 —— 开局就打开，而且是**挂载那一刻**的事实决定要不要开。
   *
   * 入口放在基座顶栏，与登出同一处。判据是**改密和登出都是会话级动作**：
   * 不依赖任何菜单码，只依赖"当前有会话"。作为对照，`platform-admin` 单跑时
   * 连登出按钮都没有（会话级动作只有基座有），因此改密入口只放基座是一致而非缺口。
   *
   * 更要紧的一点：**不能放在某个子应用里**。新账号可能一个菜单码都没有
   * （ADR-0031），那时它进不去任何子应用 —— 把改密入口放进子应用，
   * 等于让"被自己都进不去的口令锁在门外"从登录态换到门户态重演一遍。
   *
   * 为什么用 `useState` 的惰性初始值而不是 `useEffect`：
   *   1. 后者要在挂载之后再 `setState` 一次，凭空多一轮渲染（React 的
   *      `react-hooks/set-state-in-effect` 正是拦这个）；而这里也**没有**
   *      "要同步的外部系统" —— `RequireAuth` 已经等到会话就位才渲染本布局，
   *      所以初值算出来的那一刻，这个事实就是确定的。
   *   2. "只自动弹一次"也由它保证了：初值只在挂载时算一次，用户关掉之后
   *      不存在任何会把它设回去的代码路径。
   *
   * 自动弹一次、可关闭，且**不是拦截**：`mustChangePassword` 是服务端只标记、
   * 不拦截的引导事实（ADR-0043），做成"不改就不能用"需要服务端配合发受限令牌 ——
   * 而这里没有，一个关不掉的弹窗就是一个假开关。关掉之后顶栏的角标仍在，
   * 因为"这个口令是别人代设的、还没换过"这件事本身没有变。
   */
  const [changePasswordOpen, setChangePasswordOpen] = useState(
    () => session.getSnapshot().user?.mustChangePassword === true,
  );
  const mustChangePassword = user?.mustChangePassword === true;

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
          {mustChangePassword ? (
            /*
             * 角标与弹窗是两件事：弹窗只出现一次（用户关掉就不再打扰），
             * 角标则一直在 —— 因为"这个口令是别人代设的、还没换过"这个事实
             * 并没有因为用户关掉了弹窗而改变。
             */
            <span className="basic-layout__badge" data-testid="must-change-password-badge">
              口令待修改
            </span>
          ) : null}
          <button
            type="button"
            className="basic-layout__action"
            data-testid="open-change-password"
            onClick={() => setChangePasswordOpen(true)}
          >
            修改口令
          </button>
          <button type="button" className="basic-layout__action" onClick={signOut}>
            登出
          </button>
        </div>
      </header>

      {changePasswordOpen ? (
        <ChangePasswordDialog onClose={() => setChangePasswordOpen(false)} />
      ) : null}

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
