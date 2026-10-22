// AppLayout - 顶部栏（品牌 + 索引状态徽标 + 用户区/退出登录）+ 内容出口
// 工作台自带三栏（会话 / 消息流 / 证据），故不再提供侧边导航

import { useEffect, useState } from 'react';
import { App, Button, Layout, Space, Tag } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { fetchMe, logout } from '../api/auth';
import { getCurrentUser, useCurrentUser } from '../api/token';
import IndexBadge from '../components/IndexBadge';

const { Header, Content } = Layout;

/** 重建索引权限点（与后端 @RequirePermission 一致） */
const REBUILD_PERMISSION = 'sys:agent:index:rebuild';

export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const user = useCurrentUser();
  const [permissions, setPermissions] = useState<string[]>(() => getCurrentUser()?.permissions ?? []);

  // 首次进入补拉 /auth/me，用于「重建」按钮与权限相关的可见性
  useEffect(() => {
    if (permissions.length > 0) {
      return;
    }
    let active = true;
    fetchMe()
      .then((me) => {
        if (active) {
          setPermissions(me.permissions ?? []);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ height: '100vh' }}>
      <Header className="agent-header">
        <Space size={12} align="center">
          <span className="agent-brand">蓝鲸知识助手</span>
          <span className="agent-brand-sub">Platform &amp; Project Knowledge Agent</span>
        </Space>
        <Space size={12} align="center">
          <IndexBadge canRebuild={permissions.includes(REBUILD_PERMISSION)} />
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
      <Content className="agent-content" data-path={location.pathname}>
        <Outlet />
      </Content>
    </Layout>
  );
}
