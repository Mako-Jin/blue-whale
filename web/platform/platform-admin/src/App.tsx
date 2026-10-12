import { useState } from 'react';

import './App.less';
import { usePermissionState } from './auth/hooks';
import { TenantsPage } from './pages/Tenants';
import { UsersPage } from './pages/Users';

type Tab = 'users' | 'tenants';

const TABS: Array<{ key: Tab; label: string }> = [
  { key: 'users', label: '用户' },
  { key: 'tenants', label: '租户' },
];

export default function App() {
  const [tab, setTab] = useState<Tab>('users');
  const permission = usePermissionState();

  return (
    <div className="app" data-testid="admin-app">
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true" />
          <div>
            <h1 className="topbar__title">权限管理后台</h1>
            <p className="topbar__subtitle">platform-admin · 按权限标识显隐操作入口</p>
          </div>
        </div>

        <div className="topbar__side">
          <p className="topbar__identity" data-testid="admin-identity">
            {permission.ready
              ? `userId=${permission.userId ?? '-'} · 角色 ${permission.roles.length} 个 · 权限 ${permission.permissions.length} 项`
              : '正在获取权限…'}
          </p>
          {permission.permissions.length > 0 ? (
            // 把权限标识原样列出来：文档截图与排查都要看"到底下发了哪些"，
            // 只看数量无法判断是"少了一项"还是"多了一项"
            <details className="topbar__perms">
              <summary>权限集合</summary>
              <ul className="topbar__perms-list" data-testid="admin-permissions">
                {permission.permissions.map((code) => (
                  <li key={code} className="mono">
                    {code}
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </div>
      </header>

      <nav className="tabs" aria-label="管理模块">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`tabs__item${tab === item.key ? ' tabs__item--active' : ''}`}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {/*
        权限未就绪与"没有权限"在这里被刻意分开：前者只是等一等，
        后者由接口的 403 来说。若把未就绪也当成没有权限，独立运行的首次加载
        会先显示一整屏"无权限"，那是假象。
      */}
      {permission.ready ? (
        tab === 'users' ? (
          <UsersPage />
        ) : (
          <TenantsPage />
        )
      ) : (
        <p className="banner" data-testid="admin-pending">
          正在获取权限…
        </p>
      )}
    </div>
  );
}
