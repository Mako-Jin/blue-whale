// AdminLayout - 后台主框架：左侧导航 + 顶部用户区（登出）+ 内容出口

import { App, Button, Layout, Menu, Space, Tag } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../api/auth';
import { getCurrentUser } from '../api/token';

const { Header, Sider, Content } = Layout;

const NAV_ITEMS = [
  { key: '/', label: '仪表盘' },
  { key: '/users', label: '用户管理' },
  { key: '/roles', label: '角色管理' },
  { key: '/menus', label: '菜单管理' },
  { key: '/components', label: '组件示例' },
];

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const user = getCurrentUser();

  const selectedKey =
    NAV_ITEMS.filter((item) => item.key !== '/' && location.pathname.startsWith(item.key))[0]?.key ??
    '/';

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
          items={NAV_ITEMS}
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
