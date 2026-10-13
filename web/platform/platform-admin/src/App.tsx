import { useState } from 'react';
import type { ReactElement } from 'react';
import { AdminViews } from 'common-api';
import type { MenuPermission } from 'common-api';
import { visibleViews } from 'common-tools/micro';

import './App.less';
import { usePermissionState } from './auth/hooks';
import { OrganizationsPage } from './pages/Organizations';
import { ResourcesPage } from './pages/Resources';
import { RolesPage } from './pages/Roles';
import { TenantsPage } from './pages/Tenants';
import { UserGroupsPage } from './pages/UserGroups';
import { UsersPage } from './pages/Users';

type Tab = 'users' | 'tenants' | 'resources' | 'roles' | 'userGroups' | 'organizations';

/**
 * 标签页顺序 = **授权链路的顺序**。
 *
 * <p>`资源 → 角色 → 用户组` 与 `用户 → 角色` 是同一件事的两条边。
 * 照这个顺序排，「谁能做什么」在界面上就是从左到右读过去的：
 * 先登记"能做什么"（资源），再把它打包（角色 → 资源授权），
 * 最后把包交出去（角色 → 成员 / 用户组 → 成员）。
 *
 * <p>「组织」排在最后，因为它回答的是**另一个问题**：前面几项定的是
 * "能不能做这个动作"，组织定的是"看得到哪些数据"。它不参与那条从左到右的链，
 * 而是给其中的查询类动作提供范围 —— 硬插在中间会让人以为它也是授权的一环。
 *
 * <p><b>`permission` 是菜单码，不是读写权限。</b>它决定这个标签页**出不出来**，
 * 不决定点进去能不能读到数据（那由服务端的 `@RequiresPermission` 回答）。
 * 两层分开的意义在这里最明显：一个只有 `auth:user:read` 的账号
 * 既可能被授了「用户」标签页（于是看得到列表、看不到写按钮），
 * 也可能连标签页都没有（连入口都不该出现）—— 这是两个独立的决定。
 */
const TABS: ReadonlyArray<{ key: Tab; label: string; permission: MenuPermission }> = [
  { key: 'users', label: '用户', permission: AdminViews.USER },
  { key: 'tenants', label: '租户', permission: AdminViews.TENANT },
  { key: 'resources', label: '资源', permission: AdminViews.RESOURCE },
  { key: 'roles', label: '角色', permission: AdminViews.ROLE },
  { key: 'userGroups', label: '用户组', permission: AdminViews.USER_GROUP },
  { key: 'organizations', label: '组织', permission: AdminViews.ORGANIZATION },
];

/** 标签页内容。与 `TABS` 的 key 一一对应 —— 漏一个会被类型检查拦下 */
const PAGES: Record<Tab, () => ReactElement> = {
  users: UsersPage,
  tenants: TenantsPage,
  resources: ResourcesPage,
  roles: RolesPage,
  userGroups: UserGroupsPage,
  organizations: OrganizationsPage,
};

export default function App() {
  const [tab, setTab] = useState<Tab>('users');
  const permission = usePermissionState();

  /*
   * 按**菜单码**过滤标签页，与基座过滤导航栏是同一套规则、同一个来源。
   *
   * 顺序保持 `TABS` 的声明顺序：过滤只该做减法，不该重排 ——
   * 重排会让"授权链路的顺序"这件事在各种账号上长得不一样。
   *
   * 用 `visibleViews` 而不是就地 `filter`：**"未就绪时怎么办"是这条规则的
   * 一半**（答案是不显示），各写一次就会长出"有的地方未就绪时先放行"这种偏差。
   * 它把两件事收在一处：未就绪 ⇒ 空列表；就绪 ⇒ 只留持有权限的那些。
   */
  const visibleTabs = visibleViews(TABS, permission, (item) => item.permission);

  /*
   * 选中的标签页可能被过滤掉了（例如权限集合在运行中变小），
   * 这时回落到第一个可见的。**不能直接渲染一个不可见的标签页** ——
   * 那会绕开过滤，等于过滤只在导航上生效。
   */
  const activeKey: Tab | null = visibleTabs.some((item) => item.key === tab)
    ? tab
    : (visibleTabs[0]?.key ?? null);
  const ActivePage = activeKey ? PAGES[activeKey] : null;

  return (
    <div className="app" data-testid="admin-app">
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true" />
          <div>
            <h1 className="topbar__title">权限管理后台</h1>
            <p className="topbar__subtitle">
              platform-admin · 资源 → 角色 → 成员 / 用户组；组织提供数据范围，按权限标识显隐操作入口
            </p>
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

      <nav className="tabs" aria-label="管理模块" data-testid="admin-tabs">
        {visibleTabs.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`tabs__item${activeKey === item.key ? ' tabs__item--active' : ''}`}
            data-testid={`admin-tab-${item.key}`}
            data-permission={item.permission}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {/*
        三种状态刻意分开，因为它们要求用户做的事完全不同：
        · 未就绪 —— 等一等（首次加载等权限，而不是先打一次注定 401 的请求）；
        · 就绪但没有可见标签页 —— 菜单码没配（让管理员去授权，不是刷新）；
        · 就绪且有标签页 —— 渲染，读不到数据时由接口的 403 来说。
        把后两者混成一个"无权限"，会让人分不清该找谁。
      */}
      {!permission.ready ? (
        <p className="banner" data-testid="admin-pending">
          正在获取权限…
        </p>
      ) : ActivePage ? (
        <ActivePage />
      ) : (
        <p className="banner" data-testid="admin-empty">
          当前身份没有可进入的模块（需要 <code>ui:admin_*:view</code> 之一的菜单码）。
        </p>
      )}
    </div>
  );
}
