// BasicLayout - 运维平台主框架：左侧导航 + 顶部用户区（登出）+ 内容出口

import { AppEvents, eventBus } from '@bluewhale/whale-common-tools';
import { useEffect, useState } from 'react';
import { App, Button, Layout, Menu, Space } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { me } from '../api/auth';
import { clearAuth, getCurrentUser } from '../api/token';

const { Header, Sider, Content } = Layout;

const NAV_ITEMS = [
  { key: '/monitor', label: '服务监控' },
  { key: '/audit', label: '审计日志' },
  { key: '/alert', label: '告警中心' },
];

/** 路径与导航 key 的最长前缀匹配，未命中回退首个导航项 */
function findSelectedKey(pathname: string): string {
  let matched = '';
  NAV_ITEMS.forEach(({ key }) => {
    if (pathname.startsWith(key) && key.length > matched.length) {
      matched = key;
    }
  });
  return matched || NAV_ITEMS[0].key;
}

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const [user, setUser] = useState(() => getCurrentUser());

  // 首屏校验令牌有效性并刷新用户信息；失败时由 http 层跳登录页
  useEffect(() => {
    me()
      .then(() => setUser(getCurrentUser()))
      .catch(() => setUser(getCurrentUser()));
  }, []);

  const handleLogout = () => {
    const user = getCurrentUser();
    // 审计：本地退出留痕（先于清态，保证上报能解析到当前用户）
    eventBus.emit(AppEvents.AUTH_LOGOUT, { userId: user?.userId, username: user?.username });
    clearAuth();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={208}>
        <div className="ops-logo">BlueWhale Ops</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[findSelectedKey(location.pathname)]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="ops-header">
          <span className="ops-header-title">运维平台</span>
          <Space>
            <span>{user?.nickname ?? user?.username ?? '-'}</span>
            <Button type="primary" danger size="small" onClick={handleLogout}>
              退出登录
            </Button>
          </Space>
        </Header>
        <Content className="ops-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
