// AppLayout - 应用主框架：左侧菜单（表定义 / 表单视图 / 数据）+ 顶部用户区（退出登录）+ 内容出口
// 基于 admin AdminLayout 精简：本应用菜单固定，不经 /auth/me 的 menus 派生

import { App, Button, Layout, Menu, Space, Tag } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../api/auth';
import { useCurrentUser } from '../api/token';

const { Header, Sider, Content } = Layout;

/** 静态导航项（类型对齐 antd Menu items） */
type NavItem = NonNullable<MenuProps['items']>[number];

const NAV_ITEMS: NavItem[] = [
  { key: '/tables', label: '表定义' },
  { key: '/forms', label: '表单视图' },
  { key: '/data', label: '数据' },
];

/** 导航 key 集合（与 NAV_ITEMS 对应） */
const NAV_KEYS = ['/tables', '/forms', '/data'];

/** 路径与导航 key 的最长前缀匹配，未命中时回退首个菜单 */
function findSelectedKey(keys: string[], pathname: string): string {
  let matched = '';
  keys.forEach((key) => {
    if (pathname.startsWith(key) && key.length > matched.length) {
      matched = key;
    }
  });
  return matched || keys[0] || '/tables';
}

export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const user = useCurrentUser();

  const selectedKey = findSelectedKey(NAV_KEYS, location.pathname);

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={208}>
        <div className="admin-logo">表单设计器</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="admin-header">
          <span className="admin-header-title">蓝鲸表单设计器</span>
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
