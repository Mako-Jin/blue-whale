// AdminLayout - 后台主框架：左侧导航 + 顶部用户区（登出）+ 内容出口
// 侧边导航优先由 /auth/me 的 menus 驱动，menus 为空时回退静态导航

import { App, Button, Layout, Menu, Select, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { listMyTenants, logout, switchTenant, type MyTenantVO } from '../api/auth';
import { useCurrentUser } from '../api/token';
import { ThemeSwitcher } from '@bluewhale/whale-common-theme';
import { collectMenuKeys, getSidebarItems, type SidebarItem } from '../router/dynamicRoutes';

const { Header, Sider, Content } = Layout;

// 静态回退导航（无 menus 时使用；仍需按权限裁剪，避免「菜单可见但接口 403」）
const NAV_ITEMS: SidebarItem[] = [
  { key: '/', label: '仪表盘' },
  { key: '/users', label: '用户管理' },
  { key: '/roles', label: '角色管理' },
  { key: '/menus', label: '菜单管理' },
  { key: '/tenants', label: '租户管理' },
  { key: '/components', label: '组件示例' },
];

/** 静态回退导航的权限码（键为路由 key；未列出的入口登录即可见） */
const STATIC_NAV_PERMISSIONS: Record<string, string> = {
  '/users': 'sys:user:list',
  '/roles': 'sys:role:list',
  '/menus': 'sys:menu:list',
  '/tenants': 'sys:tenant:list',
};

/** 按权限裁剪静态回退导航 */
function filterStaticNav(items: SidebarItem[], permissions: readonly string[]): SidebarItem[] {
  const granted = new Set(permissions);
  return items.filter((item) => {
    if (!item) return false;
    const key = 'key' in item && item.key != null ? String(item.key) : '';
    const required = STATIC_NAV_PERMISSIONS[key];
    return !required || granted.has(required);
  });
}

/**
 * 动态菜单未包含租户管理时补一个入口。
 * 仅平台租户账号持有 sys:tenant:list 权限码，业务租户不会看到该入口。
 */
function withTenantNav(items: SidebarItem[], canManageTenants: boolean): SidebarItem[] {
  if (!canManageTenants || collectMenuKeys(items).includes('/tenants')) {
    return items;
  }
  return [...items, { key: '/tenants', label: '租户管理' }];
}

/** 路径与导航 key 的最长前缀匹配，未命中时回退首页 '/' */
function findSelectedKey(keys: string[], pathname: string): string {
  let matched = '';
  keys.forEach((key) => {
    if (key !== '/' && pathname.startsWith(key) && key.length > matched.length) {
      matched = key;
    }
  });
  if (matched) {
    return matched;
  }
  return keys.includes('/') ? '/' : (keys[0] ?? '/');
}

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const user = useCurrentUser();

  const permissions = user?.permissions ?? [];
  const canManageTenants = permissions.includes('sys:tenant:list');
  const dynamicItems = getSidebarItems(user?.menus);
  // 动态菜单由 /auth/me 按权限生成；回退静态导航时同样按权限码裁剪
  const fallbackItems = filterStaticNav(NAV_ITEMS, permissions);
  const navItems =
    dynamicItems.length > 0 ? withTenantNav(dynamicItems, canManageTenants) : fallbackItems;
  const selectedKey = findSelectedKey(collectMenuKeys(navItems), location.pathname);
  const tenantLabel = user?.tenantCode || user?.tenantId;

  // 一人多租户：可进入租户 > 1 时以切换器替代静态标签（Wave 5.54 Phase 5.2）
  const [myTenants, setMyTenants] = useState<MyTenantVO[]>([]);
  const [switching, setSwitching] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void listMyTenants()
      .then((list) => {
        if (!cancelled) {
          setMyTenants(list);
        }
      })
      .catch(() => {
        // 无权限或网络异常时保持原租户标签，不影响登录态
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSwitchTenant = async (value: string) => {
    const target = myTenants.find((item) => String(item.tenantId) === value);
    if (!target || target.current) {
      return;
    }
    setSwitching(true);
    try {
      await switchTenant(target.tenantId, target.tenantCode);
      // 全量重载，确保权限、菜单与请求头租户全部按目标租户重建
      window.location.replace('/');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '切换租户失败');
      setSwitching(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录，令牌已加入黑名单');
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={208}>
        <div className="admin-logo">BlueWhale</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="admin-header">
          <span className="admin-header-title">后台管理平台</span>
          <Space>
            <ThemeSwitcher compact />
            {myTenants.length > 1 ? (
              <Select
                className="admin-tenant-switcher"
                size="small"
                value={String(user?.tenantId ?? '')}
                placeholder="租户"
                loading={switching}
                disabled={switching}
                popupMatchSelectWidth={false}
                onChange={(value) => void handleSwitchTenant(value)}
                options={myTenants.map((item) => ({
                  value: String(item.tenantId),
                  label: `租户 ${item.tenantName || item.tenantCode}`,
                }))}
              />
            ) : tenantLabel ? (
              <Tag color="geekblue">租户 {tenantLabel}</Tag>
            ) : null}
            <span>{user?.nickname ?? user?.username ?? '-'}</span>
            {(user?.roles ?? []).map((role) => (
              <Tag key={role} color="blue">
                {role}
              </Tag>
            ))}
            <Button type="primary" danger size="small" onClick={() => void handleLogout()}>
              退出登录
            </Button>
          </Space>
        </Header>
        <Content className="admin-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
