// AdminLayout - 后台主框架：左侧导航 + 顶部用户区（登出）+ 内容出口
// 侧边导航优先由 /auth/me 的 menus 驱动，menus 为空时回退静态导航

import { App, Button, Layout, Menu, Space, Tag } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../api/auth';
import { useCurrentUser } from '../api/token';
import { collectMenuKeys, getSidebarItems, type SidebarItem } from '../router/dynamicRoutes';

const { Header, Sider, Content } = Layout;

// 静态回退导航（无 menus 时保持既有行为，不回退）
const NAV_ITEMS: SidebarItem[] = [
  { key: '/', label: '仪表盘' },
  { key: '/users', label: '用户管理' },
  { key: '/roles', label: '角色管理' },
  { key: '/menus', label: '菜单管理' },
  { key: '/components', label: '组件示例' },
];

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

  const dynamicItems = getSidebarItems(user?.menus);
  const navItems = dynamicItems.length > 0 ? dynamicItems : NAV_ITEMS;
  const selectedKey = findSelectedKey(collectMenuKeys(navItems), location.pathname);

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
